import { Outlet, createFileRoute } from "@tanstack/react-router";
import { useLayoutEffect } from "react";
import { AppShell } from "@/components/app/AppShell";
import { syncReceiptPrefsFromCloud } from "@/features/receipt/prefsSync";
import { initTheme } from "@/lib/theme";
import { LedgerProvider } from "@/services/LedgerProvider";

/** Pathless layout: ledger data + phone-column shell for every page (kept out of __root per QMuse import rules). */
export const Route = createFileRoute("/_app")({
  component: AppLayout,
});

// App-wide startup. QMuse import accepts neither initializers in main.tsx nor changes to __root, so it lives here; runs once per page load.
let started = false;
function startApp() {
  if (started) return;
  started = true;
  initTheme();
  syncReceiptPrefsFromCloud();
}

function AppLayout() {
  useLayoutEffect(startApp, []);
  return (
    <LedgerProvider>
      <AppShell>
        <Outlet />
      </AppShell>
    </LedgerProvider>
  );
}
