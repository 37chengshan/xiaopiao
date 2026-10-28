import { createFileRoute } from "@tanstack/react-router";
import { GamePage } from "@/features/game/ui/GamePage";

export const Route = createFileRoute("/game")({
  component: GamePage,
});
