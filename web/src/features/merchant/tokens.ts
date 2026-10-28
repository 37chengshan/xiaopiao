import type { CSSProperties } from "react";

/**
 * Palette for the merchant + game surfaces (oklch). Defined once here so the integrator can swap to the
 * shared tokens in styles.css later. Components read them via Tailwind `bg-(--mb-card)` style classes.
 */
export const tokens = {
  canvas: "oklch(0.962 0.006 90)", // #F5F3EE warm paper
  surface: "oklch(1 0 0)", // white card
  ink: "oklch(0.22 0.004 285)", // #1C1C1E
  inkSoft: "oklch(0.66 0.006 285)", // #8E8E93
  hairline: "oklch(0.9 0.005 90)",
  coral: "oklch(0.7 0.19 35)", // #FF6B4A
  coralSoft: "oklch(0.94 0.04 40)", // #FFE7E0
  coralDeep: "oklch(0.6 0.2 34)",
  stamp: "oklch(0.62 0.21 22)", // #E5484D
  mint: "oklch(0.78 0.1 165)",
  sky: "oklch(0.8 0.09 235)",
  grape: "oklch(0.7 0.12 305)",
  butter: "oklch(0.88 0.11 95)",
} as const;

/** CSS custom properties; apply on the page wrapper and on portaled content (drawer). */
export const tokenVars = {
  "--mb-canvas": tokens.canvas,
  "--mb-card": tokens.surface,
  "--mb-ink": tokens.ink,
  "--mb-ink-soft": tokens.inkSoft,
  "--mb-line": tokens.hairline,
  "--mb-coral": tokens.coral,
  "--mb-coral-soft": tokens.coralSoft,
  "--mb-coral-deep": tokens.coralDeep,
  "--mb-stamp": tokens.stamp,
  "--mb-mint": tokens.mint,
  "--mb-sky": tokens.sky,
  "--mb-grape": tokens.grape,
  "--mb-butter": tokens.butter,
} as CSSProperties;

export const spring = { type: "spring", stiffness: 380, damping: 28 } as const;
export const softSpring = { type: "spring", stiffness: 220, damping: 24 } as const;
