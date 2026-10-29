import { useEffect, useRef, useState } from "react";
import { IMG } from "../lib/assets";
import { Receipt } from "../parts/Receipt";
import { s } from "../strings";

const PHOTOS = [
  { src: IMG.metroPhone, caption: "story.photo.metro", alt: "story.alt.metro" },
  { src: IMG.takeoutNight, caption: "story.photo.lunch", alt: "story.alt.lunch" },
  { src: IMG.receiptStack, caption: "story.photo.coffee", alt: "story.alt.coffee" },
  { src: IMG.friendsTable, caption: "story.photo.dinner", alt: "story.alt.dinner" },
  { src: IMG.nightDesk, caption: "story.photo.home", alt: "story.alt.home" },
] as const;

/**
 * The signature move: today's receipt holds still while the day's photographs pass beside it; each one that
 * crosses the middle of the screen prints its line, and the last (home, 21:30) lands the total and the stamp.
 */
export function Day() {
  const photos = useRef<(HTMLElement | null)[]>([]);
  const end = useRef<HTMLDivElement>(null);
  const [passed, setPassed] = useState(0);
  const [endSeen, setEndSeen] = useState(false);

  useEffect(() => {
    let frame = 0;
    const measure = () => {
      frame = 0;
      const mid = window.innerHeight * 0.55;
      let n = 0;
      for (const el of photos.current) {
        if (!el) continue;
        const r = el.getBoundingClientRect();
        if (r.top + r.height / 2 < mid) n += 1;
      }
      setPassed(n);
    };
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(measure);
    };
    measure();
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onScroll);
    return () => {
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onScroll);
      if (frame) cancelAnimationFrame(frame);
    };
  }, []);

  useEffect(() => {
    const el = end.current;
    if (!el) return;
    const io = new IntersectionObserver(([e]) => e.isIntersecting && setEndSeen(true), { threshold: 0.4 });
    io.observe(el);
    return () => io.disconnect();
  }, []);

  return (
    <section className="lp3-day" id="how" aria-label={s("verbs.label")}>
      <div className="lp3-day__grid">
        <div className="lp3-day__pin">
          <p className="lp3-day__statement" data-reveal>
            {s("story.statement")}
          </p>
          <div className="lp3-day__receipt">
            <Receipt printed={Math.min(passed, 4)} done={passed >= 5} />
          </div>
        </div>
        <div className="lp3-day__photos">
          {PHOTOS.map((p, i) => (
            <figure
              key={p.caption}
              className="lp3-day__photo"
              ref={(el) => {
                photos.current[i] = el;
              }}
            >
              <img src={p.src} alt={s(p.alt)} width={1200} height={1500} loading="lazy" />
              <figcaption>{s(p.caption)}</figcaption>
            </figure>
          ))}
        </div>
      </div>
      <div className="lp3-day__end" ref={end}>
        <Receipt printed={endSeen ? 4 : 0} done={endSeen} />
      </div>
    </section>
  );
}
