import { useReducedMotion } from "framer-motion";
import { useEffect, useRef, useState } from "react";
import { IMG } from "../lib/assets";
import { s } from "../strings";

type Job = "auto" | "receipt" | "ai" | "circle" | "shop";
const JOBS: { id: Job; img: string }[] = [
  { id: "auto", img: IMG.metroPhone },
  { id: "receipt", img: IMG.nightDesk },
  { id: "ai", img: IMG.receiptStack },
  { id: "circle", img: IMG.friendsTable },
  { id: "shop", img: IMG.shopCounter },
];

/**
 * Five jobs on the night field (tt#63: 手风琴卡片). The heading holds on the left while one panel opens at a
 * time: hover, focus or tap. Until someone touches it, it walks through the five once as it comes into view.
 */
export function Features() {
  const [open, setOpen] = useState<Job>("auto");
  const touched = useRef(false);
  const root = useRef<HTMLElement>(null);
  const reduce = useReducedMotion();

  useEffect(() => {
    if (reduce) return;
    const el = root.current;
    if (!el) return;
    let timer = 0;
    let step = 0;
    const io = new IntersectionObserver(
      ([e]) => {
        if (!e.isIntersecting || touched.current || timer) return;
        timer = window.setInterval(() => {
          if (touched.current || step >= JOBS.length - 1) {
            window.clearInterval(timer);
            if (!touched.current) setOpen("auto");
            return;
          }
          step += 1;
          setOpen(JOBS[step].id);
        }, 1600);
      },
      { threshold: 0.5 },
    );
    io.observe(el);
    return () => {
      io.disconnect();
      window.clearInterval(timer);
    };
  }, [reduce]);

  const pick = (id: Job) => {
    touched.current = true;
    setOpen(id);
  };

  return (
    <section className="lp3-feat" id="features" ref={root} aria-labelledby="lp3-feat-h">
      <div className="lp3-feat__head">
        <h2 id="lp3-feat-h" data-reveal>
          {s("features.heading")}
        </h2>
        <p className="lp3-feat__hint" data-reveal style={{ ["--i" as string]: 1 }}>
          {s("features.hint")}
        </p>
      </div>
      <div className="lp3-acc">
        {JOBS.map(({ id, img }) => {
          const isOpen = open === id;
          return (
            <article
              key={id}
              className={`lp3-acc__item${isOpen ? " is-open" : ""}`}
              onPointerEnter={(e) => e.pointerType === "mouse" && pick(id)}
              data-cursor="card"
            >
              <img className="lp3-acc__photo" src={img} alt={s(`f.alt.${id}` as const)} width={1200} height={1500} loading="lazy" />
              <button
                type="button"
                className="lp3-acc__tab"
                aria-expanded={isOpen}
                aria-controls={`lp3-acc-${id}`}
                onClick={() => pick(id)}
                onFocus={() => pick(id)}
              >
                <span className="lp3-acc__vtitle">{s(`f.${id}.title` as const)}</span>
              </button>
              <div className="lp3-acc__body" id={`lp3-acc-${id}`} aria-hidden={!isOpen}>
                <h3>{s(`f.${id}.title` as const)}</h3>
                <ul>
                  {([1, 2, 3, 4] as const).map((n) => (
                    <li key={n}>{s(`f.${id}.${n}` as const)}</li>
                  ))}
                </ul>
              </div>
            </article>
          );
        })}
      </div>
    </section>
  );
}
