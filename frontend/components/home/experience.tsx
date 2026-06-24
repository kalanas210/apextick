import { IMG, unsplash } from "@/data/images";
import { ParallaxImage } from "@/components/ui/parallax-image";
import { Reveal, Stagger, StaggerItem } from "@/components/ui/motion";
import { Button } from "@/components/ui/button";

const MOMENTS = [
  {
    title: "The walk in",
    body: "Gates open two hours early. The concourse hum, the smell of the grass, the first full sight of the stand as you come up the steps.",
  },
  {
    title: "Kickoff",
    body: "Your seat, your sightline, the roar arriving all at once. No buffering, no replay, no second screen. Just the room.",
  },
  {
    title: "Stoppage time",
    body: "The minutes that decide seasons and settle arguments for a decade. You will be able to say you were there for them.",
  },
];

export function Experience() {
  return (
    <section
      id="experience"
      className="relative scroll-mt-24 overflow-hidden border-y border-line"
    >
      <ParallaxImage
        src={unsplash(IMG.playerTunnel, { w: 1800, q: 80 })}
        alt="A player tunnel opening out toward the pitch"
        amount={10}
        sizes="100vw"
        className="absolute inset-0"
      >
        <div className="absolute inset-0 bg-ink/82" />
        <div className="absolute inset-0 bg-gradient-to-r from-ink via-ink/70 to-ink/30" />
      </ParallaxImage>

      <div className="shell relative z-10 grid gap-14 py-28 md:py-40 lg:grid-cols-12">
        <div className="lg:col-span-6">
          <Reveal>
            <span className="kicker">The matchday</span>
            <h2 className="display mt-5 text-[clamp(2.4rem,6vw,5rem)]">
              The room,
              <br />
              <span className="text-accent">not the replay.</span>
            </h2>
            <p className="mt-7 max-w-md text-[1rem] leading-relaxed text-bone/70">
              Television flattens it. Being there does not. ApexTick exists for
              the version of sport you can feel in your chest: the surge when the
              ball turns, the half-second of silence before a wicket, the away end
              that never sits down.
            </p>
            <div className="mt-9">
              <Button href="/events" size="lg" magnetic arrow>
                Find your seat
              </Button>
            </div>
          </Reveal>
        </div>

        <div className="lg:col-span-5 lg:col-start-8">
          <Stagger className="border-y border-line">
            {MOMENTS.map((m, i) => (
              <StaggerItem key={m.title}>
                <div className="flex gap-5 border-b border-line py-6 last:border-b-0">
                  <span className="tnum pt-1 text-sm text-faint">0{i + 1}</span>
                  <div>
                    <h3 className="font-display text-[1.25rem] font-semibold tracking-tight text-bone">
                      {m.title}
                    </h3>
                    <p className="mt-1.5 text-[0.92rem] leading-relaxed text-bone/60">
                      {m.body}
                    </p>
                  </div>
                </div>
              </StaggerItem>
            ))}
          </Stagger>
        </div>
      </div>
    </section>
  );
}
