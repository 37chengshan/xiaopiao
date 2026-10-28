import { Outlet, createFileRoute } from "@tanstack/react-router";
import { AppShell } from "@/components/app/AppShell";
import { LedgerProvider } from "@/services/LedgerProvider";

/** Pathless layout: ledger data + phone-column shell for every page (kept out of __root per QMuse import rules). */
export const Route = createFileRoute("/_app")({
  component: AppLayout,
});

function AppLayout() {
  return (
    <LedgerProvider>
      <AppShell>
        <Outlet />
      </AppShell>
    </LedgerProvider>
  );
}
