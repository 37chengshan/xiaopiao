import { createFileRoute } from "@tanstack/react-router";
import { PrivacyPage } from "@/features/privacy/PrivacyPage";

export const Route = createFileRoute("/me")({ component: PrivacyPage });
