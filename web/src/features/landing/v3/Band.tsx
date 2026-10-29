import { s } from "../strings";

const TITLES = [
  "title.meal",
  "title.transport",
  "title.drink",
  "title.oneAndDone",
  "title.smallJoys",
  "title.grocery",
  "title.lavish",
  "title.maniac",
  "title.fun",
  "title.sleeping",
] as const;

/** The stamps the receipt can print, running off both edges; one line below says where they come from. */
export function Band() {
  const row = TITLES.map((k) => (
    <span key={k} className="lp3-band__item">
      {s(k)}
    </span>
  ));
  return (
    <section aria-labelledby="lp3-band-line">
      <div className="lp3-band" aria-hidden="true">
        <div className="lp3-band__track">
          {row}
          {row.map((el) => (
            <span key={`dup-${el.key}`} className="lp3-band__item">
              {el.props.children}
            </span>
          ))}
        </div>
      </div>
      <p className="lp3-band__line" id="lp3-band-line" data-reveal>
        {s("titles.line")}
      </p>
    </section>
  );
}
