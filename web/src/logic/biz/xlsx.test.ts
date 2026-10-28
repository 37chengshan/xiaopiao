import { describe, expect, it } from "vitest";
import { excelSerialToIso, readXlsxRows, rowsToImportText } from "./xlsx";
import { parseRevenueCsv } from "./import";

const TODAY = new Date(2026, 9, 28).getTime();

/** Minimal ZIP writer: stored or deflated entries, enough for the reader under test. */
async function zip(files: Array<{ name: string; xml: string; deflate?: boolean }>): Promise<ArrayBuffer> {
  const encoder = new TextEncoder();
  const chunks: Uint8Array[] = [];
  const central: Uint8Array[] = [];
  let offset = 0;

  for (const file of files) {
    const name = encoder.encode(file.name);
    const raw = encoder.encode(file.xml);
    let body = raw;
    let method = 0;
    if (file.deflate) {
      const stream = new Blob([raw]).stream().pipeThrough(new CompressionStream("deflate-raw"));
      body = new Uint8Array(await new Response(stream).arrayBuffer());
      method = 8;
    }
    const local = new Uint8Array(30 + name.length + body.length);
    const lv = new DataView(local.buffer);
    lv.setUint32(0, 0x04034b50, true);
    lv.setUint16(4, 20, true);
    lv.setUint16(8, method, true);
    lv.setUint32(18, body.length, true);
    lv.setUint32(22, raw.length, true);
    lv.setUint16(26, name.length, true);
    local.set(name, 30);
    local.set(body, 30 + name.length);
    chunks.push(local);

    const dir = new Uint8Array(46 + name.length);
    const dv = new DataView(dir.buffer);
    dv.setUint32(0, 0x02014b50, true);
    dv.setUint16(4, 20, true);
    dv.setUint16(6, 20, true);
    dv.setUint16(10, method, true);
    dv.setUint32(20, body.length, true);
    dv.setUint32(24, raw.length, true);
    dv.setUint16(28, name.length, true);
    dv.setUint32(42, offset, true);
    dir.set(name, 46);
    central.push(dir);
    offset += local.length;
  }

  const cdSize = central.reduce((n, c) => n + c.length, 0);
  const eocd = new Uint8Array(22);
  const ev = new DataView(eocd.buffer);
  ev.setUint32(0, 0x06054b50, true);
  ev.setUint16(8, files.length, true);
  ev.setUint16(10, files.length, true);
  ev.setUint32(12, cdSize, true);
  ev.setUint32(16, offset, true);

  const all = [...chunks, ...central, eocd];
  const out = new Uint8Array(all.reduce((n, c) => n + c.length, 0));
  let at = 0;
  for (const part of all) {
    out.set(part, at);
    at += part.length;
  }
  return out.buffer;
}

const SHARED = `<?xml version="1.0"?>
<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <si><t>日期</t></si>
  <si><t>营收</t></si>
  <si><t>订单</t></si>
  <si><r><t>芋泥</t></r><r><t>&amp; 珍珠</t></r></si>
</sst>`;

const SHEET = `<?xml version="1.0"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>
    <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c></row>
    <row r="2"><c r="A2" s="1"><v>46322</v></c><c r="B2"><v>1280.5</v></c><c r="C2"><v>86</v></c></row>
    <row r="3"><c r="A3" t="inlineStr"><is><t>2026-10-27</t></is></c><c r="B3"><v>2650</v></c><c r="D3" t="s"><v>3</v></c></row>
  </sheetData>
</worksheet>`;

describe("Excel rows", () => {
  it("reads a deflated sheet, shared strings and an inline date", async () => {
    const buf = await zip([
      { name: "xl/sharedStrings.xml", xml: SHARED, deflate: true },
      { name: "xl/worksheets/sheet1.xml", xml: SHEET, deflate: true },
    ]);
    const rows = await readXlsxRows(buf);
    expect(rows[0]).toEqual(["日期", "营收", "订单"]);
    expect(rows[1].slice(1)).toEqual(["1280.5", "86"]);
    expect(rows[1][0]).toBe("2026-10-27"); // 46322 as an Excel date serial
    expect(rows[2][0]).toBe("2026-10-27");
    expect(rows[2][3]).toBe("芋泥& 珍珠");
    expect(rows).toHaveLength(3);
  });

  it("reads a stored sheet too", async () => {
    const buf = await zip([{ name: "xl/worksheets/sheet1.xml", xml: SHEET }]);
    const rows = await readXlsxRows(buf);
    // The header row resolves to empty cells without a shared-string table, so it drops out.
    expect(rows[0][1]).toBe("1280.5");
    expect(rows[1][3]).toBe(""); // no sharedStrings part → the shared ref resolves to ""
  });

  it("turns a date serial into a date the importer accepts", () => {
    expect(excelSerialToIso(46_322)).toBe("2026-10-27");
    expect(excelSerialToIso(12)).toBeNull();
  });

  it("feeds the same import pipeline as a CSV", async () => {
    const buf = await zip([
      { name: "xl/sharedStrings.xml", xml: SHARED },
      { name: "xl/worksheets/sheet1.xml", xml: SHEET },
    ]);
    const text = rowsToImportText(await readXlsxRows(buf));
    const rows = parseRevenueCsv(text, TODAY);
    expect(rows.map((r) => r.error)).toEqual([null, null]);
    expect(rows[1]).toMatchObject({ revenueCents: 265_000, note: "芋泥& 珍珠" });
  });

  it("fails with a readable message on a file that is not a workbook", async () => {
    await expect(readXlsxRows(new TextEncoder().encode("hello").buffer)).rejects.toThrow("notXlsx");
    const empty = await zip([{ name: "docProps/core.xml", xml: "<x/>" }]);
    await expect(readXlsxRows(empty)).rejects.toThrow("noSheet");
  });
});
