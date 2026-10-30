import { motion, useScroll, useTransform } from "framer-motion";
import { useRef } from "react";
import { s } from "../strings";

/**
 * Privacy: the one dark section. Its background interpolates from paper to ink as it arrives, so the page
 * physically turns over here; the colour of everything inside rides the same progress value.
 */
const ITEMS = [
  { n: "01", title: "privacy.1.title", line: "privacy.1.line" },
  { n: "02", title: "privacy.2.title", line: "privacy.2.line" },
  { n: "03", title: "privacy.3.title", line: "privacy.3.line" },
  { n: "04", title: "privacy.4.title", line: "privacy.4.line" },
] as const;

export function Privacy() {
  const ref = useRef<HTMLElement>(null);
  const { scrollYProgress } = useScroll({ target: ref, offset: ["start 0.62", "start 0.1"] });
  const background = useTransform(scrollYProgress, [0, 1], ["#f7f6f3", "#111110"]);
  const color = useTransform(scrollYProgress, [0, 1], ["#141413", "#f6f4ef"]);
  const hairline = useTransform(scrollYProgress, [0, 1], ["rgba(20,20,19,0.16)", "rgba(246,244,239,0.22)"]);

  return (
    <motion.section id="privacy" className="lx-privacy" ref={ref} style={{ backgroundColor: background, color }}>
      <div className="lx-privacy__inner">
        <header className="lx-privacy__head">
          <p className="lx-eyebrow">07</p>
          <h2 className="lx-h2">{s("privacy.heading")}</h2>
          <p className="lx-lead">{s("privacy.lead")}</p>
        </header>
        <ol className="lx-privacy__list">
          {ITEMS.map((item, i) => (
            <motion.li
              key={item.n}
              className="lx-privacy__row"
              style={{ borderColor: hairline }}
              initial={{ opacity: 0, y: 28 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: "-12% 0px" }}
              transition={{ duration: 0.6, delay: i * 0.06, ease: [0.22, 1, 0.36, 1] }}
            >
              <span className="lx-privacy__n">{item.n}</span>
              <h3 className="lx-privacy__title">{s(item.title)}</h3>
              <p className="lx-privacy__line">{s(item.line)}</p>
            </motion.li>
          ))}
        </ol>
      </div>
    </motion.section>
  );
}
