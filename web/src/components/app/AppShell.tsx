import { Link, useRouterState } from "@tanstack/react-router";
import { ArrowLeft } from "lucide-react";
import type { ReactNode } from "react";
import { Toaster as Sonner } from "sonner";
import { EntryProvider } from "@/features/entry/EntryProvider";
import { cn } from "@/lib/utils";
import { useLedger } from "@/services/LedgerProvider";
import { FirstRun, Onboarding } from "./FirstRun";
import { onboarding, useReplayingOnboarding } from "./onboardingStore";
import { t } from "@/lib/strings";
import { TabBar } from "./TabBar";

/** Shop view: not a tab, reached from 我的; shows a return pill instead of the tab bar. */
const SHOP_PATHS = ["/merchant"];
const CONSUMER_PATHS = ["/home", "/today", "/ledger", "/insights", "/me"];
/** Pages built from inset grouped lists sit on `bg-grouped` so white groups lift off the page. */
const GROUPED_PATHS = ["/ledger", "/insights", "/me"];

/**
 * Phone column: full-bleed on phones, a centered ~430px device on desktop.
 * The scroll area fills the whole column; the tab bar floats over it (content scrolls under the glass),
 * so pages get `--tabbar-h` of bottom space from the wrapper.
 */
export function AppShell({ children }: { children: ReactNode }) {
  const { mode } = useLedger();
  const path = useRouterState({ select: (s) => s.location.pathname });
  const gated = CONSUMER_PATHS.includes(path) && mode === null;
  const replaying = useReplayingOnboarding();
  const shop = SHOP_PATHS.includes(path);
  const grouped = !gated && GROUPED_PATHS.includes(path);
  return (
    <div className="flex min-h-dvh items-center justify-center bg-bg-grouped md:p-4">
      <div
        className={cn(
          "relative h-dvh w-full max-w-[430px] overflow-hidden transition-colors duration-200 ease-[var(--ease-out)] md:h-[min(900px,calc(100dvh-2rem))] md:rounded-[2.25rem] md:shadow-[0_24px_80px_-24px_oklch(0_0_0/25%)] md:ring-1 md:ring-hairline",
          grouped ? "bg-bg-grouped" : "bg-bg",
        )}
      >
        <EntryProvider>
          <main className="absolute inset-0 overflow-y-auto overscroll-contain">
            {gated ? <FirstRun /> : <div className="relative min-h-full pb-[var(--tabbar-h)]">{children}</div>}
          </main>
          {replaying && !gated && (
            <div className="absolute inset-0 z-40 overflow-y-auto bg-bg">
              <Onboarding onFinish={onboarding.close} />
            </div>
          )}
          {!gated && !shop && <TabBar />}
          {shop && (
            <Link
              to="/me"
              style={{ bottom: "max(16px, env(safe-area-inset-bottom, 0px))" }}
              className="btn-soft absolute left-1/2 z-30 inline-flex h-11 -translate-x-1/2 items-center gap-1.5 rounded-full bg-surface px-5 text-[15px] font-medium text-ink active:scale-[0.97]"
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
