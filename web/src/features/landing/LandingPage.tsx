import { useLocale } from "@/lib/strings";
import "./landing.css";
import { CursorHalo } from "./cursor/CursorHalo";
import { useHaloEnabled } from "./hooks/useMediaQuery";
import { AiFirst } from "./sections/AiFirst";
import { Archive } from "./sections/Archive";
import { Bento } from "./sections/Bento";
import { Demo } from "./sections/Demo";
import { Footer } from "./sections/Footer";
import { Hero } from "./sections/Hero";
import { Outro } from "./sections/Outro";
import { Privacy } from "./sections/Privacy";

/**
 * Landing v2 (docs/WEB-SPEC.md §L, tt 2026-10-28): the whole page is one receipt being printed.
 * The hero owns the nav because the nav is part of its opening timeline.
 */
export function LandingPage() {
  useLocale();
  const halo = useHaloEnabled();

  return (
    <div className="lp min-h-svh overflow-x-clip antialiased">
      <main>
        <Hero />
        <Demo />
        <Archive />
        <Bento />
        <AiFirst />
        <Privacy />
        <Outro />
      </main>
      <Footer />
      {halo ? <CursorHalo /> : null}
    </div>
  );
}
