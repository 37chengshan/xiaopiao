import { createFileRoute } from "@tanstack/react-router";
import { SettingsPage } from "@/components/desktop/pages/Settings";

export const Route = createFileRoute("/app/settings")({ component: SettingsPage });
