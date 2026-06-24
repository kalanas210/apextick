import Link from "next/link";
import { Logo } from "./logo";
import { seriesList } from "@/data/events";
import { ArrowUpRight } from "@/components/ui/icons";

const EXPLORE = [
  { href: "/events", label: "All fixtures" },
  { href: "/#series", label: "Series" },
  { href: "/#experience", label: "The experience" },
  { href: "/events/india-pakistan-group-stage/seats", label: "Seat map" },
];

const ACCOUNT = [
  { href: "/signin", label: "Sign in" },
  { href: "/register", label: "Create account" },
  { href: "/forgot-password", label: "Reset password" },
];

const SUPPORT = [
  { href: "#", label: "Help center" },
  { href: "#", label: "Venue guides" },
  { href: "#", label: "Accessibility" },
  { href: "#", label: "Contact" },
];

function Column({
  title,
  links,
}: {
  title: string;
  links: { href: string; label: string }[];
}) {
  return (
    <div>
      <h3 className="kicker mb-5">{title}</h3>
      <ul className="space-y-3">
        {links.map((l) => (
          <li key={l.label}>
            <Link
              href={l.href}
              className="text-[0.92rem] text-muted transition-colors hover:text-bone"
            >
              {l.label}
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}

export function Footer() {
  return (
    <footer className="relative mt-px border-t border-line bg-ink">
      <div className="shell py-16 md:py-24">
        <div className="grid gap-12 lg:grid-cols-12">
          <div className="lg:col-span-4">
            <Logo />
            <p className="mt-6 max-w-xs text-[0.95rem] leading-relaxed text-muted">
              Real-time tickets for the biggest nights in world sport. Pick your
              seat, feel the room, never miss the moment.
            </p>
            <div className="mt-7 flex items-center gap-2.5">
              <span className="rounded-full border border-line-2 px-3 py-1 font-mono text-[0.62rem] uppercase tracking-[0.18em] text-faint">
                Live since 2019
              </span>
              <span className="rounded-full border border-line-2 px-3 py-1 font-mono text-[0.62rem] uppercase tracking-[0.18em] text-faint">
                12 nations
              </span>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-10 sm:grid-cols-4 lg:col-span-8">
            <div>
              <h3 className="kicker mb-5">Series</h3>
              <ul className="space-y-3">
                {seriesList.map((s) => (
                  <li key={s.id}>
                    <Link
                      href={`/events?series=${s.id}`}
                      className="text-[0.92rem] text-muted transition-colors hover:text-bone"
                    >
                      {s.shortName}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
            <Column title="Explore" links={EXPLORE} />
            <Column title="Account" links={ACCOUNT} />
            <Column title="Support" links={SUPPORT} />
          </div>
        </div>

        <div className="mt-16 flex flex-col gap-4 border-t border-line pt-8 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-[0.8rem] text-faint">
            2026 ApexTick. A demonstration experience. Fixtures, prices, and seats
            are sample data.
          </p>
          <a
            href="#top"
            className="group inline-flex items-center gap-1.5 font-mono text-[0.7rem] uppercase tracking-[0.18em] text-muted transition-colors hover:text-bone"
          >
            Back to top
            <ArrowUpRight className="h-3.5 w-3.5 transition-transform duration-300 group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
          </a>
        </div>
      </div>

      <div
        aria-hidden
        className="select-none overflow-hidden border-t border-line px-[clamp(1.25rem,5vw,4rem)] pb-6 pt-10"
      >
        <div className="font-display text-[clamp(3.5rem,18vw,17rem)] font-bold leading-[0.78] tracking-[-0.04em] text-bone/[0.05]">
          APEX<span className="text-accent/[0.14]">TICK</span>
        </div>
      </div>
    </footer>
  );
}
