import { createFileRoute } from "@tanstack/react-router";
import { OverviewPage } from "@/features/biz/ui/OverviewPage";

/** 经营总览（docs/WEB-SPEC.md §B）。The desktop shell at `/app` renders this in its content area. */
export const Route = createFileRoute("/app/biz/")({
  component: OverviewPage,
});
