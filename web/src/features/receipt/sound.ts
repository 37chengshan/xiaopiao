/**
 * Synthesised printer sounds (docs/DESIGN.md §7.7): no audio files, no network.
 * - printTick: a short band-passed noise burst per printed line, with a little pitch jitter, plus a faint stepper-motor knock.
 * - stampThump: a dull low thud.
 * - tearRip: a quick rising rasp.
 * The context must be created/resumed inside the user gesture that starts printing (`unlockAudio`).
 * Everything is quiet by default and silent when the user turned receipt sound off.
 */
import { isReceiptSoundOn } from "./soundPref";

const MASTER_GAIN = 0.45;

let ctx: AudioContext | null = null;
let master: GainNode | null = null;
let noise: AudioBuffer | null = null;

export interface BurstParams {
  freq: number;
  q: number;
  duration: number;
  gain: number;
}

/** Deterministic given `rand`; jitter keeps consecutive ticks from sounding machine-gunned. */
export function burstParams(rand: () => number): BurstParams {
  return {
    freq: 2600 * (0.86 + rand() * 0.3),
    q: 1.1 + rand() * 0.5,
    duration: 0.026 + rand() * 0.02,
    gain: 0.1 * (0.8 + rand() * 0.4),
  };
}

function getCtx(): AudioContext | null {
  if (ctx) return ctx;
  const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!Ctor) return null;
  try {
    ctx = new Ctor();
    master = ctx.createGain();
    master.gain.value = MASTER_GAIN;
    master.connect(ctx.destination);
  } catch {
    ctx = null;
  }
  return ctx;
}

/** Call synchronously from the tap that starts printing; browsers only allow audio after a gesture. */
export function unlockAudio(): void {
  if (!isReceiptSoundOn()) return;
  const c = getCtx();
  if (c && c.state === "suspended") void c.resume().catch(() => undefined);
}

function ready(): AudioContext | null {
  if (!isReceiptSoundOn() || !ctx || !master || ctx.state !== "running") return null;
  return ctx;
}

function noiseBuffer(c: AudioContext): AudioBuffer {
  if (noise) return noise;
  noise = c.createBuffer(1, c.sampleRate, c.sampleRate);
  const d = noise.getChannelData(0);
  for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
  return noise;
}

function envelope(g: GainNode, t: number, peak: number, attack: number, end: number) {
  g.gain.setValueAtTime(0.0001, t);
  g.gain.linearRampToValueAtTime(peak, t + attack);
  g.gain.exponentialRampToValueAtTime(0.0001, t + end);
}

export function printTick(): void {
  const c = ready();
  if (!c || !master) return;
  const t = c.currentTime;
  const p = burstParams(Math.random);
  const src = c.createBufferSource();
  src.buffer = noiseBuffer(c);
  src.loop = true;
  const bp = c.createBiquadFilter();
  bp.type = "bandpass";
  bp.frequency.value = p.freq;
  bp.Q.value = p.q;
  const g = c.createGain();
  envelope(g, t, p.gain, 0.004, p.duration);
  src.connect(bp).connect(g).connect(master);
  src.start(t, Math.random() * 0.8);
  src.stop(t + p.duration + 0.02);

  const knock = c.createOscillator();
  knock.type = "triangle";
  knock.frequency.value = 95 + Math.random() * 20;
  const kg = c.createGain();
  envelope(kg, t, 0.05, 0.003, 0.03);
  knock.connect(kg).connect(master);
  knock.start(t);
  knock.stop(t + 0.04);
}

export function stampThump(): void {
  const c = ready();
  if (!c || !master) return;
  const t = c.currentTime;
  const o = c.createOscillator();
  o.type = "sine";
  o.frequency.setValueAtTime(130, t);
  o.frequency.exponentialRampToValueAtTime(42, t + 0.14);
  const g = c.createGain();
  envelope(g, t, 0.55, 0.004, 0.2);
  o.connect(g).connect(master);
  o.start(t);
  o.stop(t + 0.24);

  const src = c.createBufferSource();
  src.buffer = noiseBuffer(c);
  const lp = c.createBiquadFilter();
  lp.type = "lowpass";
  lp.frequency.value = 520;
  const ng = c.createGain();
  envelope(ng, t, 0.22, 0.002, 0.05);
  src.connect(lp).connect(ng).connect(master);
  src.start(t);
  src.stop(t + 0.08);
}

export function tearRip(): void {
  const c = ready();
  if (!c || !master) return;
  const t = c.currentTime;
  const src = c.createBufferSource();
  src.buffer = noiseBuffer(c);
  src.loop = true;
  const bp = c.createBiquadFilter();
  bp.type = "bandpass";
  bp.Q.value = 0.9;
  bp.frequency.setValueAtTime(1500, t);
  bp.frequency.exponentialRampToValueAtTime(4200, t + 0.16);
  const g = c.createGain();
  envelope(g, t, 0.12, 0.01, 0.18);
  // rapid amplitude flutter so it reads as paper fibres, not a whoosh
  const lfo = c.createOscillator();
  lfo.frequency.value = 55;
  const depth = c.createGain();
  depth.gain.value = 0.05;
  lfo.connect(depth).connect(g.gain);
  src.connect(bp).connect(g).connect(master);
  src.start(t);
  lfo.start(t);
  src.stop(t + 0.2);
  lfo.stop(t + 0.2);
}
