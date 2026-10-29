import {
  motion,
  useMotionValue,
  useReducedMotion,
  useScroll,
  useTransform,
  type MotionValue,
} from "framer-motion";
import { Upload } from "lucide-react";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { yuan, type Cents } from "@/domain/types";
import { EASE_OUT } from "@/lib/motion";
import { useLocale } from "@/lib/strings";
import { cn } from "@/lib/utils";
import { tornBottomClip } from "../lib/paper";
import { landingStrings } from "../strings";

const GAP = 40;
const PANEL_CLASS =
  "flex h-[440px] w-[min(86vw,720px)] shrink-0 flex-col rounded-2xl border border-d-hairline bg-d-surface p-6 lg:w-[min(72vw,760px)]";

const REVENUE_ROWS: { day: string; cents: Cents; orders: number }[] = [
  { day: "10-26", cents: 428000, orders: 122 },
  { day: "10-27", cents: 512000, orders: 148 },
  { day: "10-28", cents: 396000, orders: 116 },
];

/**
 * L4 — pinned horizontal canvas. Vertical scroll translates the track by exactly
 * `trackWidth - viewportWidth`; a panel scales to 1 as it crosses the middle of the viewport.
 * Reduced motion drops the pin and stacks the three panels.
 */
export function Merchant() {
  useLocale();
  const s = landingStrings;
  const reduceMotion = useReducedMotion();
  const ref = useRef<HTMLElement>(null);
  const trackRef = useRef<HTMLDivElement>(null);
  const [metrics, setMetrics] = useState({ range: 0, step: 1 });

  useEffect(() => {
    const track = trackRef.current;
    const viewport = track?.parentElement;
    if (!track || !viewport) return;
    const measure = () => {
      const first = track.firstElementChild as HTMLElement | null;
      setMetrics({
        range: Math.max(0, track.scrollWidth - viewport.clientWidth),
        step: (first?.offsetWidth ?? 0) + GAP,
      });
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(viewport);
    return () => observer.disconnect();
  }, [reduceMotion]);

  const { scrollYProgress } = useScroll({ target: ref, offset: ["start start", "end end"] });
  const frozen = useMotionValue(0);
  const p = reduceMotion ? frozen : scrollYProgress;
  const x = useTransform(p, [0.04, 0.96], [0, -metrics.range]);

  const centers = [0, 1, 2].map((index) =>
    metrics.range > 0
      ? Math.min(0.96, Math.max(0.04, (index * metrics.step) / metrics.range))
      : 0.5,
  );

  return (
    <section
      id="merchant"
      ref={ref}
      className={cn(
        "relative scroll-mt-[72px] bg-d-canvas text-d-ink",
        !reduceMotion && "h-[220vh]",
      )}
    >
      <div
        className={cn(
          !reduceMotion && "sticky top-0 flex h-svh flex-col justify-center overflow-hidden",
        )}
      >
        <div className="mx-auto w-full max-w-[1200px] px-5 lg:px-10">
          <motion.p
            initial={{ opacity: 0 }}
            whileInView={{ opacity: 1 }}
            viewport={{ once: true, amount: 0.5 }}
            transition={{ duration: 0.2 }}
            className="text-[12px] font-semibold tracking-[0.16em] text-d-ink-3 uppercase"
          >
            {s("m.eyebrow")}
          </motion.p>
          <motion.h2
            initial={{ opacity: 0, y: reduceMotion ? 0 : 16 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.5 }}
            transition={{ duration: reduceMotion ? 0.2 : 0.6, ease: EASE_OUT }}
            className="mt-3 max-w-[720px] text-[clamp(1.6rem,2.6vw,2.4rem)] leading-[1.15] font-semibold tracking-[-0.02em] text-balance"
          >
            {s("m.title")}
          </motion.h2>
          <p className="mt-3 max-w-[520px] text-[15px] leading-relaxed text-d-ink-2">
            {s("m.sub")}
          </p>
        </div>

        <div
          className={cn(
            "mt-8",
            reduceMotion
              ? "mx-auto flex max-w-[1200px] flex-col gap-6 px-5 pb-24 lg:px-10"
              : "overflow-hidden",
          )}
        >
          <motion.div
            ref={trackRef}
            style={reduceMotion ? undefined : { x }}
            className={cn("flex gap-10", reduceMotion ? "flex-col" : "w-max pl-5 lg:pl-10")}
          >
            <Panel progress={p} center={centers[0]} still={Boolean(reduceMotion)}>
              <PanelHeading title={s("m.p1.title")} body={s("m.p1.body")} />
              <div className="mt-4 flex min-h-0 flex-1 flex-col gap-3">
                <div className="flex items-center gap-2 rounded-xl border border-dashed border-d-ink-3/60 px-4 py-3 text-[13px] text-d-ink-2">
                  <Upload size={15} strokeWidth={1.75} />
                  {s("m.panel1.hint")}
                  <span className="ml-auto font-mono text-[12px] text-d-ink-3">CSV</span>
                </div>
                <div className="overflow-hidden rounded-xl border border-d-hairline">
                  <div className="grid grid-cols-3 bg-d-fill px-4 py-2 text-[12px] font-medium text-d-ink-2">
                    <span>{s("m.panel1.col1")}</span>
                    <span className="text-right">{s("m.panel1.col2")}</span>
                    <span className="text-right">{s("m.panel1.col3")}</span>
                  </div>
                  {REVENUE_ROWS.map((row) => (
                    <div
                      key={row.day}
                      className="grid grid-cols-3 border-t border-d-hairline px-4 py-2.5 text-[13px] tabular-nums"
                    >
                      <span className="font-mono text-[12px] text-d-ink-2">{row.day}</span>
                      <span className="text-right">¥{yuan(row.cents)}</span>
                      <span className="text-right text-d-ink-2">{row.orders}</span>
                    </div>
                  ))}
                </div>
                <div className="flex gap-2 text-[12px]">
                  <Chip label={`${s("m.panel1.add")} 12`} tone="neutral" />
                  <Chip label={`${s("m.panel1.over")} 3`} tone="neutral" />
                  <Chip label={`${s("m.panel1.skip")} 1`} tone="muted" />
                </div>
              </div>
            </Panel>

            <Panel progress={p} center={centers[1]} still={Boolean(reduceMotion)}>
              <PanelHeading title={s("m.p2.title")} body={s("m.p2.body")} />
              <div className="mt-4 flex min-h-0 flex-1 items-stretch justify-center">
                <div
                  className="flex w-[300px] flex-col bg-d-paper px-6 pt-6 pb-4 text-[13px] text-paper-ink tabular-nums"
                  style={{ clipPath: tornBottomClip(24, 8) }}
                >
                  <div className="flex items-baseline justify-between text-[12px]">
                    <span className="font-semibold">{s("m.panel2.title")}</span>
                    <span className="text-paper-ink/55">10-27</span>
                  </div>
                  <div className="mt-4 flex flex-col gap-2 text-[13px]">
                    <PaperRow label={s("m.panel2.revenue")} value={`¥${yuan(512000)}`} />
                    <PaperRow label={s("m.panel2.orders")} value="148" />
                    <PaperRow label={s("m.panel2.basket")} value={`¥${yuan(3459)}`} />
                  </div>
                  <p className="mt-4 border-t border-dashed border-paper-ink/25 pt-3 text-[12px] leading-relaxed text-paper-ink/70">
                    {s("m.panel2.note")}
                  </p>
                  <span className="mt-auto pt-6 font-mono text-[11px] text-paper-ink/45">
                    {s("m.panel2.footer")}
                  </span>
                </div>
              </div>
            </Panel>

            <Panel progress={p} center={centers[2]} still={Boolean(reduceMotion)}>
              <PanelHeading title={s("m.p3.title")} body={s("m.p3.body")} />
              <div className="mt-4 flex min-h-0 flex-1 flex-col gap-3">
                <div className="flex flex-col gap-2 rounded-xl border border-d-hairline p-4">
                  <span className="text-[12px] text-d-ink-2">{s("m.panel3.group")}</span>
                  <Bar label={s("m.panel3.returning")} ratio={0.62} />
                  <Bar label={s("m.panel3.new")} ratio={0.38} />
                </div>
                <div
                  title={s("m.panel3.locked")}
                  className="flex items-center justify-between rounded-xl border border-dashed border-d-hairline bg-d-fill/40 px-4 py-3 text-[12px] text-d-ink-3"
                >
                  <span>{s("m.panel3.locked")}</span>
                  <span className="font-mono">—</span>
                </div>
                <span className="mt-auto text-[12px] text-d-ink-3">{s("m.panel3.only")}</span>
              </div>
            </Panel>
          </motion.div>
        </div>
      </div>
    </section>
  );
}

function Panel({
  progress,
  center,
  still,
  children,
}: {
  progress: MotionValue<number>;
  center: number;
  still: boolean;
  children: ReactNode;
}) {
  const scale = useTransform(progress, [center - 0.16, center, center + 0.16], [0.94, 1, 0.94]);
  return (
    <motion.div
      style={still ? { scale: 1 } : { scale }}
      className={cn(PANEL_CLASS, still && "h-auto w-full lg:w-full")}
    >
      {children}
    </motion.div>
  );
}

function PanelHeading({ title, body }: { title: string; body: string }) {
  return (
    <div>
      <h3 className="text-[17px] font-semibold tracking-[-0.01em]">{title}</h3>
      <p className="mt-1.5 text-[13px] leading-relaxed text-d-ink-2">{body}</p>
    </div>
  );
}

function PaperRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline justify-between">
      <span>{label}</span>
      <span className="font-semibold">{value}</span>
    </div>
  );
}

function Bar({ label, ratio }: { label: string; ratio: number }) {
  return (
    <div className="flex items-center gap-3">
      <span className="w-[64px] shrink-0 text-[12px] text-d-ink-2">{label}</span>
      <span className="h-[6px] flex-1 overflow-hidden rounded-full bg-d-fill">
        <span
          className="block h-full rounded-full bg-d-accent"
          style={{ width: `${Math.round(ratio * 100)}%` }}
        />
      </span>
      <span className="w-[34px] shrink-0 text-right text-[12px] tabular-nums text-d-ink-2">
        {Math.round(ratio * 100)}%
      </span>
    </div>
  );
}

function Chip({ label, tone }: { label: string; tone: "neutral" | "muted" }) {
  return (
    <span
      className={cn(
        "rounded-full px-2.5 py-1",
        tone === "neutral" ? "bg-d-fill text-d-ink" : "bg-d-fill/50 text-d-ink-3",
      )}
    >
      {label}
    </span>
  );
}
