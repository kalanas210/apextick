"use client";

import { motion, useScroll, useSpring } from "framer-motion";
import { usePathname } from "next/navigation";

/**
 * Thin page-progress line anchored to the top edge of the viewport. Reading
 * progress is a marketing-page idea; the admin panel leaves it off.
 */
export function ScrollProgress() {
  const pathname = usePathname();
  const { scrollYProgress } = useScroll();
  const scaleX = useSpring(scrollYProgress, {
    stiffness: 120,
    damping: 30,
    restDelta: 0.001,
  });

  if (pathname.startsWith("/admin")) {
    return null;
  }

  return (
    <motion.div
      aria-hidden
      style={{ scaleX }}
      className="fixed inset-x-0 top-0 z-[55] h-[2px] origin-left bg-accent"
    />
  );
}
