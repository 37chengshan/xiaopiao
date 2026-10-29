import { defineStrings } from "@/lib/strings";

/**
 * Copy for the desktop ledger page (`/app/ledger`). zh is the source; en is written natively.
 * The shared shell dictionary stays untouched by this page's one-off strings.
 */
export const s = defineStrings(
  {
    "time.custom": "自定义",

    "export.scope": "导出范围",
    "export.shown": "只导出当前 {} 项",
    "export.all": "导出全部 {} 项",
    "export.doneShown": "已导出当前筛选的 {} 项",
    "export.doneAll": "已导出全部 {} 项",

    "import.ofTotal": "预览前 {} 条，共 {} 条",
    "import.skipped": "{} 行没有读进来",
    "import.skippedRest": "还有 {} 行同样跳过",
    "import.readFail": "这个文件读不出来，换一份 CSV 试试",

    "hint.edit": "点一行编辑 · Delete 删除",
  },
  {
    "time.custom": "Custom",

    "export.scope": "Export range",
    "export.shown": "Only the {} shown",
    "export.all": "All {} entries",
    "export.doneShown": "Exported the {} matching entries",
    "export.doneAll": "Exported {} entries",

    "import.ofTotal": "First {} of {} rows",
    "import.skipped": "{} rows skipped",
    "import.skippedRest": "{} more rows skipped for the same reason",
    "import.readFail": "Could not read that file — try another CSV",

    "hint.edit": "Click a row to edit · Delete removes",
  },
);

/** Fills `{}` placeholders in order, the same convention the shell's copy uses. */
export function fill(template: string, ...values: (string | number)[]): string {
  let i = 0;
  return template.replace(/\{\}/g, () => String(values[i++] ?? ""));
}
