/**
 * A displacement map for `feDisplacementMap`, drawn the way Apple's Liquid Glass material bends light:
 * the centre is optically flat (neutral grey = no offset) and only a rim band of `band` px refracts,
 * with the offset growing toward the edge and pointing inward, so the rim reads as a thick, curved
 * slab instead of a blurred rectangle.
 *
 * R/G encode x/y offset around neutral 128; `scale` on the filter converts them to pixels.
 */
export interface GlassGeometry {
  width: number;
  height: number;
  radius: number;
  /** How far from the edge the glass noticeably bends. */
  band: number;
  /** Peak displacement in px at the very rim. */
  strength: number;
}

const roundedRectDistance = (
  px: number,
  py: number,
  halfW: number,
  halfH: number,
  radius: number,
) => {
  const qx = Math.abs(px - halfW) - (halfW - radius);
  const qy = Math.abs(py - halfH) - (halfH - radius);
  return (
    Math.min(Math.max(qx, qy), 0) + Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) - radius
  );
};

export function buildDisplacementMap({
  width,
  height,
  radius,
  band,
  strength,
}: GlassGeometry): string {
  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext("2d");
  if (!ctx) return "";
  const image = ctx.createImageData(width, height);
  const halfW = width / 2;
  const halfH = height / 2;
  const channelScale = 127 / strength;

  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const i = (y * width + x) * 4;
      const d = roundedRectDistance(x + 0.5, y + 0.5, halfW, halfH, radius);
      if (d > 1) {
        image.data[i] = image.data[i + 1] = 128;
        image.data[i + 2] = 128;
        image.data[i + 3] = 255;
        continue;
      }
      const inside = Math.min(1, Math.max(0, -d / band));
      // Strongest at the rim, easing to nothing where the surface turns flat.
      const push = Math.pow(1 - inside, 1.7) * strength;
      const e = 0.6;
      const gx =
        roundedRectDistance(x + e + 0.5, y + 0.5, halfW, halfH, radius) -
        roundedRectDistance(x - e + 0.5, y + 0.5, halfW, halfH, radius);
      const gy =
        roundedRectDistance(x + 0.5, y + e + 0.5, halfW, halfH, radius) -
        roundedRectDistance(x + 0.5, y - e + 0.5, halfW, halfH, radius);
      const len = Math.hypot(gx, gy) || 1;
      // The gradient points outward; sampling toward the centre is what curves the rim inward.
      image.data[i] = Math.round(128 - (gx / len) * push * channelScale);
      image.data[i + 1] = Math.round(128 - (gy / len) * push * channelScale);
      image.data[i + 2] = 128;
      image.data[i + 3] = 255;
    }
  }
  ctx.putImageData(image, 0, 0);
  return canvas.toDataURL("image/png");
}
