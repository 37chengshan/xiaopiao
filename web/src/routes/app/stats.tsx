import { createFileRoute } from "@tanstack/react-router";
import { StatsPage } from "@/components/desktop/pages/Stats";

export const Route = createFileRoute("/app/stats")({ component: StatsPage });
