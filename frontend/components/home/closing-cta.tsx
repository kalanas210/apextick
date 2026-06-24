import { Reveal } from "@/components/ui/motion";
import { Button } from "@/components/ui/button";

export function ClosingCta() {
  return (
    <section className="shell py-28 md:py-40">
      <Reveal>
        <div className="border-t border-line pt-12">
          <span className="kicker">Ready when you are</span>
          <h2 className="display mt-6 max-w-5xl text-[clamp(2.6rem,8vw,7rem)]">
            Pick the night.{" "}
            <span className="text-muted">We will hold the seat.</span>
          </h2>
          <div className="mt-10 flex flex-wrap items-center gap-4">
            <Button href="/events" size="lg" magnetic arrow>
              Browse fixtures
            </Button>
            <Button href="/#series" size="lg" variant="outline">
              See the series
            </Button>
            <span className="tnum ml-1 text-sm text-faint">
              16 fixtures live now
            </span>
          </div>
        </div>
      </Reveal>
    </section>
  );
}
