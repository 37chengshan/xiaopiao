import { defineStrings } from "@/lib/strings";

/**
 * Copy for the 404 screen. zh is the source; en is written natively.
 * Kept beside the page instead of in `components/desktop/strings.ts` because the screen is
 * reachable outside the desktop shell (any unknown hash path).
 */
export const s = defineStrings(
  {
    "nf.lead": "卡纸了。",
    "nf.body": "这张小票没打出来，但你的账还在。带你回去。",
    "nf.home": "回到首页",
    "nf.app": "打开 App",
    "nf.brand": "今日小票",
    "nf.label": "页面不存在",
  },
  {
    "nf.lead": "Paper jam.",
    "nf.body": "This receipt didn't print, but your ledger is safe. Let's get you back.",
    "nf.home": "Back to the site",
    "nf.app": "Open the app",
    "nf.brand": "Today's Receipt",
    "nf.label": "Page not found",
  },
);
