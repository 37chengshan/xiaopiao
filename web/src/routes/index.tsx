import { createFileRoute, redirect } from "@tanstack/react-router";

/** `/` lands on the chat home inside the app layout. */
export const Route = createFileRoute("/")({
  beforeLoad: () => {
    throw redirect({ to: "/home", replace: true });
  },
});
