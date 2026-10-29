import { money, shortDate } from "@/logic/biz/format";

/**
 * The daily report slip, laid out as geometry (not as DOM) so the same spec draws the screen
 * preview, the print sheet and the exported PNG. Widths are receipt units: 58mm ≈ 219, 80mm ≈ 302.
 * Colours are literal oklch here because the exported SVG is rasterised outside the page stylesheet.
 */
export const PAPER = "oklch(0.977 0.01 87.5)";
export const INK = "oklch(0.24 0.004 286.1)";
export const MUTED = "oklch(0.54 0.008 286.1)";
export const HAIRLINE = "oklch(0.85 0.006 286.3)";

export type ReceiptMm = 58 | 80;

export interface ReceiptRow {
  kind: "center" | "row" | "rule" | "gap";
  y: number;
  left?: string;
  right?: string;
  text?: string;
  size: number;
  weight: number;
  muted?: boolean;
  /** dash pattern for rules, in receipt units */
  dash?: string;
}

export interface ReceiptSpec {
  mm: ReceiptMm;
  width: number;
  height: number;
  rows: ReceiptRow[];
}

export interface ReceiptInput {
  mm: ReceiptMm;
  shopName: string;
  day: number;
  revenueCents: number;
  orders: number;
  ticketCents: number;
  weekCents: number;
  summary: string;
  variant: "full" | "brief";
  showOrders: boolean;
  showTicket: boolean;
  showWeek: boolean;
  showSummary: boolean;
  /** weekday names + fixed labels, resolved by the caller's dictionary */
  labels: {
    head: string;
    revenue: string;
    orders: string;
    ticket: string;
    week: string;
    issued: string;
    thanks: string;
    ordersUnit: string;
    weekday: string;
    summaryFallback: string;
  };
  issuedAt: string;
}

const PAD = 16;
const BASE = 11;
const LEAD = 17;

/** Wraps by display width: a CJK glyph is one em, ASCII roughly 0.56 em. */
export function wrapText(text: string, width: number, size: number): string[] {
  const out: string[] = [];
  let line = "";
  let used = 0;
  for (const ch of text) {
    const advance = /[\u3000-\u9fff\uff00-\uffef]/.test(ch) ? size : size * 0.56;
    if (used + advance > width && line !== "") {
      out.push(line);
      line = "";
      used = 0;
    }
    line += ch;
    used += advance;
  }
  if (line !== "") out.push(line);
  return out;
}

export function buildReceipt(input: ReceiptInput): ReceiptSpec {
  const width = input.variant === "brief" ? (input.mm === 58 ? 190 : 220) : input.mm === 58 ? 219 : 302;
  const content = width - PAD * 2;
  const rows: ReceiptRow[] = [];
  let y = PAD + 2;

  const center = (text: string, size: number, weight = 400, muted = false) => {
    rows.push({ kind: "center", y, text, size, weight, muted });
    y += Math.round(size * 1.45);
  };
  const row = (left: string, right: string, size = BASE) => {
    rows.push({ kind: "row", y, left, right, size, weight: 400 });
    y += LEAD;
  };
  const rule = (dash = "2 3") => {
    rows.push({ kind: "rule", y, size: BASE, weight: 400, dash });
    y += 10;
  };
  const para = (text: string, size: number, muted = false) => {
    for (const line of wrapText(text, content, size)) {
      rows.push({ kind: "center", y, text: line, size, weight: 400, muted });
      y += Math.round(size * 1.5);
    }
  };

  center(input.shopName, BASE + 1, 600);
  if (input.variant === "full") center(input.labels.head, 9, 400, true);
  y += 2;
  center(`${shortDate(input.day)} ${input.labels.weekday}`, BASE + 1, 600);
  y += 2;
  rule();

  row(input.labels.revenue, money(input.revenueCents), BASE + 2);
  if (input.showOrders) row(input.labels.orders, `${input.orders} ${input.labels.ordersUnit}`);
  if (input.showTicket) row(input.labels.ticket, money(input.ticketCents));
  if (input.showWeek) row(input.labels.week, money(input.weekCents));

  if (input.showSummary) {
    y += 2;
    rule("1 3");
    para(input.summary || input.labels.summaryFallback, 9);
  }

  y += 4;
  rule("1 3");
  center(input.labels.thanks, 9, 400, true);
  center(`${input.labels.issued} ${input.issuedAt}`, 8, 400, true);

  return { mm: input.mm, width, height: Math.round(y + PAD), rows };
}
