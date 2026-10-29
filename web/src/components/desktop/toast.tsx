import { Toaster, toast } from "sonner";
import { s } from "./strings";

/**
 * Feedback ladder (docs/APP-DESIGN.md §7.7) on desktop:
 * level 1 = ink pill bottom-right, 2.4s, one at a time; level 2 = the same pill with an undo link and a 5s
 * countdown hairline. Level 3 lives in Banner.tsx, level 4 is the confirm dialog.
 */
export function notify(message: string) {
  toast(message);
}

export function notifyUndo(message: string, onUndo: () => void) {
  toast.custom(
    (id) => (
      <div className="relative flex max-w-[420px] items-center gap-3 overflow-hidden rounded-full bg-d-ink py-2 pl-4 pr-1.5 text-[13px] font-medium text-d-canvas shadow-[0_10px_30px_-10px_oklch(0_0_0/45%)]">
        <span className="min-w-0 flex-1 truncate">{message}</span>
        <button
          type="button"
          onClick={() => {
            onUndo();
            toast.dismiss(id);
          }}
          className="d-press shrink-0 rounded-full px-2.5 py-1.5 text-[13px] font-medium text-d-canvas underline decoration-d-canvas/45 underline-offset-[3px] hover:decoration-d-canvas"
        >
          {s("common.undo")}
        </button>
        <span aria-hidden className="d-countdown absolute inset-x-4 bottom-0 h-[2px] origin-left rounded-full bg-d-canvas/35" />
      </div>
    ),
    { duration: 5000 },
  );
}

export function DesktopToaster() {
  return (
    <Toaster
      position="bottom-right"
      visibleToasts={1}
      duration={2400}
      offset={20}
      gap={8}
      toastOptions={{
        classNames: {
          toast: "!bg-d-ink !text-d-canvas !rounded-full !border-0 !px-4 !py-2.5 !text-[13px] !font-medium !shadow-[0_10px_30px_-10px_oklch(0_0_0/45%)]",
          title: "!text-[13px] !font-medium",
          description: "!text-[12px] !text-d-canvas/70",
        },
      }}
    />
  );
}
