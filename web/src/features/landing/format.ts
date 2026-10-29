import { getLocale } from "@/lib/strings";

const MONTH_EN = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
const WEEKDAY_EN = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

/** Dates printed on sample paper follow the reading language (merchant names stay as the data says). */
export function landingDate(year: number, month: number, day: number): string {
  const d = new Date(year, month - 1, day);
  if (getLocale() === "en") return `${MONTH_EN[d.getMonth()]} ${d.getDate()}, ${WEEKDAY_EN[d.getDay()]}`;
  const weekday = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"][d.getDay()];
  return `${d.getMonth() + 1}月${d.getDate()}日 ${weekday}`;
}
