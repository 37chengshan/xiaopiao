import { Outlet, createFileRoute } from "@tanstack/react-router";
import { useEffect } from "react";

/**
 * Legacy phone shell (`/home`, `/ledger`, … on the old phone layout). Every page moved into the desktop shell at `/app/*` (docs/WEB-SPEC.md §A.3),
 * so this layout only passes the outlet through while the old links keep working.
 */
export const Route = createFileRoute("/_legacy")({ component: () => <Outlet /> });

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
