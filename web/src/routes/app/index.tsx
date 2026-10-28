import { createFileRoute } from "@tanstack/react-router";
import { OverviewPage } from "@/components/desktop/pages/Overview";

export const Route = createFileRoute("/app/")({ component: OverviewPage });
