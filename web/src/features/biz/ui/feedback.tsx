import { AnimatePresence, motion, useReducedMotion } from "framer-motion";
import { AlertTriangle, Check, Info, X } from "lucide-react";
import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Toaster, toast } from "sonner";
import { EASE_OUT } from "@/lib/motion";
import { cn } from "@/lib/utils";
import { useBizStrings } from "../strings";
import { fill } from "../text";

/**
 * Feedback ladder from docs/APP-DESIGN.md §7.7, Web side: one action, one kind of answer.
 * 0 inline · 1 toast (2.4s, bottom-right, one at a time) · 2 undo snackbar (5s, countdown line)
 * · 3 banner (stays until the problem is gone) · 4 confirm dialog (irreversible only).
 */

const TOAST_DURATION = 2400;

/** Mounted once per biz route tree: bottom-right, one toast at a time, ink on paper-white text. */
export function BizToaster() {
  return (
    <Toaster
      position="bottom-right"
      visibleToasts={1}
      duration={TOAST_DURATION}
      offset={24}
      gap={8}
      icons={{
        success: <Check className="size-4" strokeWidth={2.25} aria-hidden />,
        error: <AlertTriangle className="size-4" strokeWidth={2.25} aria-hidden />,
        info: <Info className="size-4" strokeWidth={2.25} aria-hidden />,
      }}
      toastOptions={{
        style: { width: "auto" },
        classNames: {
          toast:
            "!bg-d-ink !text-d-canvas !rounded-[10px] !px-3.5 !py-2.5 !text-[13px] !font-medium !gap-2 !border-0 !shadow-[0_10px_30px_-12px_oklch(0_0_0/35%)]",
          title: "!font-medium",
          description: "!text-d-canvas/70 !text-[12px]",
          icon: "!text-d-canvas",
          actionButton: "!bg-transparent !text-d-canvas !font-medium",
        },
      }}
    />
  );
}

/** Toast wording, bound to the current locale. */
export function useNotify() {
  const t = useBizStrings();
  return useMemo(
    () => ({
      saved: () => toast(t("toast.saved")),
      exported: (name: string) => toast(fill(t("toast.exported"), name)),
      imported: (days: number) => toast(fill(t("toast.imported"), `${days}`)),
      printed: () => toast(t("toast.printed")),
      imageSaved: () => toast(t("toast.imageSaved")),
      deleted: (label: string) => toast(fill(t("toast.deleted"), label)),
      failed: (text: string) => toast.error(text),
    }),
    [t],
  );
}

/**
 * Level-2 snackbar: text on the left, an accent `撤销` on the right, a 5s hairline countdown and a
 * reverse animation when the undo lands. Sits where the toast does, so only one of them is ever up.
 */
export function UndoSnackbar({
  text,
  onUndo,
  onExpire,
  duration = 5000,
}: {
  text: string;
  onUndo: () => void;
  onExpire: () => void;
  duration?: number;
}) {
  const t = useBizStrings();
  const reduce = useReducedMotion() ?? false;
  const fired = useRef(false);
  const [hovered, setHovered] = useState(false);

  // One answer at a time: a snackbar takes the slot, so any toast still up steps aside.
  useEffect(() => {
    toast.dismiss();
  }, []);

  useEffect(() => {
    if (hovered) return;
    const timer = window.setTimeout(() => {
      if (fired.current) return;
      fired.current = true;
      onExpire();
    }, duration);
    return () => window.clearTimeout(timer);
  }, [duration, hovered, onExpire]);

  return (
    <motion.div
      role="status"
      initial={reduce ? { opacity: 0 } : { opacity: 0, y: 16 }}
      animate={reduce ? { opacity: 1 } : { opacity: 1, y: 0 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.2, ease: EASE_OUT }}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      className="fixed bottom-6 right-6 z-[70] overflow-hidden rounded-[10px] bg-d-ink text-d-canvas shadow-[0_10px_30px_-12px_oklch(0_0_0/35%)]"
    >
      <div className="flex items-center gap-4 px-3.5 py-2.5 text-[13px] font-medium">
        <span>{text}</span>
        <button
          type="button"
          onClick={() => {
            fired.current = true;
            onUndo();
          }}
          className="text-d-accent-ink transition-opacity hover:opacity-80"
        >
          {t("revenue.undo")}
        </button>
      </div>
      {!reduce && (
        <span
          className="block h-px origin-left bg-d-accent"
          style={{
            animation: `biz-countdown ${duration}ms linear forwards`,
            animationPlayState: hovered ? "paused" : "running",
          }}
        />
      )}
    </motion.div>
  );
}

/** Level-3 banner: under the toolbar, fill background, one line, an optional action and a close. */
export function Banner({
  children,
  tone = "warn",
  action,
  onClose,
}: {
  children: ReactNode;
  tone?: "warn" | "negative" | "neutral";
  action?: ReactNode;
  onClose: () => void;
}) {
  const t = useBizStrings();
  const Icon = tone === "negative" ? X : AlertTriangle;
  return (
    <motion.div
      role="alert"
      initial={{ opacity: 0, y: -6 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.16, ease: EASE_OUT }}
      className={cn(
        "mb-4 flex min-h-9 items-center gap-2 rounded-lg px-3 text-[12px] leading-4",
        tone === "neutral" ? "bg-d-fill text-d-ink-2" : tone === "negative" ? "bg-d-negative/10 text-d-negative" : "bg-d-fill text-d-ink",
      )}
    >
      <Icon className="size-3.5 shrink-0" strokeWidth={1.75} aria-hidden />
      <span className="min-w-0 flex-1">{children}</span>
      {action}
      <button
        type="button"
        onClick={onClose}
        aria-label={t("banner.close")}
        className="grid size-6 shrink-0 place-items-center rounded text-d-ink-3 transition-colors hover:text-d-ink"
      >
        <X className="size-3.5" strokeWidth={1.75} aria-hidden />
      </button>
    </motion.div>
  );
}

/** Shared shell for the snackbar slot, so pages never stack feedback. */
export function FeedbackSlot({ text, onUndo, onExpire }: { text: string | null; onUndo: () => void; onExpire: () => void }) {
  return <AnimatePresence>{text && <UndoSnackbar key={text} text={text} onUndo={onUndo} onExpire={onExpire} />}</AnimatePresence>;
}
