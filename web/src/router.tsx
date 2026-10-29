import { QueryClient } from "@tanstack/react-query";
import { createHashHistory, createRouter, type Router } from "@tanstack/react-router";
import { routeTree } from "./routeTree.gen";

/** Concrete router type for this app's route tree; the shell and nav helpers talk about this, not an inferred shape. */
export type AppRouter = Router<typeof routeTree>;

/**
 * The single router instance, published at module scope so non-React call sites (sidebar rows, palette,
 * page buttons) can navigate through the router itself. Writing `location.hash` directly used to bypass
 * it: the router only reacts to the `popstate` a fragment write happens to emit, so on engines that only
 * fire `hashchange` the URL moved and the view did not — the click appeared to do nothing.
 */
let instance: AppRouter | null = null;

export function setAppRouter(router: AppRouter) {
  instance = router;
}

export function appRouter(): AppRouter {
  if (!instance) throw new Error("appRouter() used before the router was created");
  return instance;
}

export function getRouter(): AppRouter {
  const queryClient = new QueryClient();
  const hashHistory = createHashHistory();

  const router = createRouter({
    routeTree,
    history: hashHistory,
    context: { queryClient },
    scrollRestoration: true,
    defaultPreloadStaleTime: 0,
  });

  return router;
}
