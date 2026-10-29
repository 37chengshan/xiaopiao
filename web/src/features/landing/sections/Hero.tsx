import { useCallback, useEffect, useRef } from "react";
import { s } from "../strings";
import { landingDate } from "../format";
import { useLocale } from "@/lib/strings";
import { usePrefersReducedMotion } from "../lib/motion";
import { TopNav } from "./TopNav";

const EASE = "cubic-bezier(.45,0,.2,1)";
const NAV_EASE = "cubic-bezier(.2,.8,.2,1)";

/**
 * L1 — the paper slot opens. One 2.1s timeline (wild-pill-hero): the hairline between 今日 and 小票
 * cracks into a slit, a circle, then a 2:1 capsule that pushes the two words apart; the nav drops in
 * and the bottom bar rises. "再打一张" plays the same timeline backwards, then forwards again.
 */
export function Hero() {
  const reduce = usePrefersReducedMotion();
  useLocale();
  const sectionRef = useRef<HTMLElement | null>(null);
  const pillRef = useRef<HTMLDivElement | null>(null);
  const clipRef = useRef<HTMLDivElement | null>(null);
  const lineRef = useRef<HTMLSpanElement | null>(null);
  const rowRef = useRef<HTMLDivElement | null>(null);
  const navRef = useRef<HTMLElement | null>(null);
  const footRef = useRef<HTMLDivElement | null>(null);
  const ruleRef = useRef<HTMLDivElement | null>(null);
  const animations = useRef<Animation[]>([]);
  const timers = useRef<number[]>([]);

  const finalGeometry = useCallback(() => {
    const pill = pillRef.current;
    const row = rowRef.current;
    if (!pill || !row) return null;
    const fs = Number.parseFloat(getComputedStyle(pill).fontSize) || 64;
    const mobile = window.matchMedia("(max-width: 720px)").matches;
    const finalW = mobile ? row.clientWidth : fs * 3.62;
    const finalH = mobile ? Math.min(row.clientWidth / 1.85, window.innerHeight * 0.42) : fs * 1.81;
    return { fs, mobile, finalW, finalH };
  }, []);

  const settle = useCallback(() => {
    const geo = finalGeometry();
    animations.current.forEach((a) => a.cancel());
    animations.current = [];
    timers.current.forEach((t) => window.clearTimeout(t));
    if (pillRef.current) {
      if (geo) {
        pillRef.current.style.width = `${geo.finalW}px`;
        pillRef.current.style.height = `${geo.finalH}px`;
      } else {
        pillRef.current.style.width = "3.62em";
        pillRef.current.style.height = "1.81em";
      }
    }
    if (clipRef.current) clipRef.current.style.clipPath = "inset(0 0 0 0)";
    if (lineRef.current) lineRef.current.style.opacity = "0";
    if (navRef.current) navRef.current.style.transform = "none";
    if (footRef.current) footRef.current.style.transform = "none";
    if (ruleRef.current) ruleRef.current.style.transform = "scaleX(1)";
  }, [finalGeometry]);

  const reset = useCallback(() => {
    const geo = finalGeometry();
    animations.current.forEach((a) => a.cancel());
    animations.current = [];
    if (!geo) return;
    const hair = Math.max(2, geo.fs * 0.012);
    if (pillRef.current) {
      pillRef.current.style.width = `${geo.mobile ? geo.finalW : hair}px`;
      pillRef.current.style.height = `${geo.mobile ? 2 : geo.finalH}px`;
    }
    if (clipRef.current) clipRef.current.style.clipPath = geo.mobile ? "inset(0 50% 0 50%)" : "inset(50% 0 50% 0)";
    if (lineRef.current) lineRef.current.style.opacity = "1";
    if (navRef.current) navRef.current.style.transform = "translateY(-140%)";
    if (footRef.current) footRef.current.style.transform = "translateY(160%)";
    if (ruleRef.current) ruleRef.current.style.transform = "scaleX(0)";
  }, [finalGeometry]);

  const play = useCallback(() => {
    const geo = finalGeometry();
    const pill = pillRef.current;
    const clip = clipRef.current;
    const line = lineRef.current;
    if (!geo || !pill || !clip || !line) return;
    reset();
    const next: Animation[] = [];

    if (geo.mobile) {
      next.push(
        pill.animate(
          [
            { height: "2px", offset: 0, easing: EASE },
            { height: "2px", offset: 0.14 },
            { height: `${geo.finalH * 0.1}px`, offset: 0.3, easing: EASE },
            { height: `${geo.finalH * 0.55}px`, offset: 0.58, easing: EASE },
            { height: `${geo.finalH}px`, offset: 1 },
          ],
          { duration: 2100, fill: "both" },
        ),
      );
      next.push(
        clip.animate(
          [{ clipPath: "inset(0 50% 0 50%)" }, { clipPath: "inset(0 0 0 0)" }],
          { duration: 300, delay: 0, fill: "both", easing: EASE },
        ),
      );
    } else {
      const hair = Math.max(2, geo.fs * 0.012);
      next.push(
        pill.animate(
          [
            { width: `${hair}px`, offset: 0, easing: EASE },
            { width: `${hair}px`, offset: 0.14 },
            { width: `${geo.fs * 0.16}px`, offset: 0.3, easing: EASE },
            { width: `${geo.fs * 1.81}px`, offset: 0.58, easing: EASE },
            { width: `${geo.finalW}px`, offset: 1 },
          ],
          { duration: 2100, fill: "both" },
        ),
      );
      next.push(
        clip.animate([{ clipPath: "inset(50% 0 50% 0)" }, { clipPath: "inset(0 0 0 0)" }], {
          duration: 300,
          fill: "both",
          easing: EASE,
        }),
      );
    }

    next.push(line.animate([{ opacity: 1 }, { opacity: 1, offset: 0.14 }, { opacity: 0 }], { duration: 630, fill: "both" }));

    if (navRef.current) {
      next.push(
        navRef.current.animate(
          [
            { transform: "translateY(-140%)", offset: 0 },
            { transform: "translateY(-140%)", offset: 0.5 },
            { transform: "translateY(0)", offset: 0.86 },
            { transform: "translateY(0)", offset: 1 },
          ],
          { duration: 2100, fill: "both", easing: NAV_EASE },
        ),
      );
    }
    if (footRef.current) {
      next.push(
        footRef.current.animate(
          [
            { transform: "translateY(160%)", offset: 0 },
            { transform: "translateY(160%)", offset: 0.52 },
            { transform: "translateY(0)", offset: 0.9 },
            { transform: "translateY(0)", offset: 1 },
          ],
          { duration: 2100, fill: "both", easing: NAV_EASE },
        ),
      );
    }
    if (ruleRef.current) {
      next.push(
        ruleRef.current.animate([{ transform: "scaleX(0)" }, { transform: "scaleX(0)", offset: 0.4 }, { transform: "scaleX(1)" }], {
          duration: 2100,
          fill: "both",
          easing: EASE,
        }),
      );
    }

    animations.current = next;
    const first = next[0];
    first.finished.then(() => settle()).catch(() => undefined);
  }, [finalGeometry, reset, settle]);

  // Scroll drives the print (tt 2026-10-28): the wheel, not a timer, opens the slot. `再打一张`
  // keeps the manual 2.1s replay because that one is a deliberate "print it again" gesture.
  useEffect(() => {
    if (reduce) {
      settle();
      return;
    }
    let frame = 0;
    const running = animations.current;
    const pending = timers.current;

    const apply = () => {
      const geo = finalGeometry();
      const section = sectionRef.current;
      const pill = pillRef.current;
      const clip = clipRef.current;
      const line = lineRef.current;
      if (!geo || !section || !pill || !clip || !line) return;

      const travel = Math.max(240, section.clientHeight * 0.62);
      const p = Math.min(1, Math.max(0, window.scrollY / travel));
      // The slit cracks in the first eighth of the scroll, then the capsule opens with an ease-out.
      const grow = p <= 0.1 ? 0 : Math.min(1, (p - 0.1) / 0.78);
      const eased = 1 - Math.pow(1 - grow, 3);
      const hair = Math.max(2, geo.fs * 0.012);

      if (geo.mobile) {
        pill.style.width = "100%";
        pill.style.height = `${2 + (geo.finalH - 2) * eased}px`;
        clip.style.clipPath = `inset(0 ${50 - 50 * Math.min(1, grow * 2.2)}% 0 ${50 - 50 * Math.min(1, grow * 2.2)}%)`;
      } else {
        pill.style.width = `${hair + (geo.finalW - hair) * eased}px`;
        pill.style.height = `${geo.finalH}px`;
        clip.style.clipPath = `inset(${50 - 50 * Math.min(1, grow * 2.2)}% 0 ${50 - 50 * Math.min(1, grow * 2.2)}% 0)`;
      }
      line.style.opacity = grow > 0.02 ? String(Math.max(0, 1 - grow * 6)) : "1";
      if (ruleRef.current) ruleRef.current.style.transform = `scaleX(${Math.min(1, grow * 1.4)})`;
    };

    const onScroll = () => {
      if (frame) return;
      frame = window.requestAnimationFrame(() => {
        frame = 0;
        apply();
      });
    };

    apply();
    window.requestAnimationFrame(apply);
    window.addEventListener("load", onScroll);
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onScroll);
    return () => {
      window.removeEventListener("load", onScroll);
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onScroll);
      if (frame) window.cancelAnimationFrame(frame);
      running.forEach((a) => a.cancel());
      pending.forEach((t) => window.clearTimeout(t));
    };
  }, [reduce, finalGeometry, settle]);

  const reprint = () => {
    if (reduce) return;
    const list = animations.current;
    if (list.length === 0) {
      play();
      return;
    }
    list.forEach((a) => {
      a.playbackRate = -1;
      a.play();
    });
    list[0].finished
      .then(() => {
        list.forEach((a) => {
          a.playbackRate = 1;
          a.play();
        });
        return list[0].finished;
      })
      .then(() => settle())
      .catch(() => undefined);
  };

  return (
    <section ref={sectionRef} id="top" className="lp-hero lp-grain">
      <div className="lp-hero-sky" aria-hidden />
      <svg className="lp-hero-art" viewBox="0 0 1440 900" preserveAspectRatio="none" aria-hidden>
        <g fill="none" stroke="rgba(27,23,18,0.16)" strokeWidth="1">
          <path d="M-40 640C220 590 360 700 640 662S1080 560 1480 620" />
          <path d="M-40 700C260 650 380 760 660 722S1100 620 1480 676" />
          <path d="M-40 760C300 712 420 820 700 782S1140 680 1480 736" />
        </g>
        <g fill="none" stroke="rgba(255,138,0,0.28)" strokeWidth="1.2" strokeLinecap="round">
          <path d="M120 250c40-26 92-26 132 0" />
          <path d="M1210 300c46-30 104-30 150 0" />
          <path d="M1180 720c-30 34-30 82 0 116" />
        </g>
        <g fill="rgba(27,23,18,0.18)">
          <circle cx="196" cy="252" r="2.4" />
          <circle cx="1268" cy="300" r="2.4" />
          <circle cx="1184" cy="740" r="2.4" />
        </g>
      </svg>

      <TopNav navRef={navRef} />

      <div className="lp-hero-badge-wrap">
        <span className="lp-badge">
          <span aria-hidden className="lp-badge-dot" />
          {s("hero.badge")}
        </span>
      </div>

      <div ref={rowRef} className="lp-hero-row">
        <h1 className="sr-only">
          {s("hero.title.a")} {s("hero.title.b")}
        </h1>
        <span className="lp-word" aria-hidden>
          {s("hero.title.a")}
        </span>
        <div
          ref={pillRef}
          className="lp-pill"
          role="img"
          aria-label={s("hero.slot")}
        >
          <div ref={clipRef} className="lp-pill-clip">
            <img src="/img/landing-printer-slot-macro-v2.webp" alt="" aria-hidden loading="eager" />
            <div className="lp-pill-shade" aria-hidden />
            <div className="lp-pill-slot" aria-hidden />
            <div className="lp-hero-paper" aria-hidden>
              <p className="lp-hero-paper-brand">{s("brand")}</p>
              <p className="lp-hero-paper-date">{landingDate(2026, 10, 27)}</p>
              <span className="rule" />
              <b>
                <span>{s("hero.paper.row1")}</span>
                <span>11.50</span>
              </b>
              <b>
                <span>{s("hero.paper.row2")}</span>
                <span>4.00</span>
              </b>
              <span className="rule" />
              <b className="total">
                <span>{s("mini.total")}</span>
                <span>15.50</span>
              </b>
            </div>
          </div>
          <span ref={lineRef} className="lp-pill-line" aria-hidden />
        </div>
        <span className="lp-word lp-word--b" aria-hidden>
          {s("hero.title.b")}
        </span>
      </div>

      <aside className="lp-hero-float lp-hero-float--left" aria-hidden>
        <div className="lp-float-card">
          <p className="lp-float-kicker">{landingDate(2026, 10, 27)}</p>
          <p className="lp-float-row"><span>{s("hero.prop1")}</span><span>32.00</span></p>
          <p className="lp-float-row"><span>{s("hero.prop2")}</span><span>5.00</span></p>
          <p className="lp-float-row"><span>{s("hero.prop3")}</span><span>18.00</span></p>
          <span className="lp-float-rule" />
          <p className="lp-float-row lp-float-total"><span>{s("mini.total")}</span><span>55.00</span></p>
          <span className="lp-float-stamp">{s("mini.recorded")}</span>
        </div>
      </aside>
      <aside className="lp-hero-float lp-hero-float--right" aria-hidden>
        <img src="/img/landing-hand-receipt.webp" alt="" loading="eager" />
        <span className="lp-float-tag">365 / 365</span>
      </aside>

      <div ref={footRef} className="lp-hero-foot">
        <div>
          <div ref={ruleRef} className="lp-hero-rule" />
          <p className="lp-dropcap mt-4 max-w-[54ch] text-[16px] leading-[1.7] text-[var(--lp-ink)] sm:text-[16px]">{s("hero.lede")}</p>
          <div className="mt-5 flex flex-wrap items-center gap-3">
            <a
              href="#/app"
              className="inline-flex h-12 items-center rounded-full bg-[var(--lp-ink)] px-6 text-[16px] font-medium text-[var(--lp-paper)] transition-transform duration-200 hover:-translate-y-0.5 active:scale-[0.98]"
            >
              {s("hero.cta")}
            </a>
            <button
              type="button"
              onClick={reprint}
              className="inline-flex h-12 items-center gap-2 rounded-full border border-[var(--lp-line)] px-5 text-[14px] font-medium text-[var(--lp-ink)] transition-colors hover:bg-[rgba(27,23,18,0.06)]"
            >
              <span aria-hidden>↻</span>
              {s("hero.reprint")}
            </button>
            <span className="text-[13px] text-[var(--lp-ink-3)]">{s("hero.note")}</span>
          </div>
          <p className="lp-hand-note mt-4 hidden sm:block" aria-hidden>
            <svg viewBox="0 0 120 44" className="lp-hand-arrow" aria-hidden>
              <path d="M4 34c22-26 52-30 92-18" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
              <path d="M88 10l12 6-9 10" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
            {s("hero.noteHand")}
          </p>
        </div>
        <p className="hidden text-right text-[13px] text-[var(--lp-ink-3)] sm:block">{s("hero.scroll")} ↓</p>
      </div>
    </section>
  );
}
