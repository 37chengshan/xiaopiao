import { defineStrings } from "@/lib/strings";

/** Copy for the cards page. zh is the source; en is written natively. `{bank}` / `{tail}` are filled by the page. */
export const c = defineStrings(
  {
    "cards.lift": "把 {bank} •••• {tail} 移到前面",
    "cards.monthSpend": "本月这张卡",
    "cards.monthEmpty": "这张卡本月还没有记录",
    "cards.setDefault": "设为默认",
  },
  {
    "cards.lift": "Bring {bank} •••• {tail} to the front",
    "cards.monthSpend": "On this card this month",
    "cards.monthEmpty": "Nothing on this card this month",
    "cards.setDefault": "Set as default",
  },
);
