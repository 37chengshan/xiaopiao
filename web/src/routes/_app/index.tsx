import { createFileRoute } from "@tanstack/react-router";
import { HomeChat } from "@/features/home/HomeChat";

export const Route = createFileRoute("/_app/")({
  component: HomeChat,
});
