import type { Router } from "@tanstack/react-router";
import type { routeTree } from "@/routeTree.gen";

/** Concrete router type for this app's route tree; the shell and nav helpers talk about this, not an inferred shape. */
export type AppRouter = Router<typeof routeTree>;

/**
 * The single router instance, published at module scope so non-React call sites (sidebar rows, palette,
 * page buttons) can navigate through the router itself. Writing `location.hash` directly used to bypass
 * it: the router only reacts to the `popstate` a fragment write happens to emit, so on engines that only
 * fire `hashchange` the URL moved and the view did not — the click appeared to do nothing.
 *
 * The root route registers it (not router.tsx or main.tsx): QMuse's import may replace those two files.
 */
let instance: AppRouter | null = null;

export function setAppRouter(router: AppRouter) {
  instance = router;
}

export function appRouter(): AppRouter {
  if (!instance) throw new Error("appRouter() used before the router was created");
  return instance;
}
