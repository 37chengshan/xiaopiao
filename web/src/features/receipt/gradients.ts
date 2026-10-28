/**
 * Share-card backdrops (docs/DESIGN.md §7.6). Muted on purpose: the paper is the hero, the gradient is the table it lies on.
 * These are image data for the exported PNG, not UI colours, so they are literal values rather than theme tokens.
 */
export interface ReceiptGradient {
  id: string;
  name: string;
  /** top-left → bottom-right */
  stops: readonly [string, string];
  /** true when the card is dark enough that its footer text must be light */
  dark: boolean;
}

export const GRADIENTS: readonly ReceiptGradient[] = [
  { id: "terracotta", name: "陶土", stops: ["#D9A58E", "#B8694B"], dark: false },
  { id: "apricot", name: "杏", stops: ["#F4D5B2", "#E3A98B"], dark: false },
  { id: "sand", name: "沙", stops: ["#E8DCC4", "#C8B896"], dark: false },
  { id: "moss", name: "苔绿", stops: ["#AEBC9E", "#6E8869"], dark: false },
  { id: "mist", name: "雾蓝", stops: ["#BCCADA", "#8AA1B8"], dark: false },
  { id: "dusk", name: "暮紫", stops: ["#BBA9C8", "#8875A2"], dark: false },
  { id: "midnight", name: "墨夜", stops: ["#3B3B40", "#161618"], dark: true },
  { id: "cream", name: "奶油", stops: ["#F7EEDB", "#E9DAB6"], dark: false },
  { id: "peach", name: "桃", stops: ["#F5C5B9", "#E39A8D"], dark: false },
  { id: "celadon", name: "青瓷", stops: ["#C1D9D0", "#8CB4A6"], dark: false },
  { id: "slate", name: "灰岩", stops: ["#B9B8B3", "#84837E"], dark: false },
  { id: "sakura", name: "夜樱", stops: ["#5E4159", "#2A1A29"], dark: true },
];

export const DEFAULT_GRADIENT = GRADIENTS[0];

export function gradientById(id: string | null | undefined): ReceiptGradient {
  return GRADIENTS.find((g) => g.id === id) ?? DEFAULT_GRADIENT;
}

export function cssGradient(g: ReceiptGradient): string {
  return `linear-gradient(135deg, ${g.stops[0]}, ${g.stops[1]})`;
}

/** WCAG relative luminance of a #rrggbb colour. */
export function luminance(hex: string): number {
  const n = parseInt(hex.slice(1), 16);
  const ch = [(n >> 16) & 255, (n >> 8) & 255, n & 255].map((v) => {
    const s = v / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * ch[0] + 0.7152 * ch[1] + 0.0722 * ch[2];
}
