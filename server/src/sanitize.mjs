/**
 * Some free models emit their tool-call syntax as answer text (`<tool_call>{…}</tool_call>`, `<function=…>`).
 * The UI must never show provider protocol details, so the whole block — tag and payload — is stripped while
 * streaming, including blocks split across chunk boundaries. Ordinary text such as `a<b` or `<3` is untouched.
 */
const NAMES = 'tool_calls?|tool_use|tool_code|function_calls?|function|invoke|parameter';
const BLOCK = new RegExp(`<(${NAMES})\\b[^>]*>[\\s\\S]*?<\\/(?:${NAMES})\\s*>`, 'gi');
const STRAY = new RegExp(`<\\/?(?:${NAMES})\\b[^>]*>`, 'gi');
const OPEN = new RegExp(`<(${NAMES})\\b[^>]*>`, 'i');
const CLOSE = new RegExp(`<\\/(?:${NAMES})\\s*>`, 'i');
const NAME_LIST = ['tool_call', 'tool_calls', 'tool_use', 'tool_code', 'function_call', 'function_calls', 'function', 'invoke', 'parameter'];
/** True when the tail is the beginning of a tag we strip, so it must be held instead of shown. */
function isPartialTag(tail) {
  const match = /^<\/?([a-z_]*)$/i.exec(tail);
  if (!match) return false;
  const name = match[1].toLowerCase();
  return NAME_LIST.some(candidate => candidate.startsWith(name));
}

export function stripToolMarkup(text) {
  return text.replace(BLOCK, '').replace(STRAY, '');
}

export function createStreamSanitizer() {
  let buffer = '';
  let inside = false;
  return {
    push(chunk) {
      if (!chunk) return '';
      buffer += chunk;
      let output = '';
      for (;;) {
        if (inside) {
          const end = CLOSE.exec(buffer);
          if (!end) { buffer = ''; return output; }
          buffer = buffer.slice(end.index + end[0].length);
          inside = false;
          continue;
        }
        const open = OPEN.exec(buffer);
        if (!open) break;
        output += buffer.slice(0, open.index);
        buffer = buffer.slice(open.index + open[0].length);
        inside = true;
      }
      // Hold a half-written tag so it cannot appear for one frame and then vanish.
      const lt = buffer.lastIndexOf('<');
      if (lt >= 0 && !buffer.slice(lt).includes('>') && isPartialTag(buffer.slice(lt))) {
        output += buffer.slice(0, lt);
        buffer = buffer.slice(lt);
        return output;
      }
      output += buffer;
      buffer = '';
      return output;
    },
    /** End of stream: a dangling half tag is protocol noise and is dropped. */
    finish() {
      const rest = inside || isPartialTag(buffer) ? '' : buffer;
      buffer = '';
      inside = false;
      return rest;
    },
  };
}
