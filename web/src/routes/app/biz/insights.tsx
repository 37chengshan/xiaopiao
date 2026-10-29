import { createFileRoute } from "@tanstack/react-router";
import { InsightsPage } from "@/features/biz/ui/InsightsPage";

/** 顾客洞察（docs/WEB-SPEC.md §B）。The desktop shell at `/app` renders this in its content area. */
export const Route = createFileRoute("/app/biz/insights")({
  component: InsightsPage,
});
