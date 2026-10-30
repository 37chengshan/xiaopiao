/** Camera coordinates are stage pixels, never a cropped inner-screen viewport. */
export interface CameraPose {
  x: number;
  y: number;
  scale: number;
  rx: number;
  ry: number;
  rz: number;
  focus: number;
}

export const cameraSmooth = (value: number) => {
  const x = Math.max(0, Math.min(1, value));
  return x * x * (3 - 2 * x);
};

/**
 * A keyframe for one moment of a shot. `screen` is where the subject should sit in the stage (0–1),
 * `focus` names the point on the phone screen the shot is about — when present the camera lands that
 * point on `screen`, which is how a detail ends up in the visual centre without cropping the device.
 */
interface Key {
  t: number;
  screen: [number, number];
  scale: number;
  rx: number;
  ry: number;
  rz: number;
  focus?: [number, number];
}

/**
 * Four different shots rather than one recipe repeated: a descent onto the dock, a lateral track across
 * the entry card, a pull-back that reveals the week, and an orbit that lands on the circle's recap.
 */
const MOVES: Key[][] = [
  [
    { t: 0, screen: [0.6, 0.52], scale: 0.86, rx: 5, ry: -18, rz: -6 },
    { t: 0.34, screen: [0.52, 0.47], scale: 1.58, rx: 2, ry: -7, rz: -2, focus: [0.5, 0.84] },
    { t: 0.64, screen: [0.5, 0.44], scale: 2.06, rx: 0, ry: 0, rz: 0, focus: [0.55, 0.85] },
    { t: 0.8, screen: [0.5, 0.44], scale: 2.02, rx: 0, ry: 0, rz: 0, focus: [0.55, 0.85] },
    { t: 1, screen: [0.36, 0.5], scale: 0.9, rx: -3, ry: 22, rz: 5 },
  ],
  [
    { t: 0, screen: [0.32, 0.5], scale: 0.92, rx: -2, ry: 22, rz: 4 },
    { t: 0.3, screen: [0.42, 0.5], scale: 1.5, rx: 0, ry: 9, rz: 1, focus: [0.5, 0.34] },
    { t: 0.62, screen: [0.55, 0.48], scale: 1.96, rx: 0, ry: 0, rz: 0, focus: [0.56, 0.52] },
    { t: 0.8, screen: [0.55, 0.48], scale: 1.9, rx: 0, ry: 0, rz: 0, focus: [0.56, 0.52] },
    { t: 1, screen: [0.5, 0.5], scale: 2.16, rx: 0, ry: 0, rz: 0, focus: [0.5, 0.6] },
  ],
  [
    { t: 0.32, screen: [0.52, 0.5], scale: 1.88, rx: 0, ry: -4, rz: 0, focus: [0.5, 0.56] },
    { t: 0.64, screen: [0.5, 0.5], scale: 1.46, rx: 0, ry: -9, rz: -2, focus: [0.5, 0.5] },
    { t: 0.84, screen: [0.5, 0.5], scale: 1.02, rx: 0, ry: -14, rz: -3 },
    { t: 1, screen: [0.62, 0.5], scale: 0.92, rx: 2, ry: -22, rz: -5 },
  ],
  [
    { t: 0, screen: [0.44, 0.5], scale: 0.96, rx: 3, ry: -24, rz: -6 },
    { t: 0.3, screen: [0.48, 0.5], scale: 1.22, rx: 1, ry: -13, rz: -2 },
    { t: 0.6, screen: [0.52, 0.47], scale: 1.62, rx: 0, ry: -2, rz: 0, focus: [0.5, 0.62] },
    { t: 0.84, screen: [0.5, 0.45], scale: 1.8, rx: 0, ry: 9, rz: 2, focus: [0.5, 0.7] },
    { t: 1, screen: [0.34, 0.5], scale: 0.92, rx: 3, ry: 18, rz: 5 },
  ],
];

/** Each shot starts from the pose the previous one ended on, so chapters never jump. */
const CHAINED = MOVES.map((move, i) =>
  i === 0 ? move : [{ ...MOVES[i - 1][MOVES[i - 1].length - 1], t: 0 }, ...move.filter((key) => key.t > 0)],
);

/** The camera never pushes in so far that the device stops reading as a device. */
function fitScale(w: number, phoneW: number) {
  return w <= 900 ? (w * 0.88) / phoneW : (w * 0.58) / phoneW;
}

function poseOf(key: Key, w: number, h: number, phoneW: number, phoneH: number): CameraPose {
  const scale = Math.min(key.scale, fitScale(w, phoneW) * (key.focus ? 1 : 1.05));
  const point = key.focus
    ? {
        x: 9 + (phoneW - 18) * key.focus[0] - phoneW / 2,
        y: 9 + (phoneH - 18) * key.focus[1] - phoneH / 2,
      }
    : { x: 0, y: 0 };
  return {
    x: (key.screen[0] - 0.5) * w - point.x * scale,
    y: (key.screen[1] - 0.5) * h - point.y * scale,
    scale,
    rx: key.rx,
    ry: key.ry,
    rz: key.rz,
    focus: key.focus ? 1 : 0,
  };
}

export function cameraPath(
  p: number,
  w: number,
  h: number,
  phoneW: number,
  phoneH: number,
): CameraPose {
  const count = MOVES.length;
  const phase = Math.min(count - 1e-6, Math.max(0, p) * count);
  const i = Math.floor(phase);
  const local = phase - i;
  const keys = CHAINED[i];

  let next = keys.findIndex((key) => key.t >= local);
  if (next <= 0) next = 1;
  const from = keys[next - 1];
  const to = keys[next];
  const span = Math.max(1e-6, to.t - from.t);
  const t = cameraSmooth((local - from.t) / span);

  const a = poseOf(from, w, h, phoneW, phoneH);
  const b = poseOf(to, w, h, phoneW, phoneH);
  return {
    x: a.x + (b.x - a.x) * t,
    y: a.y + (b.y - a.y) * t,
    scale: a.scale + (b.scale - a.scale) * t,
    rx: a.rx + (b.rx - a.rx) * t,
    ry: a.ry + (b.ry - a.ry) * t,
    rz: a.rz + (b.rz - a.rz) * t,
    focus: a.focus + (b.focus - a.focus) * t,
  };
}

/** Where a focus point of the phone lands on the stage, for tests and for the copy placement. */
export function project(
  pose: CameraPose,
  w: number,
  h: number,
  phoneW: number,
  phoneH: number,
  focus: [number, number],
) {
  return {
    x: w / 2 + pose.x + (9 + (phoneW - 18) * focus[0] - phoneW / 2) * pose.scale,
    y: h / 2 + pose.y + (9 + (phoneH - 18) * focus[1] - phoneH / 2) * pose.scale,
  };
}
