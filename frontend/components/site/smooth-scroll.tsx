"use client";

import { useEffect } from "react";
import { useReducedMotion } from "framer-motion";
import { usePathname } from "next/navigation";
import Lenis from "lenis";

/**
 * Site-wide inertial scrolling. Lenis drives the native scroll position, so it
 * stays compatible with the pinned, scroll-scrubbed hero. Skipped entirely when
 * the visitor asks for reduced motion, leaving the browser's native scroll.
 *
 * Also skipped in the admin panel: Lenis intercepts wheel events for the whole
 * document, which fights the panel's own scrolling regions (sticky table heads,
 * horizontally scrolling tables) for no benefit on a screen nobody scrubs.
 */
export function SmoothScroll() {
  const reduce = useReducedMotion();
  const pathname = usePathname();
  const admin = pathname.startsWith("/admin");

  useEffect(() => {
    if (reduce || admin) return;

    const lenis = new Lenis({
      duration: 1.1,
      easing: (t) => Math.min(1, 1.001 - Math.pow(2, -10 * t)),
      smoothWheel: true,
      touchMultiplier: 1.6,
    });

    let frame = 0;
    function raf(time: number) {
      lenis.raf(time);
      frame = requestAnimationFrame(raf);
    }
    frame = requestAnimationFrame(raf);

    return () => {
      cancelAnimationFrame(frame);
      lenis.destroy();
    };
  }, [reduce, admin]);

  return null;
}
