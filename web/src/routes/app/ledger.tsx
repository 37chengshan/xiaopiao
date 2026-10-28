import { createFileRoute } from "@tanstack/react-router";
import { LedgerPage } from "@/components/desktop/pages/Ledger";

export const Route = createFileRoute("/app/ledger")({ component: LedgerPage });
