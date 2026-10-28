import { useSyncExternalStore } from "react";

/** Replaying the intro tour from 我的 (first run is gated by "no mode chosen yet"). */
let replaying = false;
const listeners = new Set<() => void>();
const emit = () => listeners.forEach((l) => l());

export const onboarding = {
  replay() {
    replaying = true;
    emit();
  },
  close() {
    replaying = false;
    emit();
  },
};

export function useReplayingOnboarding(): boolean {
  return useSyncExternalStore(
    (l) => {
      listeners.add(l);
      return () => listeners.delete(l);
    },
    () => replaying,
    () => false,
  );
}
