import { Outlet, useRouterState } from "@tanstack/react-router";
import { AnimatePresence, motion, useReducedMotion } from "framer-motion";
import { X } from "lucide-react";
import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Download } from "lucide-react";
import { startOfDay, type Txn } from "@/domain/types";
import { getLocale, setLocale, useLocale } from "@/lib/strings";
import { useLedger } from "@/services/LedgerProvider";
import "./desktop.css";
import { Banner } from "./Banner";
import { MessageCenter } from "./MessageCenter";
import { DesktopToaster } from "./toast";
import { CommandPalette } from "./palette";
import { EntryDialog, PrintDialog } from "./dialogs";
import { useFlag, useHotkeys, useMediaQuery, dismissNotice, useNoticeDismissed } from "./hooks";
import { titleOf } from "./nav";
import { useDayDoc } from "./receiptDoc";
import { Sidebar } from "./Sidebar";
import { s } from "./strings";
import { Toolbar } from "./toolbar";
import { Button, IconButton } from "./ui";
import type { ReceiptDoc } from "@/features/receipt/receiptModel";

interface ShellActions {
  /** `null`/omitted opens a blank entry; pass a row to edit it. */
  openEntry: (txn?: Txn | null) => void;
  /** Print any day's paper; omitted prints today's. */
  openPrint: (doc?: ReceiptDoc) => void;
}

const ShellContext = createContext<ShellActions | null>(null);

export function useShell(): ShellActions {
  const ctx = useContext(ShellContext);
  if (!ctx) throw new Error("useShell must be used inside Shell");
  return ctx;
}

/**
 * Desktop shell (docs/WEB-SPEC.md §A.1): material sidebar + 52px toolbar + a 36px onboarding notice,
 * with the receipt control and command palette living in the shell so every page gets them.
 */
export function Shell() {
  useLocale();
  const pathname = useRouterState({ select: (state) => state.location.pathname });
  const reduce = useReducedMotion() ?? false;
  const { now } = useLedger();
  const title = titleOf(pathname);
  const todayDoc = useDayDoc(startOfDay(now));
  const noticeGone = useNoticeDismissed();

  const [collapsedPref, setCollapsedPref] = useFlag("xiaopiao.d.sidebar-collapsed");
  const narrow = useMediaQuery("(max-width: 1023px)");
  const phone = useMediaQuery("(max-width: 767px)");
  const collapsed = collapsedPref || narrow;

  const [drawer, setDrawer] = useState(false);
  const [messages, setMessages] = useState(false);
  const [unread, setUnread] = useState(true);
  const [palette, setPalette] = useState(false);
  const [entry, setEntry] = useState<{ txn: Txn | null } | null>(null);
  const [print, setPrint] = useState<{ doc: ReceiptDoc; origin: DOMRect | null } | null>(null);

  const actions = useMemo<ShellActions>(
    () => ({
      openEntry: (txn) => setEntry({ txn: txn ?? null }),
      openPrint: (doc) =>
        setPrint({
          doc: doc ?? todayDoc,
          // The sheet grows out of the toolbar receipt control (§A.4 shared element), measured before the dialog mounts.
          origin: document.querySelector("[data-receipt-widget]")?.getBoundingClientRect() ?? null,
        }),
    }),
    [todayDoc],
  );

  useHotkeys({
    onRecord: () => setEntry({ txn: null }),
    onSearch: () => setPalette(true),
    onPalette: () => setPalette(true),
    onPrint: () => setPrint({ doc: todayDoc, origin: document.querySelector("[data-receipt-widget]")?.getBoundingClientRect() ?? null }),
    onToggleSidebar: () => (phone ? setDrawer((v) => !v) : setCollapsedPref(!collapsed)),
  });

  useEffect(() => {
    document.title = `${title} · ${s("brand")}`;
  }, [title]);

  // Leaving the drawer open across a navigation would cover the page it just opened.
  useEffect(() => setDrawer(false), [pathname]);

  const soon = () => toast(s("soon.title"), { description: s("soon.body") });
  const chrome = 52 + (noticeGone ? 0 : 36);

  return (
    <ShellContext.Provider value={actions}>
      <div className="flex h-dvh w-full overflow-hidden bg-d-canvas text-d-ink">
        {!phone && <Sidebar collapsed={collapsed} pathname={pathname} onToggle={() => setCollapsedPref(!collapsedPref)} />}

        <div className="flex min-w-0 flex-1 flex-col">
          <Toolbar
            title={title}
            onSearch={() => setPalette(true)}
            onPrint={() => setPrint({ doc: todayDoc, origin: document.querySelector("[data-receipt-widget]")?.getBoundingClientRect() ?? null })}
            onOpenSidebar={phone ? () => setDrawer(true) : undefined}
            onMessages={() => {
              setMessages(true);
              setUnread(false);
            }}
            unread={unread}
          />

          {!noticeGone && (
            <Banner
              icon={Download}
              onDismiss={dismissNotice}
              action={
                <>
                  <Button size="sm" variant="primary" onClick={soon}>
                    {s("notice.download")}
                  </Button>
                  <Button size="sm" variant="quiet" className="bg-d-surface" onClick={soon}>
                    {s("notice.login")}
                  </Button>
                </>
              }
            >
              {s("notice.body")}
            </Banner>
          )}

          <main className="relative flex min-h-0 flex-1 flex-col overflow-hidden" style={{ ["--d-chrome" as string]: `${chrome}px` }}>
            <motion.div
              key={pathname}
              initial={reduce ? false : { opacity: 0, y: 4 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.16, ease: [0.23, 1, 0.32, 1] }}
              className="hide-scrollbar min-h-0 flex-1 overflow-y-auto"
            >
              <div className="mx-auto w-full max-w-[1200px] px-4 py-5 lg:px-8 lg:py-6">
                <Outlet />
              </div>
            </motion.div>
          </main>
        </div>

        <AnimatePresence>
          {phone && drawer && (
            <motion.div className="fixed inset-0 z-40" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.16 }}>
              <button type="button" aria-label={s("common.close")} className="absolute inset-0 bg-black/25" onClick={() => setDrawer(false)} />
              <motion.div
                className="absolute inset-y-0 left-0"
                initial={{ x: -232 }}
                animate={{ x: 0 }}
                exit={{ x: -232 }}
                transition={{ duration: 0.2, ease: [0.23, 1, 0.32, 1] }}
              >
                <Sidebar collapsed={false} pathname={pathname} onToggle={() => setDrawer(false)} toggleLabel={s("common.close")} />
              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      <CommandPalette
        open={palette}
        onOpenChange={setPalette}
        onRecord={() => setEntry({ txn: null })}
        onPrint={() => setPrint({ doc: todayDoc, origin: document.querySelector("[data-receipt-widget]")?.getBoundingClientRect() ?? null })}
        onLocale={() => setLocale(getLocale() === "zh" ? "en" : "zh")}
      />

      <EntryDialog open={entry !== null} onOpenChange={(open) => !open && setEntry(null)} initial={entry?.txn ?? null} />
      {print && <PrintDialog open onOpenChange={(open) => !open && setPrint(null)} doc={print.doc} origin={print.origin} />}

      <MessageCenter open={messages} onClose={() => setMessages(false)} />
      <DesktopToaster />
    </ShellContext.Provider>
  );
}
