import { Hero } from "@/components/home/hero";
import { SeriesShowcase } from "@/components/home/series-showcase";
import { FixturesPreview } from "@/components/home/fixtures-preview";
import { StatsBand } from "@/components/home/stats-band";
import { Experience } from "@/components/home/experience";
import { ClosingCta } from "@/components/home/closing-cta";

export default function HomePage() {
  return (
    <>
      <Hero />
      <SeriesShowcase />
      <FixturesPreview />
      <StatsBand />
      <Experience />
      <ClosingCta />
    </>
  );
}
