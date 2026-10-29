import { createFileRoute } from "@tanstack/react-router";
import { ReceiptPage } from "@/components/desktop/pages/Receipt";

export const Route = createFileRoute("/app/receipt")({ component: ReceiptPage });
