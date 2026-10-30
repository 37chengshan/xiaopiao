import type { ReactNode } from "react";

/**
 * The exact markdown subset the phone renders (ui/chat/ChatMessageRendering.kt): `**bold**`, `` `code` ``,
 * `- ` bullets, up to `###` headings and fenced code. No HTML, links, images or tables, so a malicious or
 * confused model cannot inject markup. Unbalanced markers during streaming render as plain text instead of
 * flickering half-bold.
 */
const INLINE = /\*\*(.+?)\*\*|`([^`]+)`/g;

function inline(text: string, keyPrefix: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  let at = 0;
  let index = 0;
  for (const match of text.matchAll(INLINE)) {
    const start = match.index ?? 0;
    if (start > at) nodes.push(text.slice(at, start));
    if (match[1] !== undefined) nodes.push(<strong key={`${keyPrefix}-b${index}`} className="font-semibold text-d-ink">{match[1]}</strong>);
    else nodes.push(<code key={`${keyPrefix}-c${index}`} className="rounded bg-d-fill px-1 py-px font-mono text-[0.92em]">{match[2]}</code>);
    at = start + match[0].length;
    index++;
  }
  if (at < text.length) nodes.push(text.slice(at));
  return nodes;
}

/** Provider protocol tags sometimes leak from a model; they are never part of an answer. */
const TOOL_TAG = /<\/?(?:tool_calls?|tool_use|tool_code|function_calls?|function|invoke|parameter)\b[^>]*>\s*/gi;

export function MarkdownText({ text, className }: { text: string; className?: string }) {
  const lines = text.replace(TOOL_TAG, "").split("\n");
  const blocks: ReactNode[] = [];
  let fence: string[] | null = null;
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (line.startsWith("```")) {
      if (fence) {
        blocks.push(
          <pre key={`f${i}`} className="my-1 overflow-x-auto rounded-lg bg-d-fill px-2.5 py-2 font-mono text-[12.5px] leading-[19px] text-d-ink-2">
            {fence.join("\n")}
          </pre>,
        );
        fence = null;
      } else {
        fence = [];
      }
      continue;
    }
    if (fence) { fence.push(line); continue; }
    const heading = /^#{1,3}\s/.test(line);
    const bullet = line.startsWith("- ");
    const body = heading ? line.replace(/^#{1,3}\s+/, "") : bullet ? line.slice(2) : line;
    if (!body.trim()) continue;
    blocks.push(
      <p key={`p${i}`} className={heading ? "mt-1 text-[15px] font-semibold first:mt-0" : bullet ? "pl-3 before:mr-1.5 before:content-['•'] first:mt-0" : "first:mt-0"}>
        {inline(body, `p${i}`)}
      </p>,
    );
  }
  if (fence && fence.length > 0) {
    blocks.push(<pre key="f-tail" className="my-1 overflow-x-auto rounded-lg bg-d-fill px-2.5 py-2 font-mono text-[12.5px]">{fence.join("\n")}</pre>);
  }
  return <div className={className ?? "space-y-1"}>{blocks}</div>;
}
