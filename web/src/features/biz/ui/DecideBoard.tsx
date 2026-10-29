import { Check } from "lucide-react";
import { computeResults, type Experiment } from "@/features/merchant/logic/experiment";
import type { Insights } from "@/features/merchant/logic/aggregator";
import { cn } from "@/lib/utils";
import { WEEKDAY_KEYS, useBizStrings } from "../strings";
import { fill } from "../text";
import { Button, Card, Chip } from "./primitives";

const SLOT_KEYS = ["slot.0", "slot.1", "slot.2", "slot.3", "slot.4", "slot.5"] as const;

/**
 * Step 4: three ways forward, each with its own cost. The owner picks; the app never picks.
 * Every card avoids promising revenue — it only says what would change and what it costs.
 */
export function DecideBoard({
  experiment,
  insights,
  chosenId,
  onChoose,
  onRestart,
}: {
  experiment: Experiment | null;
  insights: Insights;
  chosenId: string | null;
  onChoose: (id: string) => void;
  onRestart: () => void;
}) {
  const t = useBizStrings();

  if (!experiment || experiment.status === "draft") {
    return (
      <Card className="col-span-12" title={t("decide.pick")}>
        <p className="py-10 text-center text-[12px] leading-5 text-d-ink-2">
          {t("decide.empty")} {t("decide.emptyHint")}
        </p>
      </Card>
    );
  }

  const results = computeResults(experiment);
  const lead = results.leader ? experiment.options.find((o) => o.id === results.leader) : null;
  const peak = insights.peak
    ? fill(t("insights.peakName"), t(WEEKDAY_KEYS[insights.peak.weekday]), t(SLOT_KEYS[insights.peak.slot]))
    : t("insights.peak");
  const sampleHint = results.sampleSize >= 30 && results.isClear ? t("results.stable") : t("results.thin");

  const options = [
    {
      id: "follow",
      title: fill(t("decide.opt1.title"), lead ? lead.label : t("decide.both")),
      body: fill(t("decide.opt1.body"), lead ? lead.label : t("decide.both"), sampleHint),
      pros: t("decide.opt1.pros"),
      cons: t("decide.opt1.cons"),
      first: t("decide.opt1.first"),
    },
    {
      id: "pilot",
      title: fill(t("decide.opt2.title"), peak),
      body: t("decide.opt2.body"),
      pros: t("decide.opt2.pros"),
      cons: t("decide.opt2.cons"),
      first: t("decide.opt2.first"),
    },
    {
      id: "retest",
      title: t("decide.opt3.title"),
      body: t("decide.opt3.body"),
      pros: t("decide.opt3.pros"),
      cons: t("decide.opt3.cons"),
      first: t("decide.opt3.first"),
    },
  ];

  return (
    <div className="grid grid-cols-12 gap-5">
      <div className="col-span-12 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-[13px] font-semibold text-d-ink-2">{t("decide.pick")}</h2>
        <p className="max-w-[560px] text-[11px] leading-4 text-d-ink-3">{t("decide.note")}</p>
      </div>

      {options.map((option, index) => {
        const chosen = chosenId === option.id;
        return (
          <Card
            key={option.id}
            className={cn("col-span-12 md:col-span-6 xl:col-span-4", chosen && "ring-1 ring-d-accent/40")}
            index={index}
            title={chosen ? <span className="text-d-accent-ink">{t("decide.chosen")}</span> : fill(t("decide.option"), `${index + 1}`)}
          >
            <p className="text-[14px] font-semibold leading-5 text-d-ink">{option.title}</p>
            <p className="mt-2 text-[12px] leading-5 text-d-ink-2">{option.body}</p>
            <dl className="mt-3 space-y-1.5 border-t border-d-hairline pt-3 text-[12px] leading-5">
              <div className="flex gap-2">
                <dt className="w-10 shrink-0 text-d-ink-3">{t("decide.pros")}</dt>
                <dd className="text-d-ink-2">{option.pros}</dd>
              </div>
              <div className="flex gap-2">
                <dt className="w-10 shrink-0 text-d-ink-3">{t("decide.cons")}</dt>
                <dd className="text-d-ink-2">{option.cons}</dd>
              </div>
              <div className="flex gap-2">
                <dt className="w-10 shrink-0 text-d-ink-3">{t("decide.first")}</dt>
                <dd className="text-d-ink-2">{option.first}</dd>
              </div>
            </dl>
            <div className="mt-4">
              {chosen ? (
                <Chip tone="accent">
                  <Check className="mr-1 size-3" strokeWidth={2.5} aria-hidden />
                  {t("decide.chosen")}
                </Chip>
              ) : (
                <Button tone="fill" onClick={() => onChoose(option.id)}>
                  {t("decide.choose")}
                </Button>
              )}
            </div>
          </Card>
        );
      })}

      <div className="col-span-12 flex flex-wrap items-center gap-3 rounded-xl bg-d-surface px-5 py-4">
        <p className="flex-1 text-[12px] leading-5 text-d-ink-2">{t("decide.reviewNote")}</p>
        <Button tone="quiet" onClick={onRestart}>
          {t("decide.restart")}
        </Button>
      </div>
    </div>
  );
}
