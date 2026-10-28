/** Fills `{}` placeholders in order. The dictionary keeps the sentence, this keeps the values. */
export function fill(template: string, ...values: string[]): string {
  let i = 0;
  return template.replace(/\{\}/g, () => values[i++] ?? "");
}
