import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useLayoutEffect } from "react";
import { Shell } from "@/components/desktop/Shell";
import { initLocalePref } from "@/components/desktop/localePref";
import { startApp } from "@/components/desktop/startApp";
import { LedgerProvider } from "@/services/LedgerProvider";

/**
 * Desktop app shell (docs/WEB-SPEC.md §A). Every `/app/*` page — personal and shop alike — renders
 * inside this layout, so the sidebar, toolbar, command palette and print dialog exist once.
 * The ledger provider lives here rather than in __root to keep the phone routes independent.
 */
export const Route = createFileRoute("/app")({ component: AppLayout });

function AppLayout() {
  // Theme + cloud receipt prefs, the same startup the phone layout used to own.
  useLayoutEffect(startApp, []);
  useEffect(() => {
    initLocalePref();
  }, []);
  return (
    <LedgerProvider>
      <Shell />
    </LedgerProvider>
  );
}
