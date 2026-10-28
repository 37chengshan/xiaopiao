import { describe, expect, it } from "vitest";
import { generateDemoCrowd } from "./crowd";
import {
  TEMPLATES,
  addVotes,
  computeResults,
  createDraft,
  endExperiment,
  publish,
  wilson,
  type Experiment,
} from "./experiment";

const crowd = generateDemoCrowd();
const [t1, t2, t3] = TEMPLATES;

function running(t = t1): Experiment {
  return publish(createDraft(t, "e"), t, crowd);
}

describe("wilson", () => {
  it("handles n = 0", () => expect(wilson(0, 0)).toEqual([0, 1]));
  it("matches a known value (50/100)", () => {
    const [lo, hi] = wilson(50, 100);
    expect(lo).toBeCloseTo(0.4038, 3);
    expect(hi).toBeCloseTo(0.5962, 3);
  });
  it("stays within [0,1] at the extremes", () => {
    const [lo, hi] = wilson(10, 10);
    expect(hi).toBeLessThanOrEqual(1);
    expect(lo).toBeGreaterThan(0.6);
    expect(wilson(0, 10)[0]).toBe(0);
  });
  it("narrows as n grows", () => {
    const w = (n: number) => { const [a, b] = wilson(n / 2, n); return b - a; };
    expect(w(400)).toBeLessThan(w(40));
  });
});

describe("Experiment", () => {
  it("has the three required templates", () => {
    expect(TEMPLATES.map((t) => t.title)).toEqual([
      "第二杯半价 vs 免费加小料",
      "会员积分 vs 每周一杯折扣",
      "大杯加 2 元 vs 中杯原价",
    ]);
  });
  it("walks draft → running → ended", () => {
    const draft = createDraft(t1, "e");
    expect(draft.status).toBe("draft");
    expect(draft.votes).toHaveLength(0);
    const live = publish(draft, t1, crowd);
    expect(live.status).toBe("running");
    expect(live.votes).toHaveLength(t1.demoSampleSize);
    expect(endExperiment(live).status).toBe("ended");
    expect(endExperiment(draft).status).toBe("draft");
  });
  it("publishing is deterministic and idempotent", () => {
    expect(running()).toEqual(running());
    const live = running();
    expect(publish(live, t1, crowd)).toBe(live);
  });
  it("only running experiments take votes, one per voter", () => {
    const draft = createDraft(t1, "e");
    expect(addVotes(draft, [{ voterId: "p1", choice: "A", source: "player" }])).toBe(draft);
    const live = running();
    const v = { voterId: "p1", choice: "A", source: "player" } as const;
    const once = addVotes(live, [v]);
    expect(once.votes).toHaveLength(live.votes.length + 1);
    expect(addVotes(once, [v])).toBe(once);
    const ended = endExperiment(once);
    expect(addVotes(ended, [{ ...v, voterId: "p2" }])).toBe(ended);
  });
  it("does not mutate inputs", () => {
    const live = running();
    const before = live.votes.length;
    addVotes(live, [{ voterId: "x", choice: "B", source: "player" }]);
    expect(live.votes).toHaveLength(before);
  });
});

describe("results", () => {
  it("first template reaches 63 people with a stable note", () => {
    const r = computeResults(running(t1));
    expect(r.sampleSize).toBe(63);
    expect(r.note).toBe("样本 63 人，结论较稳定");
    expect(r.isClear).toBe(true);
    expect(r.leader).toBe("B");
  });
  it("small sample is flagged", () => {
    const r = computeResults(running(t3));
    expect(r.sampleSize).toBe(24);
    expect(r.note).toContain("样本偏少，仅供参考");
    expect(r.isClear).toBe(false);
  });
  it("shares sum to 1 and counts match the sample", () => {
    for (const t of [t1, t2, t3]) {
      const r = computeResults(running(t));
      expect(r.options[0].share + r.options[1].share).toBeCloseTo(1, 10);
      expect(r.options[0].count + r.options[1].count).toBe(r.sampleSize);
      for (const o of r.options) {
        expect(o.ci[0]).toBeLessThanOrEqual(o.share);
        expect(o.ci[1]).toBeGreaterThanOrEqual(o.share);
      }
    }
  });
  it("an enrolled close race is not called clear", () => {
    const r = computeResults(running(t2));
    expect(r.sampleSize).toBe(44);
    if (!r.isClear) expect(r.note).toContain("差距不大");
  });
  it("empty experiment has no leader", () => {
    const r = computeResults(createDraft(t1, "e"));
    expect(r.sampleSize).toBe(0);
    expect(r.leader).toBeNull();
  });
  it("counts player votes separately", () => {
    const live = addVotes(running(), [{ voterId: "p1", choice: "B", source: "player" }]);
    const r = computeResults(live);
    expect(r.playerCount).toBe(1);
    expect(r.demoCount).toBe(63);
  });
});
