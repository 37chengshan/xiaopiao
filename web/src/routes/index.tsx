import { createFileRoute, redirect } from "@tanstack/react-router";

/** `/` lands on 记账, the first slot of the nav (docs/APP-DESIGN.md §3). */
export const Route = createFileRoute("/")({
  beforeLoad: () => {
    throw redirect({ to: "/ledger", replace: true });
  },
});
