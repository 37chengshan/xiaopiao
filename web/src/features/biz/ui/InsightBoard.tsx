import { gate } from "@/logic/biz/sample";
import type { HeatCell, Insights } from "@/features/merchant/logic/aggregator";
import { WEEKDAY_KEYS, useBizStrings, type BizStringKey } from "../strings";
import { fill } from "../text";
import { Button, Card, Chip, ShareBar } from "./primitives";

const SLOT_KEYS = ["slot.0", "slot.1", "slot.2", "slot.3", "slot.4", "slot.5"] as const;

/** Share cells carry a stable id; the label is copy, so it comes from the dictionary, not the data. */
const BAND_KEYS: Record<string, BizStringKey> = {
  lt10: "insights.band.lt10",
  "10to15": "insights.band.10to15",
  "15to20": "insights.band.15to20",
  gt20: "insights.band.gt20",
};
const TOPPING_KEYS: Record<string, BizStringKey> = {
  taro: "topping.taro",
  pearl: "topping.pearl",
  coconut: "topping.coconut",
  pudding: "topping.pudding",
  herbal: "topping.herbal",
};
const CATEGORY_KEYS: Record<string, BizStringKey> = {
  MEAL: "category.MEAL",
  DRINK: "category.DRINK",
  SNACK: "category.SNACK",
  GROCERY: "category.GROCERY",
  TRANSPORT: "category.TRANSPORT",
  SHOPPING: "category.SHOPPING",
  FUN: "category.FUN",
  SUBSCRIPTION: "category.SUBSCRIPTION",
  HOUSING: "category.HOUSING",
  HEALTH: "category.HEALTH",
  SOCIAL: "category.SOCIAL",
  LEARNING: "category.LEARNING",
  OTHER: "category.OTHER",
};

function translated(
  t: (key: BizStringKey) => string,
  keys: Record<string, BizStringKey>,
  key: string,
  fallback: string,
): string {
  const dictionaryKey = keys[key];
  return dictionaryKey ? t(dictionaryKey) : fallback;
}

/** Step 1: what the anonymous crowd looks like. Anything under 20 people stays hidden. */
export function InsightBoard({
  insights,
  onNext,
  game,
}: {
  insights: Insights;
  onNext: () => void;
  game: { stances: number; lead: string | null; sample: number };
}) {
  const t = useBizStrings();
  const peak = insights.peak;
  const gatedStances = gate(game.sample, game.stances);

  return (
    <div className="grid grid-cols-12 gap-5">
        <Card className="col-span-12 xl:col-span-8" title={t("insights.heat")} index={0}>
          <Heatmap insights={insights} />
        </Card>

        <div className="col-span-12 space-y-5 xl:col-span-4">
          <Card title={t("insights.crowd")} index={1}>
            <div className="grid grid-cols-2 gap-4">
              <Stat value={`${insights.crowdSize}`} unit={t("unit.people")} label={t("insights.crowd")} />
              <Stat value={`${insights.drinkBuyers}`} unit={t("unit.people")} label={t("insights.buyers")} />
              <Stat
                value={insights.repeatRate === null ? "—" : `${Math.round(insights.repeatRate * 100)}%`}
                label={t("insights.repeat")}
                muted={insights.repeatRate === null}
                title={insights.repeatRate === null ? t("insights.suppressed") : undefined}
              />
              <Stat
                value={peak ? fill(t("insights.peakName"), t(WEEKDAY_KEYS[peak.weekday]), t(SLOT_KEYS[peak.slot])) : "—"}
                label={t("insights.peak")}
                muted={!peak}
              />
            </div>
          </Card>

          <Card title={t("insights.game")} action={<Chip>{t("insights.gameHint")}</Chip>}>
            <div className="space-y-2">
              <div className="flex items-baseline justify-between gap-3">
                <span className="text-[12px] text-d-ink-2">{t("results.sample")}</span>
                <span className="text-[15px] font-semibold tabular-nums">
                  {gatedStances.value === null ? "—" : fill(t("results.plays"), `${gatedStances.value}`)}
                </span>
              </div>
              <p className="text-[12px] leading-5 text-d-ink-2">
                {gatedStances.value === null ? t("results.gated") : game.lead ? game.lead : t("decide.both")}
              </p>
              {gatedStances.value !== null && (
                <p className="text-[11px] leading-4 text-d-ink-3">{t("results.sourceNote")}</p>
              )}
            </div>
          </Card>

          <Button tone="solid" className="w-full" onClick={onNext}>
            {t("insights.next")}
          </Button>
        </div>

        <Card className="col-span-12 md:col-span-6" title={t("insights.price")} index={2}>
          <div className="space-y-0.5">
            {insights.priceBands.map((cell) => (
              <ShareBar
                key={cell.key}
                label={translated(t, BAND_KEYS, cell.key, cell.label)}
                share={cell.share}
                suppressed={cell.suppressed}
                suppressedLabel={t("insights.suppressed")}
              />
            ))}
          </div>
        </Card>

        <Card className="col-span-12 md:col-span-6" title={t("insights.toppings")} index={3}>
          <div className="space-y-0.5">
            {insights.toppings.map((cell) => (
              <ShareBar
                key={cell.key}
                label={translated(t, TOPPING_KEYS, cell.key, cell.label)}
                emoji={cell.emoji}
                share={cell.share}
                suppressed={cell.suppressed}
                suppressedLabel={t("insights.suppressed")}
              />
            ))}
          </div>
        </Card>

        <Card className="col-span-12" title={t("insights.also")} index={4}>
          <div className="space-y-0.5">
            {insights.categoryShare.slice(0, 6).map((cell) => (
              <ShareBar
                key={cell.key}
                label={translated(t, CATEGORY_KEYS, cell.key, cell.label)}
                emoji={cell.emoji}
                share={cell.share}
                note={cell.people === null ? undefined : `${cell.people} ${t("unit.people")}`}
                suppressed={cell.suppressed}
                suppressedLabel={t("insights.suppressed")}
              />
            ))}
          </div>
        </Card>
    </div>
  );
}

function Heatmap({ insights }: { insights: Insights }) {
  const t = useBizStrings();
  const visible = insights.heatmap.flat().filter((c) => !c.suppressed).map((c) => c.people ?? 0);
  const lo = Math.min(...visible, 0);
  const hi = Math.max(...visible, 1);
  const tint = (cell: HeatCell) => {
    if (cell.suppressed || cell.people === null) return undefined;
    const step = hi === lo ? 0.5 : (cell.people - lo) / (hi - lo);
    return `color-mix(in oklch, var(--d-accent) ${Math.round(10 + step * 45)}%, transparent)`;
  };

  return (
    <div role="group" aria-label={t("insights.heat")}>
      <div className="grid grid-cols-[auto_repeat(6,1fr)] gap-1 pb-1.5 text-center text-[11px] font-medium text-d-ink-3">
        <span aria-hidden />
        {SLOT_KEYS.map((key) => (
          <span key={key}>{t(key)}</span>
        ))}
      </div>
      {insights.heatmap.map((row, weekday) => (
        <div key={weekday} className="mb-1 grid grid-cols-[auto_repeat(6,1fr)] gap-1">
          <span className="self-center pr-1 text-[11px] font-medium text-d-ink-2">{t(WEEKDAY_KEYS[weekday])}</span>
          {row.map((cell) => (
            <div
              key={cell.slot}
              title={cell.suppressed ? t("insights.suppressed") : `${cell.people} ${t("unit.people")}`}
              className="grid h-8 place-items-center rounded-[6px] text-[11px] tabular-nums"
              style={
                cell.suppressed
                  ? {
                      backgroundImage:
                        "repeating-linear-gradient(135deg, var(--d-fill) 0 3px, transparent 3px 6px)",
                      color: "var(--d-ink-3)",
                      boxShadow: "inset 0 0 0 1px var(--d-hairline)",
                    }
                  : { backgroundColor: tint(cell), color: "var(--d-ink)" }
              }
            >
              {cell.suppressed ? "—" : cell.people}
            </div>
          ))}
        </div>
      ))}
      <div className="mt-3 flex items-center gap-3 border-t border-d-hairline pt-3 text-[11px] text-d-ink-2">
        <span
          className="size-3 rounded-[3px]"
          style={{
            backgroundImage: "repeating-linear-gradient(135deg, var(--d-fill) 0 3px, transparent 3px 6px)",
            boxShadow: "inset 0 0 0 1px var(--d-hairline)",
          }}
          aria-hidden
        />
        {fill(t("insights.heatLegend"), `${insights.k}`)}
        <span className="ml-auto text-d-ink-3">
          {t("insights.peak")} {insights.peak ? fill(t("insights.peakName"), t(WEEKDAY_KEYS[insights.peak.weekday]), t(SLOT_KEYS[insights.peak.slot])) : "—"}
        </span>
      </div>
    </div>
  );
}

function Stat({
  value,
  unit,
  label,
  muted,
  title,
}: {
  value: string;
  unit?: string;
  label: string;
  muted?: boolean;
  title?: string;
}) {
  return (
    <div title={title} className="min-w-0">
      <p className={`text-[18px] font-semibold leading-6 tracking-[-0.01em] tabular-nums ${muted ? "text-d-ink-3" : "text-d-ink"}`}>
        {value}
        {unit && <span className="ml-1 text-[11px] font-normal text-d-ink-2">{unit}</span>}
      </p>
      <p className="mt-0.5 truncate text-[11px] text-d-ink-2">{label}</p>
    </div>
  );
}
