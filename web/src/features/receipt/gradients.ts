/**
 * Share-card backdrops (docs/DESIGN.md §7.6). Muted on purpose: the paper is the hero, the gradient is the table it lies on.
 * These are image data for the exported PNG, not UI colours, so they are literal values rather than theme tokens.
 */
import { s, type ReceiptKey } from "./strings";

export interface ReceiptGradient {
  id: string;
  /** key into the receipt dictionary; read it through `gradientName` so it follows the reading language */
  nameKey: ReceiptKey;
  /** top-left → bottom-right */
  stops: readonly [string, string];
  /** true when the card is dark enough that its footer text must be light */
  dark: boolean;
}

export const GRADIENTS: readonly ReceiptGradient[] = [
  { id: "terracotta", nameKey: "theme.terracotta", stops: ["#D9A58E", "#B8694B"], dark: false },
  { id: "apricot", nameKey: "theme.apricot", stops: ["#F4D5B2", "#E3A98B"], dark: false },
  { id: "sand", nameKey: "theme.sand", stops: ["#E8DCC4", "#C8B896"], dark: false },
  { id: "moss", nameKey: "theme.moss", stops: ["#AEBC9E", "#6E8869"], dark: false },
  { id: "mist", nameKey: "theme.mist", stops: ["#BCCADA", "#8AA1B8"], dark: false },
  { id: "dusk", nameKey: "theme.dusk", stops: ["#BBA9C8", "#8875A2"], dark: false },
  { id: "midnight", nameKey: "theme.midnight", stops: ["#3B3B40", "#161618"], dark: true },
  { id: "cream", nameKey: "theme.cream", stops: ["#F7EEDB", "#E9DAB6"], dark: false },
  { id: "peach", nameKey: "theme.peach", stops: ["#F5C5B9", "#E39A8D"], dark: false },
  { id: "celadon", nameKey: "theme.celadon", stops: ["#C1D9D0", "#8CB4A6"], dark: false },
  { id: "slate", nameKey: "theme.slate", stops: ["#B9B8B3", "#84837E"], dark: false },
  { id: "sakura", nameKey: "theme.sakura", stops: ["#5E4159", "#2A1A29"], dark: true },
];

export const DEFAULT_GRADIENT = GRADIENTS[0];

export function gradientById(id: string | null | undefined): ReceiptGradient {
  return GRADIENTS.find((g) => g.id === id) ?? DEFAULT_GRADIENT;
}

export function cssGradient(g: ReceiptGradient): string {
  return `linear-gradient(135deg, ${g.stops[0]}, ${g.stops[1]})`;
}

/** Theme name in the reading language ("陶土" / "Terracotta"). Call from a component that subscribes to `useLocale`. */
export function gradientName(g: ReceiptGradient): string {
  return s(g.nameKey);
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
