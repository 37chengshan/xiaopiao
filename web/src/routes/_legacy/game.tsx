import { createFileRoute } from "@tanstack/react-router";
import { ToHash } from "../_legacy";

/** The circle moved to the desktop shell; games live on the phone for now. */
export const Route = createFileRoute("/_legacy/game")({ component: () => <ToHash to="/app/circle" /> });
