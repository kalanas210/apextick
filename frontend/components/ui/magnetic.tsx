"use client";

import {
  motion,
  useMotionValue,
  useSpring,
  useReducedMotion,
} from "framer-motion";
import { useRef, type ReactNode } from "react";

interface MagneticProps {
  children: ReactNode;
  className?: string;
  /** How far the element drifts toward the cursor, 0 to 1 */
  strength?: number;
}

/**
 * Pulls its child toward the cursor while hovered, then springs back.
 * Disabled entirely under reduced-motion and on touch (no hover events fire).
 */
export function Magnetic({
  children,
  className,
  strength = 0.35,
}: MagneticProps) {
  const ref = useRef<HTMLDivElement>(null);
  const reduce = useReducedMotion();
  const x = useMotionValue(0);
  const y = useMotionValue(0);
  const sx = useSpring(x, { stiffness: 180, damping: 14, mass: 0.1 });
  const sy = useSpring(y, { stiffness: 180, damping: 14, mass: 0.1 });

  function handleMove(e: React.MouseEvent<HTMLDivElement>) {
    if (reduce || !ref.current) return;
    const rect = ref.current.getBoundingClientRect();
    const relX = e.clientX - (rect.left + rect.width / 2);
    const relY = e.clientY - (rect.top + rect.height / 2);
    x.set(relX * strength);
    y.set(relY * strength);
  }

  function handleLeave() {
    x.set(0);
    y.set(0);
  }

  return (
    <motion.div
      ref={ref}
      onMouseMove={handleMove}
      onMouseLeave={handleLeave}
      style={{ x: reduce ? 0 : sx, y: reduce ? 0 : sy }}
      className={className}
    >
      {children}
    </motion.div>
  );
}
