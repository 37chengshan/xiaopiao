import { useLocale } from "@/lib/strings";
import "./landing.css";
import { CursorHalo } from "./cursor/CursorHalo";
import { LandingToaster } from "./components/LandingToast";
import { useHaloEnabled } from "./hooks/useMediaQuery";
import { DayLedger } from "./sections/DayLedger";
import { Features } from "./sections/Features";
import { Footer } from "./sections/Footer";
import { Hero } from "./sections/Hero";
import { Merchant } from "./sections/Merchant";
import { Outro } from "./sections/Outro";
import { Privacy } from "./sections/Privacy";
import { TopNav } from "./sections/TopNav";

/**
 * Landing page composition (docs/WEB-SPEC.md §L). Sections own their own effect; this file only
 * orders them, owns the cursor halo and hides the system pointer when it is active.
 */
export function LandingPage() {
  useLocale();
  const halo = useHaloEnabled();

  return (
    <div
      className="landing-root min-h-svh overflow-x-clip bg-d-canvas text-d-ink antialiased"
    >
      <TopNav />
      <main>
        <Hero />
        <DayLedger />
        <Features />
        <Merchant />
        <Privacy />
        <Outro />
      </main>
      <Footer />
      {halo ? <CursorHalo /> : null}
      <LandingToaster />
    </div>
  );
}
