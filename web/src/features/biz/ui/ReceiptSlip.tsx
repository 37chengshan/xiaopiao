import { useId } from "react";
import { cn } from "@/lib/utils";
import { HAIRLINE, INK, MUTED, PAPER, type ReceiptSpec } from "../receipt";

const FONT = 'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace';

/** Sawtooth bottom edge, like a torn thermal slip. */
function tornEdge(width: number, height: number): string {
  const teeth = 14;
  const depth = 4;
  const step = width / teeth;
  let d = `M0 0 H${width} V${height - depth}`;
  for (let i = teeth; i > 0; i--) {
    const x = step * i;
    d += ` L${x - step / 2} ${height} L${x - step} ${height - depth}`;
  }
  return `${d} Z`;
}

/**
 * The slip as one SVG: the same node is shown on screen, printed, and rasterised for the PNG export,
 * so the three can never disagree. Sizes come from `receipt.ts` in receipt units (58mm ≈ 219).
 */
export function ReceiptSlip({ spec, className, label }: { spec: ReceiptSpec; className?: string; label: string }) {
  const clipId = `slip-${useId().replace(/[:]/g, "")}`;
  const heightMm = (spec.mm * spec.height) / spec.width;
  return (
    <svg
      className={cn("biz-slip block", className)}
      data-mm={spec.mm}
      viewBox={`0 0 ${spec.width} ${spec.height}`}
      width={`${spec.mm}mm`}
      height={`${heightMm.toFixed(2)}mm`}
      role="img"
      aria-label={label}
      style={{ filter: "drop-shadow(0 10px 24px oklch(0 0 0 / 12%))" }}
    >
      <defs>
        <clipPath id={clipId}>
          <path d={tornEdge(spec.width, spec.height)} />
        </clipPath>
      </defs>
      <g clipPath={`url(#${clipId})`}>
        <rect x={0} y={0} width={spec.width} height={spec.height} fill={PAPER} />
        {spec.rows.map((row, index) => {
          if (row.kind === "gap") return null;
          if (row.kind === "rule") {
            return (
              <line
                key={index}
                x1={16}
                x2={spec.width - 16}
                y1={row.y}
                y2={row.y}
                stroke={HAIRLINE}
                strokeWidth={1}
                strokeDasharray={row.dash}
              />
            );
          }
          const fill = row.muted ? MUTED : INK;
          if (row.kind === "row") {
            return (
              <g key={index}>
                <text x={16} y={row.y} fontFamily={FONT} fontSize={row.size} fill={fill} letterSpacing={0.2}>
                  {row.left}
                </text>
                <text
                  x={spec.width - 16}
                  y={row.y}
                  fontFamily={FONT}
                  fontSize={row.size}
                  fill={fill}
                  textAnchor="end"
                >
                  {row.right}
                </text>
              </g>
            );
          }
          return (
            <text
              key={index}
              x={spec.width / 2}
              y={row.y}
              fontFamily={FONT}
              fontSize={row.size}
              fontWeight={row.weight}
              fill={fill}
              textAnchor="middle"
            >
              {row.text}
            </text>
          );
        })}
      </g>
    </svg>
  );
}

/** Serialises the live slip and downloads it as a 4× PNG. Uses the same node the user sees. */
export async function exportSlipPng(svg: SVGSVGElement, spec: ReceiptSpec, fileName: string): Promise<void> {
  const clone = svg.cloneNode(true) as SVGSVGElement;
  clone.setAttribute("xmlns", "http://www.w3.org/2000/svg");
  clone.setAttribute("width", `${spec.width}`);
  clone.setAttribute("height", `${spec.height}`);
  clone.removeAttribute("style");
  const markup = new XMLSerializer().serializeToString(clone);
  const url = URL.createObjectURL(new Blob([markup], { type: "image/svg+xml;charset=utf-8" }));
  try {
    const image = new Image();
    await new Promise<void>((resolve, reject) => {
      image.onload = () => resolve();
      image.onerror = () => reject(new Error("render"));
      image.src = url;
    });
    const scale = 4;
    const canvas = document.createElement("canvas");
    canvas.width = spec.width * scale;
    canvas.height = spec.height * scale;
    const ctx = canvas.getContext("2d");
    if (!ctx) throw new Error("render");
    ctx.fillStyle = PAPER;
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(image, 0, 0, canvas.width, canvas.height);
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/png"));
    if (!blob) throw new Error("render");
    const pngUrl = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = pngUrl;
    anchor.download = fileName;
    anchor.click();
    URL.revokeObjectURL(pngUrl);
  } finally {
    URL.revokeObjectURL(url);
  }
}

/**
 * Prints the given slip on its own sheet at its real roll width. `desktop.css` hides every direct
 * child of `<body>` except `#d-print-root` and then reveals that node, so the slip is cloned into
 * a temporary body-level `#d-print-root` for the duration of the dialog. The preview lives inside
 * `#root`; printing it there hid its own ancestor, which is exactly why 日报 came out blank.
 * The `@page` size is injected for the same window: a named `@page` is not honoured by every
 * print path, while the default one always is. Height comes from the slip's own geometry.
 */
export function printSlip(spec: ReceiptSpec, slip: SVGSVGElement): void {
  const heightMm = (spec.mm * spec.height) / spec.width;

  const sheet = document.createElement("div");
  sheet.id = "d-print-root";
  sheet.className = "d-print-root";
  sheet.appendChild(slip.cloneNode(true));
  document.body.appendChild(sheet);

  const page = document.createElement("style");
  page.textContent = `@page { size: ${spec.mm}mm ${heightMm.toFixed(1)}mm; margin: 0; }`;
  document.head.appendChild(page);

  const cleanup = () => {
    page.remove();
    sheet.remove();
  };
  window.addEventListener("afterprint", cleanup, { once: true });
  window.print();
  window.setTimeout(cleanup, 60_000);
}
