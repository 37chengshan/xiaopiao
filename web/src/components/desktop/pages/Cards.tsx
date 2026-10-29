import { Check, Lock } from "lucide-react";
import { useState } from "react";
import { motion } from "framer-motion";
import { cn } from "@/lib/utils";
import { useLocale } from "@/lib/strings";
import { useLedger } from "@/services/LedgerProvider";
import { WALLET, bankName, cardTone, monthSpend, type CardTone, type WalletCard } from "../cardsData";
import { money } from "../format";
import { Button, Card, Hint } from "../ui";
import { s } from "../strings";
import { c } from "./copy/cards";

const FACE: Record<CardTone, string> = {
  ink: "bg-d-ink text-d-canvas",
  fill: "bg-d-fill text-d-ink",
  outline: "bg-d-surface text-d-ink ring-1 ring-d-hairline",
};

/** How far each card behind the front one rises; the exposed strip is the click target. */
const PEEK = 18;

function CardFace({ card, tone, isDefault, front }: { card: WalletCard; tone: CardTone; isDefault: boolean; front?: boolean }) {
  return (
    <div className={cn("flex h-[196px] w-full flex-col justify-between rounded-[16px] p-5", FACE[tone], front && "shadow-[0_16px_36px_-14px_oklch(0_0_0/45%)]")}>
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate text-[13px] font-semibold tracking-[0.01em]">{bankName(card)}</p>
          <p className="mt-0.5 text-[11px] opacity-70">{card.kind === "credit" ? s("cd.credit") : s("cd.debit")}</p>
        </div>
        {isDefault && <span className="shrink-0 rounded-md bg-d-canvas/20 px-1.5 py-0.5 text-[10.5px] font-medium">{s("cd.default")}</span>}
      </div>
      <p className="font-mono text-[17px] tracking-[0.16em] opacity-90">•••• {card.tail}</p>
    </div>
  );
}

export function CardsPage() {
  useLocale();
  const { txns, now } = useLedger();
  const [activeId, setActiveId] = useState(WALLET[0]?.id ?? "");
  const [defaultId, setDefaultId] = useState(() => WALLET.find((card) => card.isDefault)?.id ?? WALLET[0]?.id ?? "");
  const active = WALLET.find((card) => card.id === activeId) ?? WALLET[0];

  if (!active) {
    return (
      <Card padded={false}>
        <div className="py-16 text-center">
          <p className="text-[15px] font-semibold text-d-ink">{s("cd.empty")}</p>
          <Hint className="mt-1.5">{s("cd.emptyHint")}</Hint>
        </div>
      </Card>
    );
  }

  const stack = [active, ...WALLET.filter((card) => card.id !== active.id)];
  const spend = monthSpend(txns, now, active, WALLET);
  const isDefault = active.id === defaultId;

  return (
    <div className="grid grid-cols-12 gap-5">
      <Card className="col-span-12 self-start xl:col-span-7" padded={false}>
        <div className="relative flex flex-col items-center overflow-hidden rounded-xl px-5 pb-7 pt-8">
          {/* Warm ground under the stack: the cards are black/bone, so the scene needs its own light. */}
          <span
            aria-hidden
            className="pointer-events-none absolute inset-x-0 -top-24 h-[260px] opacity-70"
            style={{ background: "radial-gradient(120% 100% at 50% 0%, color-mix(in oklab, var(--d-highlight) 60%, transparent), transparent 72%)" }}
          />
          <div className="relative mt-9 h-[224px] w-full max-w-[320px]">
            {stack.map((card, rank) => {
              const front = rank === 0;
              const tone = cardTone(card, WALLET, card.id === defaultId);
              const lift = c("cards.lift").replace("{bank}", bankName(card)).replace("{tail}", card.tail);
              return (
                <motion.button
                  key={card.id}
                  type="button"
                  onClick={() => setActiveId(card.id)}
                  disabled={front}
                  aria-label={front ? undefined : lift}
                  title={front ? undefined : lift}
                  className={cn("absolute inset-x-0 top-7 block text-left", front ? "cursor-default" : "cursor-pointer")}
                  style={{ zIndex: 20 - rank, transformOrigin: "top center" }}
                  initial={false}
                  animate={{ y: -rank * PEEK, scale: 1 - rank * 0.045 }}
                  transition={{ duration: 0.28, ease: [0.23, 1, 0.32, 1] }}
                >
                  <CardFace card={card} tone={tone} isDefault={card.id === defaultId} front={front} />
                  {/* The exposed strip names the card it lifts, so a stack of blanks stays usable. */}
                  {!front && (
                    <span
                      aria-hidden
                      className={cn(
                        "absolute inset-x-0 top-0 flex items-center justify-center text-[10px] font-medium leading-none opacity-70",
                        tone === "ink" ? "text-d-canvas" : "text-d-ink-2",
                      )}
                      style={{ height: PEEK }}
                    >
                      {bankName(card)} · •••• {card.tail}
                    </span>
                  )}
                </motion.button>
              );
            })}
          </div>
          <p className="relative mt-6 flex items-center gap-1.5 text-[11.5px] text-d-ink-3">
            <Lock className="size-3" strokeWidth={1.8} aria-hidden />
            {s("cd.hint")}
          </p>
        </div>
      </Card>

      <div className="col-span-12 flex flex-col gap-5 xl:col-span-5">
        <Card title={s("cd.detail")}>
          <dl className="space-y-2.5">
            <div className="flex items-baseline justify-between gap-3">
              <dt className="text-[12.5px] text-d-ink-2">{s("cd.bank")}</dt>
              <dd className="text-[13px] text-d-ink">{bankName(active)}</dd>
            </div>
            <div className="flex items-baseline justify-between gap-3">
              <dt className="text-[12.5px] text-d-ink-2">{s("cd.kind")}</dt>
              <dd className="text-[13px] text-d-ink">{active.kind === "credit" ? s("cd.credit") : s("cd.debit")}</dd>
            </div>
            <div className="flex items-baseline justify-between gap-3">
              <dt className="text-[12.5px] text-d-ink-2">{s("cd.tail")}</dt>
              <dd className="num font-mono text-[13px] tracking-[0.12em] text-d-ink">•••• {active.tail}</dd>
            </div>
          </dl>

          <div className="mt-4 rounded-lg bg-d-fill/60 px-3 py-2.5">
            <p className="text-[11.5px] text-d-ink-3">{c("cards.monthSpend")}</p>
            <p className="num mt-0.5 text-[18px] font-semibold leading-6 text-d-ink">{money(spend.cents)}</p>
            <p className="mt-0.5 text-[11px] text-d-ink-3">
              {spend.count > 0 ? `${spend.count} ${s("ledger.itemUnit")}` : c("cards.monthEmpty")}
            </p>
          </div>

          <div className="mt-3">
            {isDefault ? (
              <span className="inline-flex h-9 items-center gap-1.5 text-[12.5px] text-d-ink-3">
                <Check className="size-3.5" strokeWidth={2} aria-hidden />
                {s("cd.default")}
              </span>
            ) : (
              <Button icon={Check} onClick={() => setDefaultId(active.id)}>
                {c("cards.setDefault")}
              </Button>
            )}
          </div>

          <Hint className="mt-3">{s("cd.addBody")}</Hint>
        </Card>

        <Card title={s("cd.privacy")} action={<Lock className="size-3.5 text-d-ink-3" strokeWidth={1.8} aria-hidden />}>
          <p className="text-[12.5px] leading-[20px] text-d-ink-2">{s("cd.privacyBody")}</p>
          <Hint className="mt-2">{s("cd.stageHint")}</Hint>
        </Card>
      </div>
    </div>
  );
}
