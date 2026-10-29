import { useInViewOnce, SplitText, Reveal } from "../lib/motion";
import { useLocale } from "@/lib/strings";
import { s } from "../strings";
import { landingDate } from "../format";
import { IMG } from "../lib/assets";
import { MiniReceipt, type ReceiptRow } from "../parts/MiniReceipt";

type Tile =
  | { kind: "photo"; src: string; alt: string }
  | { kind: "block"; tone: "orange" | "yellow" | "ink"; label: string }
  | { kind: "receipt"; date: string; rows: ReceiptRow[]; total: string; stamp: string };

function photo(src: string, alt: string): Tile {
  return { kind: "photo", src, alt };
}

/**
 * L3 — the receipt archive (atrium-mtl-archive): big two-line headline on the left, and a three-column
 * endless wall on the right (adjacent columns run opposite ways, 14–16s). Tiles mix generated photos,
 * flat brand-colour blocks with 13% paper grain, and real HTML receipt samples.
 */
export function Archive() {
  const { ref, inView } = useInViewOnce(0.16);
  useLocale();

  const teaReceipt: Tile = {
    kind: "receipt",
    date: landingDate(2026, 10, 21),
    rows: [
      { label: "霸王茶姬 · 伯牙", amount: "18.00" },
      { label: "瑞幸 · 生椰", amount: "13.90" },
      { label: "蜜雪 · 柠檬水", amount: "6.00" },
    ],
    total: "37.90",
    stamp: s("archive.stamp.tea"),
  };
  const metroReceipt: Tile = {
    kind: "receipt",
    date: landingDate(2026, 10, 14),
    rows: [
      { label: "地铁 · 早高峰", amount: "5.00" },
      { label: "地铁 · 晚高峰", amount: "5.00" },
      { label: "共享单车", amount: "1.50" },
    ],
    total: "11.50",
    stamp: s("archive.stamp.metro"),
  };
  const nightReceipt: Tile = {
    kind: "receipt",
    date: landingDate(2026, 10, 25),
    rows: [
      { label: "烧烤 · 夜宵", amount: "58.00" },
      { label: "便利店 · 水", amount: "4.00" },
    ],
    total: "62.00",
    stamp: s("archive.stamp.night"),
  };

  const columns: { tiles: Tile[]; dir: "down" | "up"; duration: string }[] = [
    {
      dir: "down",
      duration: "15s",
      tiles: [
        photo(IMG.handReceipt, "hand holding a receipt"),
        { kind: "block", tone: "orange", label: s("archive.block.orange") },
        teaReceipt,
        photo(IMG.receiptStack, "stack of receipts"),
        { kind: "block", tone: "yellow", label: s("archive.block.yellow") },
      ],
    },
    {
      dir: "up",
      duration: "16s",
      tiles: [
        { kind: "block", tone: "ink", label: s("archive.block.ink") },
        photo(IMG.takeoutNight, "takeout at night"),
        metroReceipt,
        { kind: "block", tone: "yellow", label: s("archive.block.yellow") },
        photo(IMG.metroPhone, "phone at the metro"),
      ],
    },
    {
      dir: "down",
      duration: "14s",
      tiles: [
        photo(IMG.metroPhone, "phone at the metro"),
        nightReceipt,
        { kind: "block", tone: "orange", label: s("archive.block.orange") },
        photo(IMG.takeoutNight, "takeout at night"),
        photo(IMG.receiptStack, "stack of receipts"),
      ],
    },
  ];

  const renderTile = (tile: Tile, key: string) => {
    if (tile.kind === "photo") {
      return (
        <div key={key} className="lp-tile">
          <img src={tile.src} alt={tile.alt} loading="lazy" />
        </div>
      );
    }
    if (tile.kind === "receipt") {
      return (
        <div key={key} className="lp-tile lp-tile--receipt">
          <MiniReceipt large date={tile.date} rows={tile.rows} total={tile.total} stamp={tile.stamp} className="!bg-transparent !p-0 !shadow-none" />
          <div aria-hidden className="lp-barcode" />
        </div>
      );
    }
    const bg =
      tile.tone === "orange"
        ? "linear-gradient(160deg,#FF9A2E,#F07400)"
        : tile.tone === "yellow"
          ? "linear-gradient(160deg,#FFD45E,#FFC93C)"
          : "linear-gradient(160deg,#232019,#0F0E0C)";
    const fg = tile.tone === "ink" ? "#F6F0E7" : "#3A2A12";
    return (
      <div key={key} className="lp-block lp-grain" style={{ background: bg, color: fg }}>
        <span>{tile.label}</span>
      </div>
    );
  };

  return (
    <section id="archive" ref={ref} className="relative mx-auto w-full max-w-[1320px] px-4 py-24 sm:px-6 lg:py-32">
      <div className="lp-archive-grid">
        <div className="max-w-[520px]">
          <p className="lp-card-kicker">{s("archive.eyebrow")}</p>
          <h2 className="mt-3 text-[clamp(34px,4.6vw,64px)] font-semibold leading-[1.02] tracking-[-0.035em]">
            <SplitText as="span" text={s("archive.title.a")} active={inView} />
            <SplitText as="span" text={s("archive.title.b")} active={inView} base={90} index={4} />
          </h2>
          <Reveal delay={2}>
            <p className="mt-6 max-w-[44ch] text-[16px] leading-[1.8] text-[var(--lp-ink-2)]">{s("archive.body")}</p>
          </Reveal>
          <Reveal delay={3}>
            <p className="mt-5 border-l-2 border-[var(--lp-orange)] pl-3 text-[13px] leading-[1.7] text-[var(--lp-ink-2)]">{s("archive.note")}</p>
          </Reveal>
        </div>

        <div className="lp-walls" aria-hidden>
          {columns.map((col, i) => (
            <div
              key={i}
              className={`lp-col ${col.dir === "up" ? "lp-col--up" : ""} ${i === 1 ? "lp-col--mid" : ""}`}
              style={{ animationDuration: col.duration }}
            >
              {col.tiles.map((tile, j) => renderTile(tile, `a${i}${j}`))}
              {col.tiles.map((tile, j) => renderTile(tile, `b${i}${j}`))}
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
