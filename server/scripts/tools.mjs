/**
 * The README tool table is generated, not typed: `server/README.md` holds it between two markers and this script
 * rewrites that block from `src/tools.mjs`. A test asserts the two agree, so a tool cannot exist in the registry and
 * be missing from the README (or vice versa).
 *
 *   node scripts/tools.mjs           # print the block
 *   node scripts/tools.mjs --write   # rewrite it inside server/README.md
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { renderToolDoc } from '../src/tools.mjs';

export const BEGIN = '<!-- tools:begin -->';
export const END = '<!-- tools:end -->';
export const readmePath = () => join(dirname(fileURLToPath(import.meta.url)), '..', 'README.md');
export const block = body => `${BEGIN}\n${body}\n${END}`;
/** The block currently in the README, or null when the markers are gone. */
export function currentBlock(readme) {
  const start = readme.indexOf(BEGIN);
  const end = readme.indexOf(END);
  if (start < 0 || end < start) return null;
  return readme.slice(start, end + END.length);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  if (process.argv.includes('--write')) {
    const path = readmePath();
    const readme = readFileSync(path, 'utf8');
    const current = currentBlock(readme);
    if (!current) {
      console.error(`README is missing ${BEGIN} / ${END}`);
      process.exit(1);
    }
    writeFileSync(path, readme.replace(current, block(renderToolDoc())));
    console.log('README tool table rewritten');
  } else {
    console.log(block(renderToolDoc()));
  }
}
