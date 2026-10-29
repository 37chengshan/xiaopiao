import { Lock, Plus } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { motion } from "framer-motion";
import { cn } from "@/lib/utils";
import { WALLET, type WalletCard } from "../cardsData";
import { Card, Button, Hint } from "../ui";
import { s } from "../strings";

const FACE = {
  ink: "bg-d-ink text-d-canvas",
  fill: "bg-d-fill text-d-ink",
  outline: "bg-d-surface text-d-ink ring-1 ring-d-hairline",
} as const;

function CardFace({ card, active }: { card: WalletCard; active: boolean }) {
  return (
    <div className={cn("relative flex h-[196px] w-[320px] max-w-full flex-col justify-between rounded-[16px] p-5", FACE[card.tone], active && "shadow-[0_16px_36px_-14px_oklch(0_0_0/45%)]")}>
      <div className="flex items-start justify-between">
        <div>
          <p className="text-[13px] font-semibold tracking-[0.01em]">{card.bank}</p>
          <p className="mt-0.5 text-[11px] opacity-70">{card.kind === "credit" ? s("cd.credit") : s("cd.debit")}</p>
        </div>
        {card.isDefault && <span className="rounded-md bg-d-canvas/20 px-1.5 py-0.5 text-[10.5px] font-medium">{s("cd.default")}</span>}
      </div>
      <div>
        <p className="font-mono text-[17px] tracking-[0.16em] opacity-90">•••• {card.tail}</p>
      </div>
    </div>
  );
}

export function CardsPage() {
  const [activeId, setActiveId] = useState(WALLET[0]?.id ?? "");
  const active = WALLET.find((c) => c.id === activeId) ?? WALLET[0];
  const behind = WALLET.filter((c) => c.id !== active.id);

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

  return (
    <div className="grid grid-cols-12 gap-5">
      <Card className="col-span-12 self-start xl:col-span-7">
        <div className="flex flex-col items-center py-4">
          <motion.div layout transition={{ duration: 0.24, ease: [0.23, 1, 0.32, 1] }} className="w-full max-w-[320px]">
            <CardFace card={active} active />
          </motion.div>
          {behind.length > 0 && (
            <div className="mt-2 flex flex-col items-center gap-2">
              {behind.map((card, i) => (
                <button
                  key={card.id}
                  type="button"
                  onClick={() => setActiveId(card.id)}
                  aria-label={`${card.bank} ${s("cd.tail")} ${card.tail}`}
                  title={`${card.bank} · •••• ${card.tail}`}
                  className={cn("d-press h-2.5 rounded-full bg-d-ink-3 transition-opacity hover:opacity-100", i === 0 ? "opacity-55" : "opacity-30")}
                  style={{ width: 296 - i * 22 }}
                />
              ))}
            </div>
          )}
        </div>
      </Card>

      <div className="col-span-12 flex flex-col gap-5 xl:col-span-5">
        <Card title={s("cd.detail")}>
          <dl className="space-y-2.5">
            <div className="flex items-baseline justify-between gap-3">
              <dt className="text-[12.5px] text-d-ink-2">{s("cd.bank")}</dt>
              <dd className="text-[13px] text-d-ink">{active.bank}</dd>
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
          <div className="mt-4 flex items-center gap-2">
            <Button
              icon={Plus}
              onClick={() => toast(s("soon.title"), { description: s("cd.addBody") })}
            >
              {s("cd.add")}
            </Button>
            <span className="text-[11.5px] text-d-ink-3">{s("cd.addBody")}</span>
          </div>
        </Card>

        <Card title={s("cd.privacy")} action={<Lock className="size-3.5 text-d-ink-3" strokeWidth={1.8} aria-hidden />}>
          <p className="text-[12.5px] leading-[20px] text-d-ink-2">{s("cd.privacyBody")}</p>
        </Card>


      </div>
    </div>
  );
}
