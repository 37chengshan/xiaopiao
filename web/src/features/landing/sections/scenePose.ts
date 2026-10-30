/**
 * Inner-screen poses for the concept studies inside the phone.
 *
 * These are framer keyframe tracks: `input` is the chapter-local scroll progress (0–1), `output` the
 * matching `transform` strings. Every length in a track must carry its unit (`0px`, not `0`): framer
 * interpolates a complex value with the unit template it parsed from the keyframes, so a single
 * unit-less `0` makes every interpolated frame unit-less too. A unit-less non-zero length is invalid
 * CSS, the browser drops the whole declaration, and the inner content freezes mid-scroll.
 */
export interface PoseTrack {
  input: number[];
  output: string[];
}

export const scenePose = {
  /** Record: the speech bubble settles in from the lower right as the user speaks. */
  speech: {
    input: [0, 0.26],
    output: ["translate3d(20px, 16px, 0px)", "translate3d(0px, 0px, 0px)"],
  },
  /** Glass: the coffee card drifts and settles while the camera descends on the dock. */
  liquid: {
    input: [0, 0.5, 1],
    output: [
      "translate3d(0px, 18px, 0px) rotate(-6deg)",
      "translate3d(0px, 0px, 0px) rotate(-3deg)",
      "translate3d(0px, -10px, 0px) rotate(-1deg)",
    ],
  },
  /** Record: the parsed chip appears once the amount has finished counting. */
  tag: {
    input: [0, 0.55, 0.68],
    output: [
      "translate3d(0px, 8px, 0px) scale(0.92)",
      "translate3d(0px, 8px, 0px) scale(0.92)",
      "translate3d(0px, 0px, 0px) scale(1)",
    ],
  },
  /** Record: the confirm button takes the press, then releases. */
  buttonPress: {
    input: [0, 0.78, 0.86, 1],
    output: ["scale(1)", "scale(1)", "scale(0.955)", "scale(1)"],
  },
  /** Stats: the callout settles over the tallest bar after the camera lands on it. */
  peak: {
    input: [0, 0.62, 0.74],
    output: [
      "translate3d(0px, 7px, 0px)",
      "translate3d(0px, 7px, 0px)",
      "translate3d(0px, 0px, 0px)",
    ],
  },
  /** Record: the parsed entry rises into place, then holds while the confirmation stamps on. */
  entry: {
    input: [0, 0.22, 0.55],
    output: [
      "translate3d(0px, 55px, 0px) rotate(4deg)",
      "translate3d(0px, 55px, 0px) rotate(4deg)",
      "translate3d(0px, 0px, 0px) rotate(0deg)",
    ],
  },
  /** Circle: the members' orbit lifts into the frame. */
  orbit: {
    input: [0, 0.4],
    output: ["translate3d(0px, 25px, 0px) scale(0.8)", "translate3d(0px, 0px, 0px) scale(1)"],
  },
  /** Circle: the weekly recap slides up over the previous block. */
  recap: {
    input: [0, 0.35, 0.68],
    output: [
      "translate3d(0px, 45px, 0px)",
      "translate3d(0px, 45px, 0px)",
      "translate3d(0px, 0px, 0px)",
    ],
  },
} satisfies Record<string, PoseTrack>;

export type ScenePoseName = keyof typeof scenePose;
