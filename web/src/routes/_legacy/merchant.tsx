import { createFileRoute } from "@tanstack/react-router";
import { ToHash } from "../_legacy";

/** The shop console lives under /app/biz/* (built on another branch); hash redirect keeps the link alive. */
export const Route = createFileRoute("/_legacy/merchant")({ component: () => <ToHash to="/app/biz/insights" /> });
