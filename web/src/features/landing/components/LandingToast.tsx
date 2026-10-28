import { AnimatePresence, motion } from "framer-motion";
import { useSyncExternalStore } from "react";

/** Tiny toast for placeholder actions (sign-in, download). Lives inside the landing only. */
let current: string | null = null;
let timer: number | undefined;
const listeners = new Set<() => void>();

const emit = () => listeners.forEach((listener) => listener());

export function landingToast(text: string) {
  current = text;
  emit();
  window.clearTimeout(timer);
  timer = window.setTimeout(() => {
    current = null;
    emit();
  }, 2600);
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function LandingToaster() {
  const message = useSyncExternalStore(
    subscribe,
    () => current,
    () => null,
  );

  return (
    <div className="pointer-events-none fixed inset-x-0 bottom-6 z-[70] flex justify-center px-4">
      <AnimatePresence>
        {message ? (
          <motion.div
            key={message}
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 8 }}
            transition={{ duration: 0.2, ease: [0.23, 1, 0.32, 1] }}
            role="status"
            className="rounded-full border border-d-hairline bg-d-canvas/90 px-5 py-2.5 text-[13px] text-d-ink backdrop-blur-xl"
          >
            {message}
          </motion.div>
        ) : null}
      </AnimatePresence>
    </div>
  );
}
