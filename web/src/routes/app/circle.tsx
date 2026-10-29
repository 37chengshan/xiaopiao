import { createFileRoute } from "@tanstack/react-router";
import { CirclePage } from "@/components/desktop/pages/Circle";

export const Route = createFileRoute("/app/circle")({ component: CirclePage });
