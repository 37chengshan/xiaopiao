import { createFileRoute } from "@tanstack/react-router";
import { CardsPage } from "@/components/desktop/pages/Cards";

export const Route = createFileRoute("/app/cards")({ component: CardsPage });
