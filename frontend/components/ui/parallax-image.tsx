"use client";

import Image from "next/image";
import { useRef, type ReactNode } from "react";
import { motion, useReducedMotion, useScroll, useTransform } from "framer-motion";
import { cn } from "@/lib/cn";

interface ParallaxImageProps {
  src: string;
  alt: string;
  /** Vertical drift in percent across the scroll window */
  amount?: number;
  sizes?: string;
  priority?: boolean;
  className?: string;
  imgClassName?: string;
  children?: ReactNode;
}

/**
 * Full-bleed image with a gentle scroll parallax. The inner layer is oversized
 * so the drift never exposes an edge. Holds still under reduced motion.
 */
export function ParallaxImage({
  src,
  alt,
  amount = 8,
  sizes = "100vw",
  priority = false,
  className,
  imgClassName,
  children,
}: ParallaxImageProps) {
  const ref = useRef<HTMLDivElement>(null);
  const reduce = useReducedMotion();
  const { scrollYProgress } = useScroll({
    target: ref,
    offset: ["start end", "end start"],
  });
  const y = useTransform(
    scrollYProgress,
    [0, 1],
    [`-${amount}%`, `${amount}%`],
  );

  return (
    <div ref={ref} className={cn("relative overflow-hidden", className)}>
      <motion.div
        style={{ y: reduce ? 0 : y }}
        className="absolute inset-[-12%] will-change-transform"
      >
        <Image
          src={src}
          alt={alt}
          fill
          sizes={sizes}
          priority={priority}
          className={cn("object-cover", imgClassName)}
        />
      </motion.div>
      {children}
    </div>
  );
}
