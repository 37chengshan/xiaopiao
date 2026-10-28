/**
 * Copy dictionary for new UI text (2026-10-28). zh is the source; en is filled for the bilingual pass.
 * Add new strings here instead of hard-coding them in components.
 */
export type Locale = "zh" | "en";

const zh = {
  "nav.ledger": "账本",
  "nav.insights": "洞察",
  "nav.chat": "记账",
  "nav.receipt": "小票",
  "nav.me": "我的",
  "nav.aria": "主导航",
  "me.title": "我的",
  "me.appearance": "外观",
  "me.sound": "打印音效",
  "me.haptics": "震动反馈",
  "me.mode": "数据模式",
  "me.mode.demo": "演示数据",
  "me.mode.empty": "自己记",
  "me.mode.none": "未选择",
  "me.switchMode": "重新选择模式",
  "me.clear": "清空所有记录",
  "me.privacy": "隐私与数据",
  "me.merchant": "切换到小店版",
  "me.guide": "新手引导",
  "me.about": "关于",
  "me.more": "了解更多",
  "me.version": "版本",
  "back.personal": "返回个人版",
  "ob.skip": "跳过",
  "ob.next": "下一步",
  "ob.done": "完成",
  "ob.1.title": "每天一张小票",
  "ob.1.body": "不用天天记账，睡前领一张。",
  "ob.2.title": "自动记，不打扰",
  "ob.2.body": "读支付通知，只在手机里解析。",
  "ob.3.title": "原文不出手机",
  "ob.3.body": "云端只存结构化字段。",
  "ob.3.b1": "账单原文只在本机",
  "ob.3.b2": "数据贡献默认关闭",
  "ob.3.b3": "每笔都由你确认",
  "ob.4.title": "按你的喜好",
  "ob.4.body": "外观和声音，随时可改。",
  "ob.start.demo": "看看演示",
  "ob.start.empty": "从零开始",
  "chat.placeholder": "说点什么，如：午饭 32",
  "chat.listening": "正在听，请说话",
} as const;

export type StringKey = keyof typeof zh;

const en: Record<StringKey, string> = {
  "nav.ledger": "Ledger",
  "nav.insights": "Insights",
  "nav.chat": "Log",
  "nav.receipt": "Receipt",
  "nav.me": "Me",
  "nav.aria": "Main navigation",
  "me.title": "Me",
  "me.appearance": "Appearance",
  "me.sound": "Print sound",
  "me.haptics": "Haptics",
  "me.mode": "Data mode",
  "me.mode.demo": "Demo data",
  "me.mode.empty": "My own",
  "me.mode.none": "Not chosen",
  "me.switchMode": "Choose mode again",
  "me.clear": "Clear all records",
  "me.privacy": "Privacy & data",
  "me.merchant": "Switch to Shop view",
  "me.guide": "Intro tour",
  "me.about": "About",
  "me.more": "Learn more",
  "me.version": "Version",
  "back.personal": "Back to personal",
  "ob.skip": "Skip",
  "ob.next": "Next",
  "ob.done": "Done",
  "ob.1.title": "One receipt a day",
  "ob.1.body": "No daily bookkeeping. Collect one at bedtime.",
  "ob.2.title": "Logs itself, quietly",
  "ob.2.body": "Reads payment notices, parsed on your phone only.",
  "ob.3.title": "Raw text stays put",
  "ob.3.body": "The cloud only stores structured fields.",
  "ob.3.b1": "Statements stay on device",
  "ob.3.b2": "Data sharing is off by default",
  "ob.3.b3": "You confirm every entry",
  "ob.4.title": "Make it yours",
  "ob.4.body": "Look and sound, change anytime.",
  "ob.start.demo": "Try the demo",
  "ob.start.empty": "Start fresh",
  "chat.placeholder": "Say something, e.g. lunch 32",
  "chat.listening": "Listening...",
};

const dicts: Record<Locale, Record<StringKey, string>> = { zh, en };
let locale: Locale = "zh";

export function setLocale(l: Locale) {
  locale = l;
}
export function t(key: StringKey): string {
  return dicts[locale][key];
}
