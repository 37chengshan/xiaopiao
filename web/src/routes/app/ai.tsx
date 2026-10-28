import { createFileRoute } from "@tanstack/react-router";
import { AiPage } from "@/components/desktop/pages/Ai";

export const Route = createFileRoute("/app/ai")({ component: AiPage });
