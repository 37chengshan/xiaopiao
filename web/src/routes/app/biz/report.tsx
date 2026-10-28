import { createFileRoute } from "@tanstack/react-router";
import { ReportPage } from "@/features/biz/ui/ReportPage";

/** 日报（docs/WEB-SPEC.md §B）。The desktop shell at `/app` renders this in its content area. */
export const Route = createFileRoute("/app/biz/report")({
  component: ReportPage,
});
