import { createFileRoute, redirect } from "@tanstack/react-router";

export const Route = createFileRoute("/_legacy/ledger")({
  beforeLoad: () => {
    throw redirect({ to: "/app/ledger", replace: true });
  },
});
