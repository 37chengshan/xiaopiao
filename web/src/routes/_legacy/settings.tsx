import { createFileRoute, redirect } from "@tanstack/react-router";

export const Route = createFileRoute("/_legacy/settings")({
  beforeLoad: () => {
    throw redirect({ to: "/app/settings", replace: true });
  },
});
