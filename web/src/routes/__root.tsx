import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Outlet, createRootRouteWithContext, useRouter } from "@tanstack/react-router";
import { setAppRouter } from "@/lib/routerRef";
import { NotFound } from "@/features/notfound/NotFound";

/** Unknown hash path: the full-bleed 404 screen, same one the router falls back to. */
function NotFoundComponent() {
  return <NotFound />;
}

/** A crashed route keeps the shell's own typography and the warm focus ring instead of legacy tokens. */
function ErrorComponent() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-d-canvas px-4 text-d-ink">
      <div className="max-w-md text-center">
        <h1 className="text-[17px] font-semibold tracking-[-0.01em]">页面加载失败</h1>
        <p className="mt-2 text-[13px] leading-[20px] text-d-ink-2">页面暂时无法加载。你可以尝试重试，或返回首页。</p>
        <div className="mt-6 flex flex-wrap justify-center gap-2">
          <button
            onClick={() => window.location.reload()}
            className="d-press inline-flex h-9 items-center justify-center rounded-lg bg-d-ink px-3.5 text-[13px] font-medium text-d-canvas focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-d-accent-ink"
          >
            重试
          </button>
          <a
            href="#/"
            className="d-press inline-flex h-9 items-center justify-center rounded-lg bg-d-fill px-3.5 text-[13px] font-medium text-d-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-d-accent-ink"
          >
            返回首页
          </a>
        </div>
      </div>
    </div>
  );
}

export const Route = createRootRouteWithContext<{ queryClient: QueryClient }>()({
  component: RootRoute,
  errorComponent: ErrorComponent,
  notFoundComponent: NotFoundComponent,
});

function RootRoute() {
  const { queryClient } = Route.useRouteContext();
  setAppRouter(useRouter());

  return (
    <QueryClientProvider client={queryClient}>
      <Outlet />
    </QueryClientProvider>
  );
}
