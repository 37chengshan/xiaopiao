import { createFileRoute } from "@tanstack/react-router";
import { LandingPage } from "@/features/landing/LandingPage";

/** `/` is the landing page (docs/WEB-SPEC.md §L); the app itself lives under `/app`. */
export const Route = createFileRoute("/")({
  component: LandingPage,
});
