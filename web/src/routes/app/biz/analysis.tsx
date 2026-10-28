import { createFileRoute } from "@tanstack/react-router";
import { AnalysisPage } from "@/features/biz/ui/AnalysisPage";

/** 分析（docs/WEB-SPEC.md §B）。The desktop shell at `/app` renders this in its content area. */
export const Route = createFileRoute("/app/biz/analysis")({
  component: AnalysisPage,
});
