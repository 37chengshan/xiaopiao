/**
 * The last-line filter for the product boundaries (no advice on what to eat, no weight-loss plans, no medical
 * guidance). The primary control is the prompt; this exists because a model that ignores it must not reach the
 * person.
 *
 * Two things need context rather than a bare pattern, so detection is a function:
 * - 「多吃 / 少吃 / 多喝 / 少喝」 is advice («多喝水») but also how a review reads («比上周多吃 2 顿»,
 *   «这周少喝了 3 杯»). A quantity after it, or a 比-comparison in the same sentence, means it is a fact.
 * - «可能导致» is legitimate simulation («照这个速度月底可能导致预算超支»). It is only refused when the same
 *   sentence also talks about the body or health.
 */

/** Patterns that are advice on their own, with no context needed. */
export const ADVICE_PATTERNS = [
  // Direct or softened Chinese suggestions about what to eat or drink.
  /(建议|推荐|应该|最好|不妨|可以|尽量|记得)(你|您)?(多|少|别|不要|不)?(吃|喝|选|改|换|忌口)/,
  /忌口|戒掉|控制饮食|管住嘴|别吃了|别喝了/,
  /(减肥|减重|增肌|瘦身)(计划|方案|目标|食谱|餐)/,
  /帮你(减肥|减重|瘦)/,
  /你(吃|喝)得(太|有点|比较|不够)(多|少|油腻|咸|甜)/,
  /(营养|健康)(有|会|存在)?(风险|隐患|问题|不良|负担)/,
  // English is a first language for part of the audience; the same imperative shapes must be caught.
  /you (?:should|might|may|had better|need to)\s+(?:eat|drink|try|avoid|cut)/i,
  /(?:try|consider)\s+(?:eating|drinking|cutting|avoiding)/i,
  /cut down on|cut back on/i,
  /I(?:'| a)?d? (?:recommend|suggest)/i,
  /(?:eat|drink) (?:more|less)\b/i,
  /(?:reduce|lower) your (?:intake|calories|sugar|salt|fat)/i,
  /weight[- ]loss plan|lose weight/i,
  /(?:healthy|healthier) (?:choice|option|alternative)/i,
];

/** The verb that may be either advice or a report; decided by what follows it and by comparison context. */
const QUANTIFIED = /(多|少)(吃|喝)/g;
/** A quantity right after the verb: 「少喝了 3 杯」「多吃两顿」「少喝三杯奶茶」. */
const QUANTITY_AFTER = /^(?:了|过)?\s*(?:[0-9０-９]+|[一两三四五六七八九十几]+)/;
/** Same-sentence comparison: 「比上周多吃 2 顿」. */
const COMPARISON = /比/;
/** Simulation that names a consequence: allowed unless the sentence is about the body. */
const CAUSAL = /(可能|会)(导致|引起|带来)|不利于/;
const BODY = /(健康|身体|体重|血糖|血压|血脂|胆固醇|肥胖|营养|肠胃|睡眠|health|weight|blood|calorie)/i;
const SENTENCE = /。|！|？|!|\?|\n/;

const sentences = text => String(text).split(SENTENCE);
/** Index of the first character of the sentence containing `index`. */
const sentenceStart = (text, index) => {
  for (let i = index - 1; i >= 0; i--) if (SENTENCE.test(text[i])) return i + 1;
  return 0;
};

export function isAdvice(text) {
  if (typeof text !== 'string' || !text) return false;
  if (ADVICE_PATTERNS.some(pattern => pattern.test(text))) return true;

  // 「多/少吃（喝）」: a report wins when a quantity follows or the sentence compares against something.
  for (const match of text.matchAll(QUANTIFIED)) {
    const after = text.slice(match.index + match[0].length);
    if (QUANTITY_AFTER.test(after)) continue;
    if (COMPARISON.test(text.slice(sentenceStart(text, match.index), match.index))) continue;
    return true;
  }

  // A health consequence is advice; the same wording about money or time is the simulation the product wants.
  for (const sentence of sentences(text)) {
    if (CAUSAL.test(sentence) && BODY.test(sentence)) return true;
  }
  return false;
}
