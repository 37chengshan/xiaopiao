import { Check } from "lucide-react";
import { TEMPLATES, type Experiment } from "@/features/merchant/logic/experiment";
import { cn } from "@/lib/utils";
import { useBizStrings } from "../strings";
import { templateCopy } from "../templates";
import { Button, Card, Chip } from "./primitives";

/** Step 2: pick one question, see the game card, then start a round of game stances. */
export function ExperimentBoard({
  templateId,
  experiment,
  onSelect,
  onPublish,
  onNext,
}: {
  templateId: string | null;
  experiment: Experiment | null;
  onSelect: (id: string) => void;
  onPublish: () => void;
  onNext: () => void;
}) {
  const t = useBizStrings();
  const template = TEMPLATES.find((item) => item.id === templateId) ?? null;
  const copy = template ? templateCopy(t, template) : null;
  const status = experiment?.status ?? "draft";
  const live = status !== "draft";

  return (
    <div className="grid grid-cols-12 gap-5">
      <Card
        className="col-span-12 lg:col-span-6"
        index={0}
        title={t("launch.pick")}
        action={
          <Chip tone={live ? "accent" : "neutral"}>
            {status === "draft" ? t("status.draft") : status === "running" ? t("status.running") : t("status.ended")}
          </Chip>
        }
      >
        <div role="radiogroup" aria-label={t("launch.pick")} className="space-y-2">
          {TEMPLATES.map((item) => (
            <TemplateRow
              key={item.id}
              title={templateCopy(t, item).title}
              selected={item.id === templateId}
              locked={live}
              onSelect={() => onSelect(item.id)}
            />
          ))}
        </div>
        <p className="mt-3 text-[11px] leading-4 text-d-ink-3">{live ? t("launch.locked") : t("decide.note")}</p>
      </Card>

      <Card className="col-span-12 lg:col-span-6" title={t("launch.preview")} index={1}>
        {template && copy ? (
          <div className="rounded-xl bg-d-fill/60 px-5 py-5">
            <p className="text-[11px] font-medium uppercase tracking-wide text-d-ink-3">{t("launch.ask")}</p>
            <p className="mt-2 text-[15px] font-semibold leading-6 text-d-ink">{copy.question}</p>
            <div className="mt-3 grid grid-cols-2 gap-2">
              {copy.options.map((option) => (
                <div key={option.id} className="rounded-lg bg-d-surface px-3 py-2.5">
                  <p className="text-[13px] font-medium text-d-ink">
                    {option.emoji} {option.label}
                  </p>
                  <p className="mt-1 text-[11px] leading-4 text-d-ink-2">{option.blurb}</p>
                </div>
              ))}
            </div>
            <p className="mt-3 text-[11px] text-d-ink-3">{t("insights.gameHint")}</p>
          </div>
        ) : (
          <p className="grid h-[220px] place-items-center rounded-xl border border-dashed border-d-hairline text-[12px] text-d-ink-2">
            {t("launch.pickFirst")}
          </p>
        )}

        <div className="mt-4 flex flex-wrap gap-2">
          {live ? (
            <Button tone="solid" onClick={onNext}>
              {t("launch.seeResults")}
            </Button>
          ) : (
            <Button tone="solid" disabled={!template} onClick={onPublish}>
              {t("launch.publish")}
            </Button>
          )}
        </div>
        {!live && <p className="mt-3 text-[11px] leading-4 text-d-ink-3">{t("launch.noLink")}</p>}
      </Card>
    </div>
  );
}

function TemplateRow({
  title,
  selected,
  locked,
  onSelect,
}: {
  title: string;
  selected: boolean;
  locked: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      disabled={locked}
      onClick={onSelect}
      className={cn(
        "flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left transition-colors",
        selected ? "bg-d-accent/[8%] ring-1 ring-d-accent/40" : "bg-d-fill/50 hover:bg-d-fill",
        locked && !selected && "opacity-50",
      )}
    >
      <span className="min-w-0 flex-1 text-[13px] font-medium leading-5 text-d-ink">{title}</span>
      {selected && <Check className="size-4 text-d-accent-ink" strokeWidth={2.25} aria-hidden />}
    </button>
  );
}
