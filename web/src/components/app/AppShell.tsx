import { Link, useRouterState } from "@tanstack/react-router";
import { motion, useReducedMotion } from "framer-motion";
import { ArrowLeft } from "lucide-react";
import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from "react";
import { Toaster as Sonner } from "sonner";
import { EntryProvider } from "@/features/entry/EntryProvider";
import { DUR, EASE_OUT } from "@/lib/motion";
import { cn } from "@/lib/utils";
import { FirstRun, Onboarding } from "./FirstRun";
import { onboarding, useFirstRun, useReplayingOnboarding } from "./onboardingStore";
import { t } from "@/lib/strings";
import { TabBar } from "./TabBar";

/** Shop view: not a tab, reached from 设置; shows a return pill instead of the tab bar. */
const SHOP_PATHS = ["/merchant"];
/** Standalone pages (a back button, no tab bar). */
const BARE_PATHS = ["/settings"];
const CONSUMER_PATHS = ["/home", "/today", "/ledger", "/insights", "/game", "/settings"];
/** Pages built from inset grouped lists sit on `bg-grouped` so white groups lift off the page. */
const GROUPED_PATHS = ["/ledger", "/insights", "/settings"];

/**
 * Phone column: full-bleed on phones, a centered ~430px device on desktop.
 * The scroll area fills the whole column; the tab bar floats over it (content scrolls under the glass),
 * so pages get `--tabbar-h` of bottom space from the wrapper.
 */
/**
 * Keyboard handling (2026-10-28, one-tap logging): while a text field is focused the tab bar slides away and the
 * composer drops to the bottom edge; on iOS (which does not resize the layout viewport) the column follows visualViewport.
 */
function useKeyboard() {
  const [typing, setTyping] = useState(false);
  const [vvh, setVvh] = useState<number | null>(null);
  useEffect(() => {
    const editable = (el: EventTarget | null) => el instanceof HTMLElement && (el.tagName === "INPUT" || el.tagName === "TEXTAREA") && !(el as HTMLInputElement).readOnly;
    const on = (e: FocusEvent) => editable(e.target) && setTyping(true);
    const off = () => setTyping(false);
    document.addEventListener("focusin", on);
    document.addEventListener("focusout", off);
    const vv = window.visualViewport;
    const sync = () => vv && setVvh(window.innerWidth < 768 && vv.height < window.innerHeight - 120 ? vv.height : null);
    vv?.addEventListener("resize", sync);
    return () => {
      document.removeEventListener("focusin", on);
      document.removeEventListener("focusout", off);
      vv?.removeEventListener("resize", sync);
    };
  }, []);
  return { typing, vvh };
}

export function AppShell({ children }: { children: ReactNode }) {
  const path = useRouterState({ select: (s) => s.location.pathname });
  const reduce = useReducedMotion() ?? false;
  // Crossfade only when the route actually changes; the first paint appears as-is.
  const prevPath = useRef(path);
  const entering = prevPath.current !== path;
  prevPath.current = path;
  const firstRun = useFirstRun();
  const gated = CONSUMER_PATHS.includes(path) && firstRun;
  const { typing, vvh } = useKeyboard();
  const replaying = useReplayingOnboarding();
  const shop = SHOP_PATHS.includes(path);
  const bare = BARE_PATHS.includes(path);
  const grouped = !gated && GROUPED_PATHS.includes(path);
  return (
    <div className="flex min-h-dvh items-center justify-center bg-bg-grouped md:p-4">
      <div
        className={cn(
          "relative h-dvh w-full max-w-[430px] overflow-hidden transition-colors duration-200 ease-[var(--ease-out)] md:h-[min(900px,calc(100dvh-2rem))] md:rounded-[2.25rem] md:shadow-[0_24px_80px_-24px_oklch(0_0_0/25%)] md:ring-1 md:ring-hairline",
          grouped ? "bg-bg-grouped" : "bg-bg",
        )}
        style={{ ...(typing ? ({ "--tabbar-h": "8px" } as CSSProperties) : null), ...(vvh ? { height: vvh } : null) }}
      >
        <EntryProvider>
          <main className="absolute inset-0 overflow-y-auto overscroll-contain">
            {gated ? <FirstRun /> : (
              <motion.div
                key={path}
                initial={entering && !reduce ? { opacity: 0 } : false}
                animate={{ opacity: 1 }}
                transition={{ duration: DUR.fade, ease: EASE_OUT }}
                className="relative min-h-full pb-[var(--tabbar-h)]"
              >
                {children}
              </motion.div>
            )}
          </main>
          {replaying && !gated && (
            <div className="absolute inset-0 z-40 overflow-y-auto bg-bg">
              <Onboarding onFinish={onboarding.close} />
            </div>
          )}
          {!gated && !shop && !bare && <TabBar hidden={typing} />}
          {shop && (
            <Link
              to="/settings"
              style={{ bottom: "max(16px, env(safe-area-inset-bottom, 0px))" }}
              className="glass-float absolute left-1/2 z-30 inline-flex !bg-surface-2 h-11 -translate-x-1/2 items-center gap-1.5 rounded-full px-5 text-[15px] font-medium text-ink"
            >
              <ArrowLeft className="size-4" aria-hidden />
              {t("back.personal")}
            </Link>
          )}
        </EntryProvider>
        <Sonner
          position="top-center"
          offset={12}
          style={
            {
              "--normal-bg": "var(--ink)",
              "--normal-text": "var(--bg)",
              "--normal-border": "transparent",
              "--border-radius": "999px",
            } as React.CSSProperties
          }
          toastOptions={{
            classNames: {
              toast: "!px-4 !py-2.5 !text-[14px] !shadow-[0_8px_30px_oklch(0_0_0/18%)]",
              description: "!text-[13px] !opacity-70",
              actionButton: "!bg-transparent !font-semibold !text-clay",
            },
          }}
        />
      </div>
    </div>
  );
}
