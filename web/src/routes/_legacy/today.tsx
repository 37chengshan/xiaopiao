import { createFileRoute, redirect } from "@tanstack/react-router";

export const Route = createFileRoute("/_legacy/today")({
  beforeLoad: () => {
    throw redirect({ to: "/app/receipt", replace: true });
  },
});
