import { describe, expect, it } from "vitest";
import { generateDemoCrowd } from "./crowd";
import * as S from "./state";

const crowd = generateDemoCrowd();

function live(): S.MerchantState {
  return S.publishExperiment(S.selectTemplate(S.initialState, "half-vs-topping"), crowd);
}

describe("merchant state", () => {
  it("ignores unknown templates and publish without draft", () => {
    expect(S.selectTemplate(S.initialState, "nope")).toBe(S.initialState);
    expect(S.publishExperiment(S.initialState, crowd)).toBe(S.initialState);
  });
  it("selecting then publishing makes a running experiment", () => {
    const s = live();
    expect(s.experiment?.status).toBe("running");
    expect(s.experiment?.votes.length).toBe(63);
  });
  it("cannot swap template once running", () => {
    const s = live();
    expect(S.selectTemplate(s, "member-vs-weekly")).toBe(s);
  });
  it("records player choices only while running", () => {
    const s = S.recordPlayerChoice(live(), "B");
    expect(s.players).toBe(1);
    expect(s.experiment?.votes.length).toBe(64);
    const ended = S.finishExperiment(s);
    expect(S.recordPlayerChoice(ended, "A")).toBe(ended);
    expect(S.recordPlayerChoice(S.initialState, "A")).toBe(S.initialState);
  });
  it("records the owner's pick and can start over", () => {
    const s = S.chooseSuggestion(live(), "pilot");
    expect(s.chosenSuggestionId).toBe("pilot");
    expect(S.startOver()).toEqual(S.initialState);
  });
  it("does not mutate previous state", () => {
    const a = live();
    const snapshot = JSON.stringify(a);
    S.recordPlayerChoice(a, "A");
    expect(JSON.stringify(a)).toBe(snapshot);
  });
});
