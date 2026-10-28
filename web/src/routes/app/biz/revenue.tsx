import { createFileRoute } from "@tanstack/react-router";
import { RevenuePage } from "@/features/biz/ui/RevenuePage";

/** 营收（docs/WEB-SPEC.md §B）。The desktop shell at `/app` renders this in its content area. */
export const Route = createFileRoute("/app/biz/revenue")({
  component: RevenuePage,
});
