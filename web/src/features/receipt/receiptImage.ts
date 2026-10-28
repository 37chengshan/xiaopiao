import type { ReceiptGradient } from "./gradients";
import { DISCLAIMER, displayWidth, fmtMoney, STUB, TAGLINE, type ReceiptDoc } from "./receiptModel";
import { bottomEdge, PAPER_W, topEdge } from "./zigzag";

/**
 * Exports the receipt as a PNG: the paper (same zigzag edges and rows as ReceiptPaper) lying on a gradient card.
 * Canvas cannot read Tailwind tokens, so paper colours are literal and mirror the light-theme paper tokens
 * (the exported image is the same in both themes: paper stays paper).
 */
const PAPER = "#FBFAF6";
const INK = "#2A2724";
const STAMP = "#C0412F";
const MONO = `ui-monospace, "SF Mono", Menlo, "PingFang SC", "Noto Sans Mono CJK SC", monospace`;
const SERIF = `"Songti SC", "Noto Serif SC", Georgia, serif`;
const MARGIN = 28;
const PAD = 20;
const SCALE = 3;

type Op =
  | { k: "line"; y: number; left: string; right: string; weight?: number; dim?: boolean; indent?: number }
  | { k: "center"; y: number; text: string; size: number; font: string; dim?: boolean; weight?: number; spacing?: number }
  | { k: "rule"; y: number }
  | { k: "stamp"; y: number; text: string };

function wrapText(text: string, cols: number): string[] {
  const out: string[] = [];
  let cur = "";
  for (const ch of text) {
    if (displayWidth(cur + ch) > cols && cur) {
      out.push(cur);
      cur = ch;
    } else cur += ch;
  }
  return cur ? [...out, cur] : out;
}

export function layout(doc: ReceiptDoc): { ops: Op[]; height: number } {
  const ops: Op[] = [];
  let y = 30;
  const rule = () => {
    y += 6;
    ops.push({ k: "rule", y });
    y += 8;
  };
  const line = (left: string, right: string, o: { weight?: number; dim?: boolean; indent?: number } = {}) => {
    ops.push({ k: "line", y: y + 15, left, right, ...o });
    y += 20;
  };
  ops.push({ k: "center", y: y + 22, text: "今日小票", size: 23, font: SERIF, weight: 600, spacing: 11 });
  y += 30;
  ops.push({ k: "center", y: y + 14, text: TAGLINE, size: 12, font: SERIF, dim: true });
  y += 18;
  rule();
  line("日期", doc.dateLabel);
  if (doc.variant === "full") line("出票", doc.printedAt);
  rule();
  ops.push({ k: "center", y: y + 12, text: doc.isToday ? "今日支出" : "当日支出", size: 11.5, font: MONO, dim: true, spacing: 3.5 });
  y += 18;
  ops.push({ k: "center", y: y + 40, text: `¥ ${fmtMoney(doc.totalCents)}`, size: 42, font: MONO, weight: 600 });
  y += 52;
  ops.push({ k: "center", y: y + 10, text: DISCLAIMER, size: 10.5, font: MONO, dim: true });
  y += 16;
  rule();
  if (doc.variant === "brief") {
    /* brief: no detail lines */
  } else if (doc.zero) {
    ops.push({ k: "center", y: y + 28, text: "今日无消费", size: 22, font: SERIF, weight: 600 });
    y += 40;
    ops.push({ k: "center", y: y + 8, text: "钱包今天睡着了 🌙", size: 11.5, font: MONO, dim: true });
    y += 22;
  } else {
    line("分类 / 明细", "金额", { dim: true });
    y += 4;
    for (const g of doc.groups) {
      line(`${g.emoji} ${g.label}`, fmtMoney(g.subtotalCents), { weight: 500 });
      for (const l of g.lines) line(l.merchant, fmtMoney(l.cents), { dim: true, indent: 22 });
      if (g.hidden > 0) line(`… 另 ${g.hidden} 笔`, "", { dim: true, indent: 22 });
      y += 4;
    }
    if (doc.folded) line(`… 其他 ${doc.folded.count} 类`, fmtMoney(doc.folded.cents), { dim: true });
    if (doc.income) {
      y += 2;
      line(`${doc.income.emoji} ${doc.income.label}`, `+${fmtMoney(doc.income.subtotalCents)}`, { weight: 500 });
      for (const l of doc.income.lines) line(l.merchant, `+${fmtMoney(l.cents)}`, { dim: true, indent: 22 });
    }
  }
  if (doc.variant === "full") {
    rule();
    ops.push({ k: "center", y: y + 14, text: `${doc.stats.count}   ${doc.stats.delta}   ${doc.stats.weekDay}`, size: 10.5, font: MONO, dim: true });
    y += 20;
    rule();
  }
  for (const part of wrapText(`「${doc.remark}」`, 40)) {
    ops.push({ k: "center", y: y + 16, text: part, size: 12.5, font: SERIF });
    y += 21;
  }
  y += 5;
  ops.push({ k: "stamp", y: y + 44, text: doc.stampText });
  y += 88;
  if (doc.variant === "full" && doc.badgeReason) {
    ops.push({ k: "center", y: y - 8, text: doc.badgeReason, size: 11, font: MONO, dim: true });
    y += 12;
  }
  ops.push({ k: "center", y: y + 10, text: STUB, size: 10.5, font: MONO, dim: true, spacing: 1.2 });
  y += 16;
  if (doc.demo) {
    ops.push({ k: "center", y: y + 10, text: "演示数据", size: 10, font: MONO, dim: true });
    y += 14;
  }
  return { ops, height: y + 26 };
}

function fit(ctx: CanvasRenderingContext2D, text: string, max: number): string {
  if (ctx.measureText(text).width <= max) return text;
  let s = text;
  while (s.length > 1 && ctx.measureText(`${s}…`).width > max) s = s.slice(0, -1);
  return `${s}…`;
}

function paperPath(ctx: CanvasRenderingContext2D, x: number, y: number, h: number) {
  ctx.beginPath();
  topEdge(PAPER_W).forEach(([px, py], i) => (i ? ctx.lineTo(x + px, y + py) : ctx.moveTo(x + px, y + py)));
  bottomEdge(PAPER_W).forEach(([px, py]) => ctx.lineTo(x + px, y + h - py));
  ctx.closePath();
}

const spacing = (ctx: CanvasRenderingContext2D, px: number) => {
  (ctx as unknown as { letterSpacing: string }).letterSpacing = `${px}px`;
};

export async function renderReceiptPng(doc: ReceiptDoc, gradient: ReceiptGradient): Promise<Blob> {
  const { ops, height } = layout(doc);
  const W = PAPER_W + MARGIN * 2;
  const H = height + MARGIN * 2 + 22;
  const canvas = document.createElement("canvas");
  canvas.width = W * SCALE;
  canvas.height = H * SCALE;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("canvas unavailable");
  ctx.scale(SCALE, SCALE);

  const bg = ctx.createLinearGradient(0, 0, W, H);
  bg.addColorStop(0, gradient.stops[0]);
  bg.addColorStop(1, gradient.stops[1]);
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, W, H);

  const px = MARGIN;
  const py = MARGIN;
  ctx.save();
  ctx.shadowColor = "rgba(0,0,0,0.22)";
  ctx.shadowBlur = 22;
  ctx.shadowOffsetY = 8;
  ctx.fillStyle = PAPER;
  paperPath(ctx, px, py, height);
  ctx.fill();
  ctx.restore();

  ctx.save();
  paperPath(ctx, px, py, height);
  ctx.clip();
  ctx.textBaseline = "alphabetic";
  const inner = PAPER_W - PAD * 2;
  for (const op of ops) {
    const y = py + op.y;
    if (op.k === "rule") {
      ctx.strokeStyle = "rgba(42,39,36,0.25)";
      ctx.setLineDash([3, 3]);
      ctx.beginPath();
      ctx.moveTo(px + PAD, y);
      ctx.lineTo(px + PAD + inner, y);
      ctx.stroke();
      ctx.setLineDash([]);
    } else if (op.k === "line") {
      ctx.font = `${op.weight ?? 400} 12.5px ${MONO}`;
      ctx.fillStyle = op.dim ? "rgba(42,39,36,0.7)" : INK;
      ctx.textAlign = "right";
      ctx.fillText(op.right, px + PAD + inner, y);
      const rw = op.right ? ctx.measureText(op.right).width + 12 : 0;
      ctx.textAlign = "left";
      ctx.fillText(fit(ctx, op.left, inner - (op.indent ?? 0) - rw), px + PAD + (op.indent ?? 0), y);
    } else if (op.k === "center") {
      ctx.font = `${op.weight ?? 400} ${op.size}px ${op.font}`;
      ctx.fillStyle = op.dim ? "rgba(42,39,36,0.7)" : INK;
      ctx.textAlign = "center";
      spacing(ctx, op.spacing ?? 0);
      ctx.fillText(fit(ctx, op.text, inner), px + PAPER_W / 2 + (op.spacing ?? 0) / 2, y);
      spacing(ctx, 0);
    } else {
      ctx.save();
      ctx.translate(px + PAPER_W / 2, y);
      ctx.rotate((-8 * Math.PI) / 180);
      ctx.font = `600 19px ${SERIF}`;
      spacing(ctx, 2.6);
      const w = ctx.measureText(op.text).width + 28;
      ctx.globalAlpha = 0.92;
      ctx.strokeStyle = STAMP;
      ctx.fillStyle = STAMP;
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.roundRect(-w / 2, -20, w, 40, 6);
      ctx.stroke();
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.roundRect(-w / 2 + 4, -16, w - 8, 32, 3);
      ctx.stroke();
      ctx.textAlign = "center";
      ctx.fillText(op.text, 1.3, 6.5);
      ctx.restore();
    }
  }
  ctx.restore();

  ctx.font = `10px ${MONO}`;
  ctx.textAlign = "center";
  ctx.fillStyle = gradient.dark ? "rgba(255,255,255,0.6)" : "rgba(42,39,36,0.55)";
  ctx.fillText("今日小票 · 仅作记录", W / 2, H - 16);

  return new Promise((resolve, reject) => canvas.toBlob((b) => (b ? resolve(b) : reject(new Error("toBlob failed"))), "image/png"));
}

export type ShareOutcome = "shared" | "cancelled" | "fallback";

/** Web Share with a file when supported; otherwise tells the caller to save the image instead. */
export async function shareReceiptFile(blob: Blob, title: string): Promise<ShareOutcome> {
  const file = new File([blob], "今日小票.png", { type: "image/png" });
  try {
    if (typeof navigator.canShare === "function" && navigator.canShare({ files: [file] })) {
      await navigator.share({ files: [file], title });
      return "shared";
    }
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") return "cancelled";
  }
  return "fallback";
}

/** Saves through a temporary link (the browser's download / "save image" path). */
export function saveBlob(blob: Blob, name = "今日小票.png"): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 4000);
}
