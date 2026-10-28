import { describe, expect, it } from "vitest";
import { createDraft, publish, TEMPLATES } from "../../merchant/logic/experiment";
import { generateDemoCrowd } from "../../merchant/logic/crowd";
import { buildRounds, pickBadge, tagOf, type GameAnswer } from "./game";

const crowd = generateDemoCrowd();
const running = publish(createDraft(TEMPLATES[0], "e1"), TEMPLATES[0], crowd);

describe("buildRounds", () => {
  it("includes the experiment round exactly once when running", () => {
    const rounds = buildRounds(running);
    expect(rounds.filter((r) => r.kind === "experiment")).toHaveLength(1);
    expect(rounds.length).toBeGreaterThanOrEqual(5);
    expect(rounds.length).toBeLessThanOrEqual(8);
  });
  it("is fillers only for draft / no experiment", () => {
    const draft = createDraft(TEMPLATES[0], "e2");
    expect(buildRounds(draft).every((r) => r.kind === "filler")).toBe(true);
    expect(buildRounds(null)).toHaveLength(6);
  });
  it("is deterministic and clamps the round count", () => {
    expect(buildRounds(running, 3)).toEqual(buildRounds(running, 3));
    expect(buildRounds(null, 1, 99)).toHaveLength(7); // only 7 fillers exist, 8 max
    expect(buildRounds(null, 1, 1)).toHaveLength(5);
  });
  it("has unique round ids", () => {
    const ids = buildRounds(running).map((r) => r.id);
    expect(new Set(ids).size).toBe(ids.length);
  });
});

describe("pickBadge", () => {
  const a = (tag: GameAnswer["tag"]): GameAnswer => ({ roundId: tag, kind: "filler", choice: "A", tag });
  it("picks the most frequent tag", () => {
    expect(pickBadge([a("topping"), a("topping"), a("deal")]).title).toBe("小料党");
  });
  it("breaks ties by fixed order and defaults to classic", () => {
    expect(pickBadge([a("deal"), a("topping")]).tag).toBe("topping");
    expect(pickBadge([]).tag).toBe("classic");
  });
  it("tagOf reads the tag of the chosen option", () => {
    expect(tagOf(buildRounds(running).find((r) => r.kind === "experiment")!, "B")).toBe("topping");
  });
});
