import { useMotionValue } from "framer-motion";
import { useLocale } from "@/lib/strings";
import "./landing.css";
import { CursorHalo } from "./cursor/CursorHalo";
import { useHaloEnabled } from "./hooks/useMediaQuery";
import { Nav } from "./parts/Nav";
import { PillHero } from "./sections/PillHero";
import { PhoneLaunch } from "./sections/PhoneLaunch";
import { Bento } from "./sections/Bento";
import { Accordion } from "./sections/Accordion";
import { Shelf } from "./sections/Shelf";
import { Mosaic } from "./sections/Mosaic";
import { Privacy } from "./sections/Privacy";
import { Closing } from "./sections/Closing";

/**
 * Landing v4 (tt 2026-10-29): a launch page rather than a poster. One argument in order —
 * open with the day (scroll-driven capsule), show the phone that lives on it (3D app screens),
 * let the interface move (bento), explain the five things (accordion), prove the archive (3D shelf),
 * then the wall, the defaults, the questions, and the way in.
 */
export function LandingPage() {
  useLocale();
  const halo = useHaloEnabled();
  const heroProgress = useMotionValue(0);

  return (
    <div className="lx">
      <Nav progress={heroProgress} />
      <main>
        <PillHero progress={heroProgress} />
        <PhoneLaunch />
        <Bento />
        <Accordion />
        <Shelf />
        <Mosaic />
        <Privacy />
        <Closing />
      </main>
      {halo ? <CursorHalo /> : null}
    </div>
  );
}
