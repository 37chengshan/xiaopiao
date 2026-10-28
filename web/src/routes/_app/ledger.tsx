import { createFileRoute } from "@tanstack/react-router";
import { LedgerPage } from "@/features/ledger/LedgerPage";

export const Route = createFileRoute("/_app/ledger")({ component: LedgerPage });
