import { readXlsxRows, rowsToImportText } from "@/logic/biz/xlsx";

/**
 * One file in, one `日期,营收,订单,备注` text out. CSV and TSV are read as text; .xlsx is unpacked
 * in the browser (`logic/biz/xlsx.ts`). Errors are codes the UI turns into a sentence.
 */
export async function readSpreadsheet(file: File): Promise<string> {
  const name = file.name.toLowerCase();
  if (name.endsWith(".xls")) throw new Error("oldXls");
  if (name.endsWith(".xlsx") || name.endsWith(".xlsm")) {
    return rowsToImportText(await readXlsxRows(await file.arrayBuffer()));
  }
  if (name.endsWith(".csv") || name.endsWith(".txt") || name.endsWith(".tsv") || file.type.startsWith("text/")) {
    return file.text();
  }
  throw new Error("notXlsx");
}
