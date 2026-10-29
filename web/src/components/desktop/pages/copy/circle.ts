import { defineStrings } from "@/lib/strings";
import type { ShareItem } from "../../circleData";

/**
 * Copy for the circle page. zh is the source; en is written natively, not translated word for
 * word. Strings with `{name}` / `{count}` placeholders are filled by the page.
 */
export const c = defineStrings(
  {
    "circle.roomMeta": "{count} 位成员 · 消息只保存在这台设备上",
    "circle.matrixTitle": "谁能看到什么",
    "circle.matrixIntro": "共享范围按项授权，默认只给汇总、不给明细。",
    "circle.matrixMine": "圈子里看到的",
    "circle.matrixTheirs": "「{name}」愿意分享的",
    "circle.shared": "已共享",
    "circle.unshared": "未共享",
    "circle.revokeNote": "改动立即生效：关掉一项后，群里已经发过的小票也会一起隐藏。",
    "circle.hierarchyNote": "单笔明细是小票全文的前提；关掉今日总额，明细和小票也会一起隐藏。",
    "circle.share.todayTotal": "今日总额",
    "circle.share.todayTotalHint": "只显示一天一共花了多少，不含店名",
    "circle.share.categoryMix": "分类构成",
    "circle.share.categoryMixHint": "按分类的占比，不写店名",
    "circle.share.weeklyTitles": "周报与头衔",
    "circle.share.weeklyTitlesHint": "周报和头衔里可以提到你",
    "circle.share.txnDetail": "单笔明细",
    "circle.share.txnDetailHint": "每一笔的金额、商户和时间",
    "circle.share.receiptFull": "小票全文",
    "circle.share.receiptFullHint": "把小票原文一起发到圈子里",
    "circle.receipt": "小票",
    "circle.fullReceipt": "小票全文",
    "circle.total": "合计",
    "circle.noSpend": "这天没有支出",
    "circle.amountHidden": "金额未共享",
    "circle.blockedTotal": "关掉今日总额后，周报没有可分享的数字。",
    "circle.blockedTitles": "关掉周报与头衔后，不再往圈子里发周报。",
    "circle.visibilityTitle": "数据在哪",
    "circle.visibilityValue": "只在这台设备上",
    "circle.visibilityHint": "圈子还没有服务器：消息、共享开关和撤销都只写在本机，换一台设备看不到。",
  },
  {
    "circle.roomMeta": "{count} members · messages stay on this device",
    "circle.matrixTitle": "Who sees what",
    "circle.matrixIntro": "Each item is granted on its own: totals by default, never the details.",
    "circle.matrixMine": "What the circle sees",
    "circle.matrixTheirs": "What {name} shares",
    "circle.shared": "Shared",
    "circle.unshared": "Not shared",
    "circle.revokeNote": "Changes apply at once: switching an item off also hides it on receipts already in the feed.",
    "circle.hierarchyNote": "Itemised entries gate the full receipt; turning off the daily total hides entries and receipts too.",
    "circle.share.todayTotal": "Daily total",
    "circle.share.todayTotalHint": "How much one day cost, without shop names",
    "circle.share.categoryMix": "Category mix",
    "circle.share.categoryMixHint": "Share of spending by category, no shop names",
    "circle.share.weeklyTitles": "Weekly report & titles",
    "circle.share.weeklyTitlesHint": "Your report and titles can mention you",
    "circle.share.txnDetail": "Itemised entries",
    "circle.share.txnDetailHint": "Amount, shop and time for every entry",
    "circle.share.receiptFull": "Full receipt",
    "circle.share.receiptFullHint": "Posts the receipt as printed",
    "circle.receipt": "receipt",
    "circle.fullReceipt": "Full receipt",
    "circle.total": "Total",
    "circle.noSpend": "No spending that day",
    "circle.amountHidden": "Amount not shared",
    "circle.blockedTotal": "With the daily total off, a weekly report has no number to share.",
    "circle.blockedTitles": "With weekly reports off, you don't post one to the circle.",
    "circle.visibilityTitle": "Where it lives",
    "circle.visibilityValue": "On this device only",
    "circle.visibilityHint": "The circle has no server yet: messages, sharing switches and revocations are written on this device alone, and another device sees none of them.",
  },
);

export type CircleStringKey = Parameters<typeof c>[0];

export const SHARE_LABEL: Record<ShareItem, CircleStringKey> = {
  todayTotal: "circle.share.todayTotal",
  categoryMix: "circle.share.categoryMix",
  weeklyTitles: "circle.share.weeklyTitles",
  txnDetail: "circle.share.txnDetail",
  receiptFull: "circle.share.receiptFull",
};

export const SHARE_HINT: Record<ShareItem, CircleStringKey> = {
  todayTotal: "circle.share.todayTotalHint",
  categoryMix: "circle.share.categoryMixHint",
  weeklyTitles: "circle.share.weeklyTitlesHint",
  txnDetail: "circle.share.txnDetailHint",
  receiptFull: "circle.share.receiptFullHint",
};
