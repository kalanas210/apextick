"use client";

import { useEffect, useRef, useState, type CSSProperties } from "react";
import Image from "next/image";
import { AnimatePresence, motion, useReducedMotion } from "framer-motion";
import { seriesList } from "@/data/events";
import { unsplash } from "@/data/images";
import { Button } from "@/components/ui/button";
import { Scroll } from "@/components/ui/icons";

const DURATION = 5600; // ms each segment holds before advancing
const FADE = 1.2; // s cross-dissolve
const EASE = [0.16, 1, 0.3, 1] as const;

const SEGMENTS = [
  {
    series: seriesList[0], // ICC T20 World Cup
    title: ["Every nation,", "one trophy."],
    meta: "Final, 8 Mar 2026, Ahmedabad",
  },
  {
    series: seriesList[2], // Premier League
    title: ["Ninety minutes,", "full voice."],
    meta: "Every weekend, England",
  },
  {
    series: seriesList[3], // FIFA World Cup
    title: ["The world's game,", "biggest stage."],
    meta: "Jun to Jul 2026, North America",
  },
  {
    series: seriesList[1], // Indian Premier League
    title: ["Ten cities,", "one obsession."],
    meta: "Apr to May 2026, across India",
  },
] as const;

function Scrim() {
  return (
    <>
      <div className="absolute inset-0 bg-gradient-to-t from-ink via-ink/35 to-ink/20" />
      <div className="absolute inset-0 bg-gradient-to-r from-ink/80 via-ink/25 to-transparent" />
      <div className="absolute inset-0 bg-[radial-gradient(125%_85%_at_50%_-10%,transparent_45%,rgba(11,11,12,0.6))]" />
    </>
  );
}

export function Hero() {
  const reduce = useReducedMotion();
  const [active, setActive] = useState(0);
  const [paused, setPaused] = useState(false);

  const pausedRef = useRef(false);
  const elapsedRef = useRef(0);

  useEffect(() => {
    pausedRef.current = paused;
  }, [paused]);

  // Auto-advance, driven by a single rAF loop so the progress bar can pause
  // mid-fill. Skipped entirely under reduced motion (manual tabs instead).
  useEffect(() => {
    if (reduce) return;
    let raf = 0;
    let last = performance.now();
    const tick = (now: number) => {
      const dt = now - last;
      last = now;
      if (!pausedRef.current && !document.hidden) {
        elapsedRef.current += dt;
        if (elapsedRef.current >= DURATION) {
          elapsedRef.current = 0;
          setActive((a) => (a + 1) % SEGMENTS.length);
        }
      }
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [reduce]);

  const seg = SEGMENTS[active];
  const tint = seg.series.tint;

  return (
    <section
      aria-roledescription="carousel"
      aria-label="Featured series"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocusCapture={() => setPaused(true)}
      onBlurCapture={() => setPaused(false)}
      className="relative h-svh min-h-[640px] overflow-hidden"
      style={{ "--tint": tint } as CSSProperties}
    >
      {/* Image stack, video-like Ken Burns push on the active frame */}
      {SEGMENTS.map((s, i) => {
        const isActive = i === active;
        const dir = i % 2 === 0 ? "2%" : "-2%";
        return (
          <motion.div
            key={s.series.id}
            className="absolute inset-0"
            animate={{ opacity: isActive ? 1 : 0 }}
            transition={{ duration: reduce ? 0 : FADE, ease: "easeInOut" }}
          >
            <motion.div
              className="relative h-full w-full will-change-transform"
              animate={
                reduce
                  ? { scale: 1.03 }
                  : {
                      scale: isActive ? 1.13 : 1.03,
                      x: isActive ? dir : "0%",
                      y: isActive ? "-1.5%" : "0%",
                    }
              }
              transition={
                reduce
                  ? { duration: 0 }
                  : {
                      duration: isActive ? (DURATION + 1600) / 1000 : 0.9,
                      ease: isActive ? "linear" : "easeOut",
                    }
              }
            >
              {/* Desktop Image */}
              <Image
                src={unsplash(s.series.image, { w: 2200, q: 80 })}
                alt={`${s.series.name} atmosphere`}
                fill
                priority={i === 0}
                sizes="100vw"
                className={`object-cover ${s.series.mobileHeroImage ? "hidden sm:block" : ""}`}
              />
              {/* Mobile Image */}
              {s.series.mobileHeroImage && (
                <Image
                  src={s.series.mobileHeroImage}
                  alt={`${s.series.name} atmosphere`}
                  fill
                  priority={i === 0}
                  sizes="100vw"
                  className="object-cover sm:hidden"
                />
              )}
            </motion.div>
          </motion.div>
        );
      })}

      <Scrim />

      {/* Vertical edge label */}
      <div className="pointer-events-none absolute right-[clamp(1rem,4vw,3rem)] top-1/2 hidden -translate-y-1/2 lg:block">
        <AnimatePresence mode="wait">
          <motion.span
            key={seg.series.id}
            initial={{ opacity: 0, y: 14 }}
            animate={{ opacity: 0.7, y: 0 }}
            exit={{ opacity: 0, y: -14 }}
            transition={{ duration: 0.5 }}
            className="block font-mono text-[0.65rem] uppercase tracking-[0.35em] text-bone/70 [writing-mode:vertical-rl]"
          >
            {seg.series.cities[0]} / {seg.series.scale}
          </motion.span>
        </AnimatePresence>
      </div>

      {/* Foreground */}
      <div className="shell relative z-10 flex h-full flex-col justify-between pb-20 pt-20 md:pb-32 md:pt-[6.5rem]">
        <h1 className="sr-only">
          ApexTick, live tickets for the ICC T20 World Cup 2026, the Indian
          Premier League, the Premier League, and the FIFA World Cup
        </h1>

        {/* Top label */}
        <div className="flex items-center gap-4">
          <span className="tnum text-xs text-bone/75">
            0{active + 1}
            <span className="text-faint"> / 0{SEGMENTS.length}</span>
          </span>
          <span className="h-px w-8 bg-bone/20" />
          <AnimatePresence mode="wait">
            <motion.span
              key={seg.series.id}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              transition={{ duration: 0.4 }}
              className="kicker text-tint"
            >
              {seg.series.name}
            </motion.span>
          </AnimatePresence>
        </div>

        {/* Headline + controls */}
        <div>
          <div className="max-w-4xl">
            <AnimatePresence mode="wait">
              <motion.div
                key={active}
                initial={{ opacity: reduce ? 1 : 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: reduce ? 1 : 0, filter: "blur(8px)" }}
                transition={{ duration: 0.45 }}
              >
                <div
                  aria-hidden
                  className="display text-[clamp(2.8rem,9vw,8.5rem)]"
                >
                  {seg.title.map((line, li) => (
                    <span key={li} className="block overflow-hidden pb-[0.04em]">
                      <motion.span
                        className="block"
                        style={li === 1 ? { color: "var(--tint)" } : undefined}
                        initial={reduce ? false : { y: "115%" }}
                        animate={{ y: "0%" }}
                        exit={reduce ? undefined : { y: "-115%" }}
                        transition={{
                          duration: 0.85,
                          ease: EASE,
                          delay: reduce ? 0 : 0.05 + li * 0.08,
                        }}
                      >
                        {line}
                      </motion.span>
                    </span>
                  ))}
                </div>
                <motion.p
                  initial={reduce ? false : { opacity: 0, y: 14 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={reduce ? undefined : { opacity: 0 }}
                  transition={{ duration: 0.5, delay: reduce ? 0 : 0.25 }}
                  className="mt-5 max-w-md text-[0.95rem] leading-relaxed text-bone/70"
                >
                  {seg.series.blurb}
                  <span className="tnum mt-1 block text-bone/50">{seg.meta}</span>
                </motion.p>
              </motion.div>
            </AnimatePresence>

            <div className="mt-8 flex flex-wrap items-center gap-3">
              <Button href="/events" size="lg" magnetic arrow>
                Browse fixtures
              </Button>
              <Button href="/#experience" size="lg" variant="outline">
                The experience
              </Button>
            </div>
          </div>
        </div>
      </div>

      <div className="pointer-events-none absolute bottom-9 left-1/2 z-10 hidden -translate-x-1/2 items-center gap-2 text-bone/55 sm:flex md:bottom-12">
        <Scroll className="h-4 w-4" />
        <span className="font-mono text-[0.6rem] uppercase tracking-[0.3em]">
          Scroll
        </span>
      </div>
    </section>
  );
}
