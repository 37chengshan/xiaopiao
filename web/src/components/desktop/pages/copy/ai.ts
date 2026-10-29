import { defineStrings } from "@/lib/strings";

/**
 * Copy for the AI page at `/app/ai`. zh is the source; en is written natively, not translated.
 * Existing shared keys (ai.answer.*, tool.*, ledger.import*) still live in `components/desktop/strings.ts`.
 */
export const s = defineStrings(
  {
    // thread state
    "state.error": "账本没读出来，下面的数字可能不全。",
    "answer.loading": "账本还在读，等它读完再问我。",
    "answer.offline": "我现在读不到账本，数字可能不全。重试之后再问一次。",

    // this month
    "answer.month": "这个月到现在花了 {}，{} 笔。花得最多的是{}，{}。",
    "answer.monthNone": "这个月还没有支出。",
    "chart.month": "本月",

    // draft card
    "drafts.group": "待确认的账目",
    "drafts.editAria": "编辑 {}",
    "drafts.removeAria": "去掉 {}",
    "drafts.name": "商家",
    "drafts.amount": "金额",
    "drafts.category": "分类",
    "drafts.date": "日期",
    "drafts.done": "完成",
    "drafts.total": "合计",
    "drafts.empty": "草稿都去掉了，什么也没记下。",

    // import preview
    "import.note": "重复的记录会跳过；确认之后才会写进账本。",

    // feedback
    "action.feedbackThanks": "记下了，谢谢。",
    "action.sources": "查看引用",

    // sources panel
    "refs.tryAsking": "可以试试：",
    "hint.newline": "换行",
  },
  {
    "state.error": "The ledger didn't load, so the numbers below may be incomplete.",
    "answer.loading": "The ledger is still loading. Ask me again once it's in.",
    "answer.offline": "I can't read the ledger right now, so any figure would be incomplete. Retry, then ask again.",

    "answer.month": "This month so far: {} across {} entries. The biggest slice is {} at {}.",
    "answer.monthNone": "Nothing spent this month yet.",
    "chart.month": "This month",

    "drafts.group": "Entries waiting for your confirmation",
    "drafts.editAria": "Edit {}",
    "drafts.removeAria": "Remove {}",
    "drafts.name": "Merchant",
    "drafts.amount": "Amount",
    "drafts.category": "Category",
    "drafts.date": "Date",
    "drafts.done": "Done",
    "drafts.total": "Total",
    "drafts.empty": "All rows removed — nothing was saved.",

    "import.note": "Duplicates are skipped; nothing is written until you confirm.",

    "action.feedbackThanks": "Noted, thank you.",
    "action.sources": "Show sources",

    "refs.tryAsking": "Try asking:",
    "hint.newline": "newline",
  },
);
