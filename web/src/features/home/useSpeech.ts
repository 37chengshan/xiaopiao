import { useCallback, useEffect, useRef, useState } from "react";

interface RecognitionResultLike {
  isFinal: boolean;
  0: { transcript: string };
}
interface RecognitionEventLike {
  resultIndex: number;
  results: ArrayLike<RecognitionResultLike>;
}
interface RecognitionLike {
  lang: string;
  interimResults: boolean;
  continuous: boolean;
  onresult: ((e: RecognitionEventLike) => void) | null;
  onerror: ((e: { error: string }) => void) | null;
  onend: (() => void) | null;
  start(): void;
  stop(): void;
  abort(): void;
}
type RecognitionCtor = new () => RecognitionLike;

function getCtor(): RecognitionCtor | null {
  if (typeof window === "undefined") return null;
  const w = window as unknown as { SpeechRecognition?: RecognitionCtor; webkitSpeechRecognition?: RecognitionCtor };
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null;
}

/** Web Speech API wrapper (zh-CN). `supported` is false on browsers without it, e.g. Firefox. */
export function useSpeech(onInterim: (text: string) => void, onFinal: (text: string) => void, onError: (message: string) => void) {
  const [listening, setListening] = useState(false);
  const recRef = useRef<RecognitionLike | null>(null);
  const cbs = useRef({ onInterim, onFinal, onError });
  cbs.current = { onInterim, onFinal, onError };
  const supported = getCtor() !== null;

  const stop = useCallback(() => recRef.current?.stop(), []);
  const start = useCallback(() => {
    const Ctor = getCtor();
    if (!Ctor) {
      cbs.current.onError("unsupported");
      return;
    }
    const rec = new Ctor();
    rec.lang = "zh-CN";
    rec.interimResults = true;
    rec.continuous = false;
    let finalText = "";
    rec.onresult = (e) => {
      let interim = "";
      for (let i = e.resultIndex; i < e.results.length; i++) {
        const r = e.results[i];
        if (r.isFinal) finalText += r[0].transcript;
        else interim += r[0].transcript;
      }
      cbs.current.onInterim(finalText + interim);
    };
    rec.onerror = (e) => cbs.current.onError(e.error);
    rec.onend = () => {
      setListening(false);
      recRef.current = null;
      if (finalText.trim()) cbs.current.onFinal(finalText.trim());
    };
    recRef.current = rec;
    try {
      rec.start();
      setListening(true);
    } catch {
      cbs.current.onError("start-failed");
    }
  }, []);

  useEffect(() => () => recRef.current?.abort(), []);
  return { supported, listening, start, stop };
}
