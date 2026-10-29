import { Outlet, createFileRoute } from "@tanstack/react-router";
import { useEffect, useLayoutEffect } from "react";
import { startApp } from "@/components/desktop/startApp";

/**
 * Legacy phone shell (`/home`, `/ledger`, … kept as URLs). Every page moved into the desktop shell at
 * `/app/*` (docs/WEB-SPEC.md §A.3), so this layout only starts the app and passes the outlet through
 * while the redirects below run.
 */
export const Route = createFileRoute("/_legacy")({ component: () => <LegacyOutlet /> });

function LegacyOutlet() {
  useLayoutEffect(startApp, []);
  useEffect(() => {
    document.title = "今日小票";
  }, []);
  return <Outlet />;
}

/**
 * Redirect to a hash path that has no typed route on this branch yet (the shop console is built separately;
 * the router uses hash history, so replacing the hash navigates without a reload).
 */
export function ToHash({ to }: { to: string }) {
  useEffect(() => {
    window.location.replace(`#${to}`);
  }, [to]);
  return null;
}
