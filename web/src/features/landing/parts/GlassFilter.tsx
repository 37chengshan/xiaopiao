import { useMemo } from "react";
import { buildDisplacementMap } from "./displacementMap";

/**
 * Hidden `<defs>` for one piece of Liquid Glass. The element itself applies this filter through
 * `backdrop-filter`, so what bends is the real page behind it — the same trick Apple's material needs
 * and the reason a baked screenshot never looked right.
 *
 * Chromium runs the full pipeline: three displacement passes (one per channel, slightly different
 * strengths) recombined, which is where the thin colour fringe on the rim comes from. Browsers that
 * ignore `url()` in `backdrop-filter` still get the blur and the tint.
 */
export function GlassFilter({
  id,
  width,
  height,
  radius,
  band = 16,
  strength = 26,
}: {
  id: string;
  width: number;
  height: number;
  radius: number;
  band?: number;
  strength?: number;
}) {
  const map = useMemo(
    () =>
      typeof document === "undefined"
        ? ""
        : buildDisplacementMap({ width, height, radius, band, strength }),
    [width, height, radius, band, strength],
  );
  if (!map) return null;

  const pass = (suffix: string, scale: number, row: string) => (
    <>
      <feDisplacementMap
        in="SourceGraphic"
        in2="map"
        scale={scale}
        xChannelSelector="R"
        yChannelSelector="G"
        result={`d${suffix}`}
      />
      <feColorMatrix
        in={`d${suffix}`}
        type="matrix"
        values={`${row} 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 1 0`}
        result={`c${suffix}`}
      />
    </>
  );

  return (
    <svg className="lx-glass-defs" aria-hidden="true" focusable="false">
      <defs>
        <filter id={id} colorInterpolationFilters="sRGB" x="0" y="0" width="100%" height="100%">
          <feImage
            href={map}
            result="map"
            x="0"
            y="0"
            width={width}
            height={height}
            preserveAspectRatio="none"
          />
          {pass("r", strength * 1.06, "1 0 0 0 0")}
          {pass("g", strength, "0 1 0 0 0")}
          {pass("b", strength * 0.94, "0 0 1 0 0")}
          <feBlend in="cr" in2="cg" mode="screen" result="rg" />
          <feBlend in="rg" in2="cb" mode="screen" />
        </filter>
      </defs>
    </svg>
  );
}
