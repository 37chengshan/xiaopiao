import { IMG } from "../lib/assets";
import { SHELF_DAYS } from "../lib/days";
import { ReceiptPaper } from "../parts/Receipt";
import { scrollToId } from "../parts/Nav";
import { Link } from "@tanstack/react-router";
import { s } from "../strings";

/**
 * Mosaic: the receipts' other half. Big editorial statement on the left, and on the right four staggered
 * columns that loop forever — photos, spend stills, colour blocks and mini receipts, columns running in
 * opposite directions at different speeds. Pure CSS keyframes; hover pauses; reduced motion stills it.
 */
type Tile =
  | { kind: "photo"; src: string; alt: string; tall?: boolean }
  | { kind: "spend"; src: string; label: string }
  | { kind: "block"; tone: "ink" | "accent" | "paper" }
  | { kind: "receipt"; day: number };

const COLUMNS: Tile[][] = [
  [
    { kind: "photo", src: IMG.metroPhone, alt: "f.alt.auto", tall: true },
    { kind: "block", tone: "ink" },
    { kind: "spend", src: "/img/spend-tea.webp", label: "spend.tea" },
    { kind: "photo", src: IMG.receiptStack, alt: "hero.photo.macro" },
    { kind: "receipt", day: 0 },
  ],
  [
    { kind: "photo", src: IMG.friendsTable, alt: "f.alt.circle" },
    { kind: "receipt", day: 3 },
    { kind: "block", tone: "accent" },
    { kind: "spend", src: "/img/spend-lunch.webp", label: "spend.lunch" },
    { kind: "spend", src: "/img/spend-grocery.webp", label: "spend.grocery" },
    { kind: "photo", src: IMG.takeoutNight, alt: "shelf.photo.takeout", tall: true },
  ],
  [
    { kind: "spend", src: "/img/spend-coffee.webp", label: "spend.coffee" },
    { kind: "photo", src: IMG.printerSlot, alt: "f.alt.receipt", tall: true },
    { kind: "block", tone: "paper" },
    { kind: "photo", src: IMG.breakfastQr, alt: "shelf.photo.breakfast" },
    { kind: "receipt", day: 1 },
  ],
  [
    { kind: "photo", src: IMG.shopCounter, alt: "f.alt.shop", tall: true },
    { kind: "block", tone: "ink" },
    { kind: "spend", src: "/img/spend-metro.webp", label: "spend.commute" },
    { kind: "spend", src: "/img/spend-movie.webp", label: "spend.movie" },
    { kind: "photo", src: IMG.handReceipt, alt: "hero.photo.hand" },
    { kind: "receipt", day: 2 },
  ],
];

function TileView({ tile }: { tile: Tile }) {
  if (tile.kind === "photo") {
    return (
      <figure className={`lx-tile lx-tile--photo${tile.tall ? " is-tall" : ""}`}>
        <img src={tile.src} alt={s(tile.alt as Parameters<typeof s>[0])} loading="lazy" decoding="async" />
      </figure>
    );
  }
  if (tile.kind === "spend") {
    return (
      <figure className="lx-tile lx-tile--spend">
        <img src={tile.src} alt={s(tile.label as Parameters<typeof s>[0])} loading="lazy" decoding="async" />
        <figcaption>{s(tile.label as Parameters<typeof s>[0])}</figcaption>
      </figure>
    );
  }
  if (tile.kind === "receipt") {
    return (
      <div className="lx-tile lx-tile--receipt">
        <ReceiptPaper day={SHELF_DAYS[tile.day]} />
      </div>
    );
  }
  return <div className={`lx-tile lx-tile--block is-${tile.tone}`} aria-hidden="true" />;
}

export function Mosaic() {
  return (
    <section id="mosaic" className="lx-mosaic">
      <div className="lx-mosaic__copy">
        <p className="lx-eyebrow">06</p>
        <h2 className="lx-mosaic__heading">{s("mosaic.heading")}</h2>
        <p className="lx-lead">{s("mosaic.lead")}</p>
        <div className="lx-mosaic__actions">
          <Link to="/app" className="lx-pillbtn lx-pillbtn--ink lx-pillbtn--lg" data-cursor="cta">
            {s("mosaic.cta")}
          </Link>
          <button
            type="button"
            className="lx-pillbtn lx-pillbtn--fill lx-pillbtn--lg"
            onClick={() => scrollToId("shelf")}
            data-cursor="cta"
          >
            {s("mosaic.cta2")}
          </button>
        </div>
      </div>
      <div className="lx-mosaic__grid" aria-hidden="true">
        {COLUMNS.map((column, ci) => (
          <div className={`lx-mcol lx-mcol--${ci}`} key={ci}>
            <div className="lx-mcol__track">
              {[...column, ...column].map((tile, ti) => (
                <TileView tile={tile} key={`${ci}-${ti}`} />
              ))}
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
