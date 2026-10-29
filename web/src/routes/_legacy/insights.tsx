import { createFileRoute, redirect } from "@tanstack/react-router";

export const Route = createFileRoute("/_legacy/insights")({
  beforeLoad: () => {
    throw redirect({ to: "/app/stats", replace: true });
  },
});
