import { describe, expect, it } from "vitest";
import { scenePose } from "./scenePose";

const LENGTH = /^-?(?:\d+\.?\d*|\.\d+)px$/;

function translateArguments(value: string): string[][] {
  return [...value.matchAll(/translate3d\(([^)]*)\)/g)].map((match) =>
    match[1].split(",").map((part) => part.trim()),
  );
}

describe("the inner-screen pose tracks keep the phone's content moving", () => {
  it("gives every translate3d component a px unit, so no interpolated frame is invalid CSS", () => {
    for (const [name, track] of Object.entries(scenePose)) {
      expect(track.input.length, name).toBe(track.output.length);
      for (const value of track.output) {
        for (const parts of translateArguments(value)) {
          expect(parts.length, `${name}: ${value}`).toBe(3);
          for (const part of parts) expect(part, `${name}: ${value}`).toMatch(LENGTH);
        }
        // Two-axis translate helpers must carry units too: framer interpolates the template it parsed,
        // so one unit-less key makes every in-between frame unit-less and the browser drops it.
        for (const helper of value.matchAll(/translate(?:X|Y|Z)?\(([^)]*)\)/g)) {
          for (const part of helper[1].split(",").map((item) => item.trim()))
            expect(part, `${name}: ${value}`).toMatch(LENGTH);
        }
      }
    }
  });

  it("changes inside every window instead of holding one frozen frame", () => {
    for (const [name, track] of Object.entries(scenePose)) {
      expect(new Set(track.output).size, name).toBeGreaterThan(1);
      for (let i = 1; i < track.input.length; i++) {
        expect(track.input[i], name).toBeGreaterThan(track.input[i - 1]);
      }
    }
  });
});
