import { useSyncExternalStore } from "react";

const DONE_KEY = "xiaopiao.onboarded";

function readDone(): boolean {
  try {
    return localStorage.getItem(DONE_KEY) === "1";
  } catch {
    return false;
  }
}

/** First run shows the intro tour once; a replay from 设置 reuses the same screen. */
let firstRun = !readDone();
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
  finishFirstRun() {
    firstRun = false;
    try {
      localStorage.setItem(DONE_KEY, "1");
    } catch {
      /* private mode: the tour just shows again next load */
    }
    emit();
  },
};

function useStore(read: () => boolean): boolean {
  return useSyncExternalStore(
    (l) => {
      listeners.add(l);
      return () => listeners.delete(l);
    },
    read,
    () => false,
  );
}

export const useReplayingOnboarding = () => useStore(() => replaying);
export const useFirstRun = () => useStore(() => firstRun);
