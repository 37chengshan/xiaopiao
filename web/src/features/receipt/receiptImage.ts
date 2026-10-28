import { type DailyReceipt, CATEGORIES, yuan } from "../../domain/types";
import { dayLabel, hhmm } from "../../logic/time";

/** Canvas palette (canvas cannot read Tailwind tokens; these mirror the oklch tokens in styles.css). */
const C = {
  outer: "rgb(238, 233, 224)",
  paper: "rgb(253, 252, 248)",
  ink: "rgb(32, 32, 34)",
  ink2: "rgb(138, 138, 144)",
  line: "rgb(214, 210, 200)",
  stamp: "rgb(222, 64, 62)",
  coral: "rgb(255, 107, 74)",
};

const FONT = `ui-monospace, "SF Mono", Menlo, "PingFang SC", "Noto Sans Mono CJK SC", monospace`;
const MAX_ITEMS = 12;

function wrap(ctx: CanvasRenderingContext2D, text: string, maxWidth: number): string[] {
  const lines: string[] = [];
  let cur = "";
  for (const ch of text) {
    if (ctx.measureText(cur + ch).width > maxWidth && cur) {
      lines.push(cur);
      cur = ch;
    } else cur += ch;
  }
  if (cur) lines.push(cur);
  return lines;
}

/** Draws the receipt to a PNG blob. Pure canvas, no dependencies. */
export async function renderReceiptPng(receipt: DailyReceipt, opts: { demo: boolean }): Promise<Blob> {
  const W = 600;
  const pad = 44;
  const margin = 36;
  const scale = 2;
  const items = receipt.items.slice(0, MAX_ITEMS);
  const extra = receipt.items.length - items.length;

  const measure = document.createElement("canvas").getContext("2d")!;
  measure.font = `22px ${FONT}`;
  const commentLines = wrap(measure, receipt.aiComment, W - margin * 2 - pad * 2);
  const zero = receipt.items.length === 0;

  const H = 120 + 70 + (zero ? 120 : items.length * 40 + (extra > 0 ? 36 : 0)) + 150 + commentLines.length * 34 + 150 + 30;
  const canvas = document.createElement("canvas");
  canvas.width = (W) * scale;
  canvas.height = H * scale;
  const ctx = canvas.getContext("2d")!;
  ctx.scale(scale, scale);

  ctx.fillStyle = C.outer;
  ctx.fillRect(0, 0, W, H);

  // paper with zigzag bottom
  const left = margin;
  const right = W - margin;
  const top = 24;
  const bottom = H - 24;
  ctx.save();
  ctx.shadowColor = "rgba(0,0,0,0.12)";
  ctx.shadowBlur = 18;
  ctx.shadowOffsetY = 6;
  ctx.fillStyle = C.paper;
  ctx.beginPath();
  ctx.moveTo(left, top);
  ctx.lineTo(right, top);
  ctx.lineTo(right, bottom - 10);
  const teeth = Math.round((right - left) / 14);
  const tw = (right - left) / teeth;
  for (let i = 0; i < teeth; i++) {
    ctx.lineTo(right - tw * (i + 0.5), bottom);
    ctx.lineTo(right - tw * (i + 1), bottom - 10);
  }
  ctx.closePath();
  ctx.fill();
  ctx.restore();

  let y = top + 60;
  ctx.textBaseline = "alphabetic";
  ctx.fillStyle = C.ink;
  ctx.textAlign = "center";
  ctx.font = `800 34px ${FONT}`;
  ctx.fillText("今 日 小 票", W / 2, y);
  y += 32;
  ctx.font = `20px ${FONT}`;
  ctx.fillStyle = C.ink2;
  ctx.fillText(dayLabel(receipt.dayStart), W / 2, y);
  y += 26;

  const dash = () => {
    ctx.strokeStyle = C.line;
    ctx.setLineDash([8, 6]);
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(left + pad, y);
    ctx.lineTo(right - pad, y);
    ctx.stroke();
    ctx.setLineDash([]);
    y += 34;
  };
  dash();

  ctx.textAlign = "left";
  if (zero) {
    ctx.fillStyle = C.ink;
    ctx.textAlign = "center";
    ctx.font = `700 30px ${FONT}`;
    ctx.fillText("今日无消费 🎉", W / 2, y + 30);
    y += 90;
  } else {
    for (const t of items) {
      ctx.font = `21px ${FONT}`;
      ctx.fillStyle = C.ink2;
      ctx.textAlign = "left";
      ctx.fillText(hhmm(t.occurredAt), left + pad, y);
      ctx.fillStyle = C.ink;
      const name = `${CATEGORIES[t.category].emoji} ${t.merchant}`;
      let shown = name;
      while (ctx.measureText(shown).width > 250 && shown.length > 2) shown = shown.slice(0, -1);
      ctx.fillText(shown === name ? name : `${shown}…`, left + pad + 76, y);
      ctx.textAlign = "right";
      ctx.fillText(`${t.direction === "IN" ? "+" : ""}${yuan(t.amountCents)}`, right - pad, y);
      y += 40;
    }
    if (extra > 0) {
      ctx.textAlign = "left";
      ctx.fillStyle = C.ink2;
      ctx.fillText(`… 另有 ${extra} 笔`, left + pad, y);
      y += 36;
    }
  }
  y -= 10;
  dash();

  ctx.textAlign = "left";
  ctx.fillStyle = C.ink;
  ctx.font = `700 26px ${FONT}`;
  ctx.fillText("合计", left + pad, y + 8);
  ctx.textAlign = "right";
  ctx.font = `800 44px ${FONT}`;
  ctx.fillText(`¥${yuan(receipt.totalOutCents)}`, right - pad, y + 12);
  y += 58;

  ctx.textAlign = "left";
  ctx.font = `22px ${FONT}`;
  ctx.fillStyle = C.ink2;
  for (const line of commentLines) {
    ctx.fillText(line, left + pad, y);
    y += 34;
  }
  y += 10;

  // stamp
  const stampText = receipt.badge?.title ?? (zero ? "钱包休眠者" : "今日小票");
  ctx.save();
  ctx.translate(right - pad - 110, y + 50);
  ctx.rotate((-8 * Math.PI) / 180);
  ctx.strokeStyle = C.stamp;
  ctx.fillStyle = C.stamp;
  ctx.globalAlpha = 0.9;
  ctx.lineWidth = 5;
  ctx.font = `800 30px ${FONT}`;
  const sw = Math.max(150, ctx.measureText(stampText).width + 44);
  ctx.beginPath();
  ctx.roundRect(-sw / 2, -34, sw, 68, 12);
  ctx.stroke();
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.roundRect(-sw / 2 + 6, -28, sw - 12, 56, 8);
  ctx.stroke();
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.fillText(stampText, 0, 2);
  ctx.restore();

  ctx.textAlign = "left";
  ctx.textBaseline = "alphabetic";
  ctx.font = `18px ${FONT}`;
  ctx.fillStyle = C.coral;
  ctx.fillText("今日小票 · 每天只领一张", left + pad, bottom - 34);
  if (opts.demo) {
    ctx.fillStyle = C.ink2;
    ctx.textAlign = "right";
    ctx.fillText("演示数据", right - pad, bottom - 34);
  }

  return new Promise((resolve, reject) => canvas.toBlob((b) => (b ? resolve(b) : reject(new Error("toBlob failed"))), "image/png"));
}

export type ShareOutcome = "shared" | "cancelled" | "fallback";

/** Web Share with a file when supported; otherwise tells the caller to show the image for long-press saving. */
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
