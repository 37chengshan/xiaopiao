import { Outlet, createFileRoute } from "@tanstack/react-router";
import "@/features/biz/biz.css";
import { BizToaster } from "@/features/biz/ui/feedback";

/** Layout for every `/app/biz/*` page: the only place the console's toast host is mounted. */
export const Route = createFileRoute("/app/biz")({
  component: BizLayout,
});

function BizLayout() {
  return (
    <>
      <Outlet />
      <BizToaster />
    </>
  );
}
