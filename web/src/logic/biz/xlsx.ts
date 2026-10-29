import { csvCell } from "./import";

/**
 * Reading a shop's Excel export without a dependency: an .xlsx file is a ZIP of XML, so we
 * walk the central directory, inflate the sheet and shared strings with the browser's own
 * `DecompressionStream('deflate-raw')`, and read cells with a small pattern pass.
 *
 * Only what a one-sheet revenue table needs: shared strings, inline strings, numbers and dates.
 */

interface ZipEntry {
  name: string;
  method: number;
  compressedSize: number;
  localOffset: number;
}

const EOCD_SIG = 0x06054b50;
const CENTRAL_SIG = 0x02014b50;
const LOCAL_SIG = 0x04034b50;

function u16(view: DataView, at: number): number {
  return view.getUint16(at, true);
}
function u32(view: DataView, at: number): number {
  return view.getUint32(at, true);
}

function findEocd(view: DataView): number {
  const min = Math.max(0, view.byteLength - 66_000);
  for (let at = view.byteLength - 22; at >= min; at--) {
    if (u32(view, at) === EOCD_SIG) return at;
  }
  return -1;
}

function readCentralDirectory(buf: ArrayBuffer): ZipEntry[] {
  const view = new DataView(buf);
  const eocd = findEocd(view);
  if (eocd < 0) throw new Error("notXlsx");
  const count = u16(view, eocd + 10);
  let at = u32(view, eocd + 16);
  const entries: ZipEntry[] = [];
  for (let i = 0; i < count && at + 46 <= buf.byteLength; i++) {
    if (u32(view, at) !== CENTRAL_SIG) break;
    const method = u16(view, at + 10);
    const compressedSize = u32(view, at + 20);
    const nameLength = u16(view, at + 28);
    const extraLength = u16(view, at + 30);
    const commentLength = u16(view, at + 32);
    const localOffset = u32(view, at + 42);
    const name = new TextDecoder().decode(new Uint8Array(buf, at + 46, nameLength));
    entries.push({ name, method, compressedSize, localOffset });
    at += 46 + nameLength + extraLength + commentLength;
  }
  return entries;
}

async function entryBytes(buf: ArrayBuffer, entry: ZipEntry): Promise<Uint8Array> {
  const view = new DataView(buf);
  if (u32(view, entry.localOffset) !== LOCAL_SIG) throw new Error("notXlsx");
  const nameLength = u16(view, entry.localOffset + 26);
  const extraLength = u16(view, entry.localOffset + 28);
  const start = entry.localOffset + 30 + nameLength + extraLength;
  const raw = new Uint8Array(buf, start, entry.compressedSize);
  if (entry.method === 0) return raw;
  if (entry.method !== 8) throw new Error("unsupported");
  const stream = new Blob([raw]).stream().pipeThrough(new DecompressionStream("deflate-raw"));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}

function unescapeXml(text: string): string {
  return text
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&#(\d+);/g, (_, code: string) => String.fromCodePoint(Number(code)))
    .replace(/&#x([0-9a-f]+);/gi, (_, code: string) => String.fromCodePoint(Number.parseInt(code, 16)))
    .replace(/&amp;/g, "&");
}

/** Shared-string table: one entry per `<si>`, joined over its `<t>` runs. */
function parseSharedStrings(xml: string): string[] {
  const out: string[] = [];
  const si = /<si\b[^>]*>([\s\S]*?)<\/si>/g;
  for (let m = si.exec(xml); m; m = si.exec(xml)) {
    const runs = [...m[1].matchAll(/<t\b[^>]*>([\s\S]*?)<\/t>/g)].map((r) => unescapeXml(r[1]));
    out.push(runs.join(""));
  }
  return out;
}

function columnIndex(ref: string): number {
  const letters = ref.match(/^[A-Z]+/i)?.[0].toUpperCase() ?? "";
  let n = 0;
  for (const ch of letters) n = n * 26 + (ch.charCodeAt(0) - 64);
  return n - 1;
}

/** Excel stores dates as days since 1899-12-30; serials land in 20000…80000 for 1954–2119. */
export function excelSerialToIso(serial: number): string | null {
  if (!Number.isFinite(serial) || serial < 20_000 || serial > 80_000) return null;
  const ms = Date.UTC(1899, 11, 30) + Math.round(serial) * 86_400_000;
  const d = new Date(ms);
  const month = `${d.getUTCMonth() + 1}`.padStart(2, "0");
  const day = `${d.getUTCDate()}`.padStart(2, "0");
  return `${d.getUTCFullYear()}-${month}-${day}`;
}

/** First worksheet, as a rectangle of cell texts. */
function parseSheet(xml: string, shared: readonly string[]): string[][] {
  const rows: string[][] = [];
  const rowRe = /<row\b[^>]*>([\s\S]*?)<\/row>/g;
  for (let rm = rowRe.exec(xml); rm; rm = rowRe.exec(xml)) {
    const cells: string[] = [];
    const cellRe = /<c\b([^>]*?)(?:\/>|>([\s\S]*?)<\/c>)/g;
    let next = 0;
    for (let cm = cellRe.exec(rm[1]); cm; cm = cellRe.exec(rm[1])) {
      const attrs = cm[1];
      const body = cm[2] ?? "";
      const ref = attrs.match(/\br="([A-Z]+\d+)"/i)?.[1];
      const index = ref ? columnIndex(ref) : next;
      next = index + 1;
      const type = attrs.match(/\bt="([^"]+)"/)?.[1] ?? "n";
      let text = "";
      if (type === "s") {
        const id = Number(body.match(/<v>([\s\S]*?)<\/v>/)?.[1] ?? "");
        text = Number.isFinite(id) ? (shared[id] ?? "") : "";
      } else if (type === "inlineStr") {
        text = [...body.matchAll(/<t\b[^>]*>([\s\S]*?)<\/t>/g)].map((r) => unescapeXml(r[1])).join("");
      } else {
        text = unescapeXml(body.match(/<v>([\s\S]*?)<\/v>/)?.[1] ?? "");
      }
      while (cells.length < index) cells.push("");
      cells[index] = text;
    }
    rows.push(cells);
  }
  return rows.filter((r) => r.some((c) => c.trim() !== ""));
}

/** Cell rows of the first sheet in an .xlsx workbook. Throws a readable message on anything else. */
export async function readXlsxRows(buf: ArrayBuffer): Promise<string[][]> {
  const entries = readCentralDirectory(buf);
  const sheet =
    entries.find((e) => /^xl\/worksheets\/sheet1\.xml$/i.test(e.name)) ??
    entries.find((e) => /^xl\/worksheets\/sheet\d+\.xml$/i.test(e.name));
  if (!sheet) throw new Error("noSheet");
  const shared = entries.find((e) => /^xl\/sharedStrings\.xml$/i.test(e.name));
  const decoder = new TextDecoder();
  const sharedStrings = shared ? parseSharedStrings(decoder.decode(await entryBytes(buf, shared))) : [];
  const sheetXml = decoder.decode(await entryBytes(buf, sheet));
  const rows = parseSheet(sheetXml, sharedStrings);
  return rows.map((row) => {
    const first = row[0]?.trim() ?? "";
    if (/^\d{5}(\.\d+)?$/.test(first)) {
      const iso = excelSerialToIso(Number(first));
      if (iso) return [iso, ...row.slice(1)];
    }
    return row;
  });
}

/** Cell rows → the same `日期,营收,订单,备注` text the CSV reader already understands. */
export function rowsToImportText(rows: readonly (readonly string[])[]): string {
  return rows.map((row) => row.map((cell) => csvCell(cell ?? "")).join(",")).join("\n");
}
