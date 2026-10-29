import { getBook } from "@/features/biz/store";

/**
 * The two numbers the status strip needs from the shop book. Exposed as plain reads (not hooks) so
 * the strip stays a dumb presentational component; `useBizBook()` inside `StatusBar` keeps it fresh.
 */
export function dayEntry() {
  const { book, today } = getBook();
  return book.find((entry) => entry.day === today) ?? null;
}

export function bookWeek(): number {
  const { book, today } = getBook();
  const start = today - 6 * 24 * 60 * 60 * 1000;
  return book.reduce((sum, entry) => (entry.day >= start ? sum + entry.revenueCents : sum), 0);
}
