import Link from "next/link";
import Image from "next/image";
import type { ReactNode } from "react";
import { unsplash } from "@/data/images";
import { Logo } from "@/components/site/logo";
import { ArrowUpRight } from "@/components/ui/icons";

interface AuthShellProps {
  kicker: string;
  title: string;
  subtitle: string;
  children: ReactNode;
  image: string;
  alt: string;
  quote: string;
  quoteCaption: string;
}

/**
 * Split-screen auth scaffold: a focused form on the ink canvas paired with a
 * full-bleed graded photograph and an editorial pull quote.
 */
export function AuthShell({
  kicker,
  title,
  subtitle,
  children,
  image,
  alt,
  quote,
  quoteCaption,
}: AuthShellProps) {
  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      {/* Form column */}
      <div className="flex min-h-screen flex-col px-6 py-8 sm:px-10 lg:px-14">
        <div className="flex items-center justify-between">
          <Logo />
          <Link
            href="/"
            className="group inline-flex items-center gap-1.5 font-mono text-[0.66rem] uppercase tracking-[0.18em] text-muted transition-colors hover:text-bone"
          >
            Back to site
            <ArrowUpRight className="h-3.5 w-3.5 transition-transform duration-300 group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
          </Link>
        </div>

        <div className="flex flex-1 items-center py-12">
          <div className="mx-auto w-full max-w-sm">
            <span className="kicker text-accent">{kicker}</span>
            <h1 className="display mt-4 text-[clamp(2.1rem,5vw,2.9rem)]">
              {title}
            </h1>
            <p className="mt-3 text-[0.95rem] leading-relaxed text-muted">
              {subtitle}
            </p>
            <div className="mt-9">{children}</div>
          </div>
        </div>

        <p className="text-center text-[0.7rem] leading-relaxed text-faint">
          Accounts are created and stored by our Keycloak sign-in service. This
          is a demonstration site: checkout runs in test mode.
        </p>
      </div>

      {/* Cinematic column */}
      <div className="relative hidden overflow-hidden lg:block">
        <Image
          src={unsplash(image, { w: 1400, q: 80 })}
          alt={alt}
          fill
          sizes="50vw"
          priority
          className="object-cover"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-ink via-ink/40 to-ink/20" />
        <div className="absolute inset-0 bg-gradient-to-br from-transparent to-ink/60" />
        <div className="absolute inset-0 flex flex-col justify-between p-12">
          <div className="flex justify-end">
            <span className="rounded-full border border-white/15 bg-ink/40 px-3.5 py-1.5 font-mono text-[0.6rem] uppercase tracking-[0.2em] text-bone/80 backdrop-blur-sm">
              Live sport, your seat
            </span>
          </div>
          <div>
            <p className="max-w-md font-display text-[clamp(1.8rem,2.6vw,2.6rem)] font-semibold leading-[1.12] tracking-tight text-bone">
              {quote}
            </p>
            <p className="mt-4 font-mono text-[0.66rem] uppercase tracking-[0.2em] text-bone/55">
              {quoteCaption}
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
