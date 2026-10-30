import { describe, expect, it } from "vitest";
import { cameraPath, project } from "./cameraPath";

const CHAPTERS = 4;
const WIDTHS: [number, number, number, number][] = [
  [1470, 1006, 336, 724],
  [1440, 960, 320, 690],
  [390, 844, 196, 422],
];

/** The moment each shot is about: the phone-screen point it centres, and where it should land. */
const SUBJECTS: { chapter: number; local: number; focus: [number, number]; screen: [number, number] }[] = [
  { chapter: 0, local: 0.72, focus: [0.55, 0.85], screen: [0.5, 0.44] },
  { chapter: 1, local: 0.7, focus: [0.56, 0.52], screen: [0.55, 0.48] },
  { chapter: 2, local: 0.32, focus: [0.5, 0.56], screen: [0.52, 0.5] },
  { chapter: 3, local: 0.8, focus: [0.5, 0.7], screen: [0.5, 0.45] },
];

describe("camera", () => {
  for (const [w, h, pw, ph] of WIDTHS) {
    it(`lands each chapter's subject where the shot says, at ${w}px`, () => {
      for (const subject of SUBJECTS) {
        const pose = cameraPath((subject.chapter + subject.local) / CHAPTERS, w, h, pw, ph);
        const at = project(pose, w, h, pw, ph, subject.focus);
        expect(at.x / w).toBeCloseTo(subject.screen[0], 1);
        expect(at.y / h).toBeCloseTo(subject.screen[1], 1);
      }
    });

    it(`keeps the device on screen for the whole section at ${w}px`, () => {
      for (let step = 0; step <= 80; step++) {
        const pose = cameraPath(step / 80, w, h, pw, ph);
        const halfW = (pw * pose.scale) / 2;
        expect(w / 2 + pose.x + halfW).toBeGreaterThan(w * 0.1);
        expect(w / 2 + pose.x - halfW).toBeLessThan(w * 0.9);
      }
    });

    it(`continues across chapter boundaries at ${w}px`, () => {
      for (let i = 1; i < CHAPTERS; i++) {
        const a = cameraPath(i / CHAPTERS - 1e-7, w, h, pw, ph);
        const b = cameraPath(i / CHAPTERS + 1e-7, w, h, pw, ph);
        for (const key of ["x", "y", "scale", "rx", "ry", "rz"] as const)
          expect(Math.abs(a[key] - b[key])).toBeLessThan(0.01);
      }
    });
  }

  it("differs per chapter: a push-in, a lateral track, a pull-back and an orbit", () => {
    const [w, h, pw, ph] = [1440, 960, 320, 690];
    const at = (chapter: number, local: number) => cameraPath((chapter + local) / CHAPTERS, w, h, pw, ph);

    const glass = { start: at(0, 0.02), end: at(0, 0.7) };
    expect(glass.end.scale).toBeGreaterThan(glass.start.scale * 1.8);

    const record = { start: at(1, 0.02), end: at(1, 0.62) };
    expect(Math.abs(record.end.x - record.start.x)).toBeGreaterThan(w * 0.15);

    const stats = { start: at(2, 0.001), end: at(2, 0.84) };
    expect(stats.end.scale).toBeLessThan(stats.start.scale * 0.6);

    const circle = { start: at(3, 0.02), end: at(3, 0.84) };
    expect(Math.abs(circle.end.ry - circle.start.ry)).toBeGreaterThan(25);
  });
});
