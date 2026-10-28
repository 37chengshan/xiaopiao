import { createFileRoute } from "@tanstack/react-router";
import { TodayPage } from "@/features/receipt/TodayPage";

export const Route = createFileRoute("/today")({ component: TodayPage });
