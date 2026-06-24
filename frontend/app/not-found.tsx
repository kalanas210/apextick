import { Button } from "@/components/ui/button";

export default function NotFound() {
  return (
    <div className="shell flex min-h-[82vh] flex-col items-center justify-center py-32 text-center">
      <span className="kicker">Error 404</span>
      <h1 className="display mt-5 text-[clamp(3rem,13vw,9rem)]">
        Off the pitch.
      </h1>
      <p className="mt-6 max-w-sm text-[0.98rem] leading-relaxed text-muted">
        That page is not on the team sheet. Let us get you back to the fixtures
        that matter.
      </p>
      <div className="mt-10 flex flex-wrap items-center justify-center gap-3">
        <Button href="/" arrow magnetic>
          Back home
        </Button>
        <Button href="/events" variant="outline">
          Browse fixtures
        </Button>
      </div>
    </div>
  );
}
