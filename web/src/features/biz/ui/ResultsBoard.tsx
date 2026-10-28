import { computeResults, type Experiment } from "@/features/merchant/logic/experiment";
import { gate } from "@/logic/biz/sample";
import { useBizStrings } from "../strings";
import { Button, Card, Chip } from "./primitives";

/** Step 3: what the game stances say, with the interval — and nothing at all under 20 answers. */
export function ResultsBoard({
  experiment,
  onEnd,
  onNext,
}: {
  experiment: Experiment | null;
  onEnd: () => void;
  onNext: () => void;
}) {
  const t = useBizStrings();
  if (!experiment || experiment.votes.length === 0) {
    return (
      <Card className="col-span-12" title={t("results.sample")}>
        <p className="py-10 text-center text-[12px] leading-5 text-d-ink-2">
          {t("results.empty")} {t("results.emptyHint")}
        </p>
      </Card>
    );
  }

  const results = computeResults(experiment);
  const gated = gate(results.sampleSize, results);
  const visible = gated.visible && results.sampleSize > 0;

  return (
    <div className="grid grid-cols-12 gap-5">
      <Card
        className="col-span-12 lg:col-span-8"
        title={experiment.title}
        action={
          <span className="flex items-center gap-2">
            <Chip>{t("insights.gameHint")}</Chip>
            <Chip tone="accent">
              {experiment.status === "running" ? t("status.running") : t("status.ended")}
            </Chip>
          </span>
        }
      >
        <div className="space-y-4">
          {experiment.options.map((option) => {
            const result = results.options.find((r) => r.optionId === option.id);
            const share = visible ? (result?.share ?? 0) : null;
            return (
              <div key={option.id} title={visible ? undefined : t("results.gated")}>
                <div className="flex items-baseline justify-between gap-3">
                  <p className="text-[13px] font-medium text-d-ink">
                    {option.emoji} {option.label}
                  </p>
                  <p className="text-[13px] tabular-nums text-d-ink-2">
                    {visible ? `${Math.round((share ?? 0) * 100)}% · ${result?.count ?? 0} ${t("unit.people")}` : "—"}
                  </p>
                </div>
                <div className="mt-1.5 h-2 overflow-hidden rounded-full bg-d-fill">
                  {visible && (
                    <div className="h-full rounded-full bg-d-accent" style={{ width: `${Math.max(2, (share ?? 0) * 100)}%` }} />
                  )}
                </div>
                <p className="mt-1 text-[11px] tabular-nums text-d-ink-3">
                  {visible && result
                    ? `${t("results.interval")} ${Math.round(result.ci[0] * 100)}–${Math.round(result.ci[1] * 100)}%`
                    : t("results.gated")}
                </p>
              </div>
            );
          })}
        </div>

        <p className="mt-4 border-t border-d-hairline pt-3 text-[12px] leading-5 text-d-ink-2">
          {!visible
            ? t("results.gated")
            : results.sampleSize < 30
              ? t("results.thin")
              : results.leader === null
                ? t("results.split")
                : t("results.stable")}
        </p>
      </Card>

      <div className="col-span-12 space-y-5 lg:col-span-4">
        <Card title={t("results.sample")}>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <p className="text-[22px] font-semibold leading-7 tabular-nums">{visible ? results.sampleSize : "—"}</p>
              <p className="text-[11px] text-d-ink-2">{t("unit.people")}</p>
            </div>
            <div>
              <p className="text-[22px] font-semibold leading-7 tabular-nums">{visible ? results.playerCount : "—"}</p>
              <p className="text-[11px] text-d-ink-2">{t("results.players")}</p>
            </div>
          </div>
        </Card>

        <div className="flex flex-wrap gap-2">
          <Button tone="solid" onClick={onNext}>
            {t("results.next")}
          </Button>
          {experiment.status === "running" && (
            <Button tone="fill" onClick={onEnd}>
              {t("results.finish")}
            </Button>
          )}
        </div>
        {experiment.status === "ended" && <p className="text-[11px] leading-4 text-d-ink-3">{t("results.ended")}</p>}
      </div>
    </div>
  );
}
