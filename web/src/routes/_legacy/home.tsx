import { createFileRoute, redirect } from "@tanstack/react-router";

export const Route = createFileRoute("/_legacy/home")({
  beforeLoad: () => {
    throw redirect({ to: "/app/ai", replace: true });
  },
});
