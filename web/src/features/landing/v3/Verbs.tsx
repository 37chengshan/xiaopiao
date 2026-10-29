import { IMG } from "../lib/assets";
import { s } from "../strings";

const VERBS = [
  { id: "pay", img: IMG.breakfastQr },
  { id: "log", img: IMG.lockscreenNight },
  { id: "print", img: IMG.thermalMacro },
  { id: "tear", img: IMG.tearReceipt },
] as const;

/** One verb at a time: each full-bleed photograph carries its verb in the centre while it passes. */
export function Verbs() {
  return (
    <section aria-label={s("verbs.label")}>
      {VERBS.map(({ id, img }) => (
        <div key={id} className="lp3-verbs__panel">
          <img className="lp3-verbs__img" src={img} alt={s(`verb.alt.${id}` as const)} width={1600} height={1000} loading="lazy" />
          <h2 className="lp3-verbs__word">{s(`verb.${id}` as const)}</h2>
          <p className="lp3-verbs__line" data-reveal>
            {s(`verb.${id}.line` as const)}
          </p>
        </div>
      ))}
    </section>
  );
}
