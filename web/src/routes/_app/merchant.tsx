import { createFileRoute } from "@tanstack/react-router";
import { MerchantPage } from "@/features/merchant/ui/MerchantPage";

export const Route = createFileRoute("/_app/merchant")({
  component: MerchantPage,
});
