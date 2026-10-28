import { describe, expect, it } from "vitest";
import { aggregate } from "./aggregator";
import { generateDemoCrowd } from "./crowd";
import { TEMPLATES, createDraft, publish } from "./experiment";
import { generateSuggestions } from "./suggestions";

const crowd = generateDemoCrowd();
const insights = aggregate(crowd);
const BANNED = /保证|一定会|必然|翻倍|增收|赚钱|提升[^，。]*%|稳赚/;

describe("SuggestionGenerator", () => {
  for (const t of TEMPLATES) {
    it(`returns exactly three distinct cards for ${t.id}`, () => {
      const exp = publish(createDraft(t, "e"), t, crowd);
      const cards = generateSuggestions(exp, insights);
      expect(cards).toHaveLength(3);
      expect(new Set(cards.map((c) => c.id)).size).toBe(3);
      for (const c of cards) {
        expect(c.pros.length).toBeGreaterThan(0);
        expect(c.cons.length).toBeGreaterThan(0);
        expect(c.reviewNote).toContain("两周后复盘");
      }
    });
    it(`never promises revenue for ${t.id}`, () => {
      const exp = publish(createDraft(t, "e"), t, crowd);
      const text = JSON.stringify(generateSuggestions(exp, insights));
      expect(text).not.toMatch(BANNED);
    });
  }
  it("is deterministic", () => {
    const t = TEMPLATES[0];
    const exp = publish(createDraft(t, "e"), t, crowd);
    expect(generateSuggestions(exp, insights)).toEqual(generateSuggestions(exp, insights));
  });
  it("works for an experiment with no votes", () => {
    const cards = generateSuggestions(createDraft(TEMPLATES[0], "e"), insights);
    expect(cards).toHaveLength(3);
  });
});
