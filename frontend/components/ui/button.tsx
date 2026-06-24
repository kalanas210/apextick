"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { cn } from "@/lib/cn";
import { Magnetic } from "./magnetic";
import { ArrowUpRight } from "./icons";

type Variant = "primary" | "outline" | "ghost";
type Size = "sm" | "md" | "lg";

interface CommonProps {
  children: ReactNode;
  variant?: Variant;
  size?: Size;
  className?: string;
  magnetic?: boolean;
  arrow?: boolean;
}

type ButtonAsLink = CommonProps & {
  href: string;
  onClick?: never;
  type?: never;
};
type ButtonAsButton = CommonProps & {
  href?: never;
  onClick?: () => void;
  type?: "button" | "submit";
};

type ButtonProps = ButtonAsLink | ButtonAsButton;

const sizes: Record<Size, string> = {
  sm: "h-9 px-4 text-[0.8rem]",
  md: "h-11 px-5 text-[0.9rem]",
  lg: "h-14 px-7 text-[0.95rem]",
};

const variants: Record<Variant, string> = {
  primary:
    "bg-accent text-accent-ink hover:brightness-105 border border-transparent",
  outline:
    "bg-transparent text-bone border border-line-2 hover:border-bone hover:bg-bone/[0.04]",
  ghost: "bg-transparent text-bone border border-transparent hover:bg-bone/[0.06]",
};

export function Button(props: ButtonProps) {
  const {
    children,
    variant = "primary",
    size = "md",
    className,
    magnetic = false,
    arrow = false,
  } = props;

  const classes = cn(
    "group/btn relative inline-flex items-center justify-center gap-2 rounded-full font-medium tracking-tight",
    "transition-[background-color,border-color,filter,transform] duration-300 ease-out",
    "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent",
    sizes[size],
    variants[variant],
    className,
  );

  const inner = (
    <>
      <span>{children}</span>
      {arrow && (
        <ArrowUpRight className="h-4 w-4 transition-transform duration-300 ease-out group-hover/btn:translate-x-0.5 group-hover/btn:-translate-y-0.5" />
      )}
    </>
  );

  const element =
    "href" in props && props.href !== undefined ? (
      <Link href={props.href} className={classes}>
        {inner}
      </Link>
    ) : (
      <button
        type={("type" in props && props.type) || "button"}
        onClick={"onClick" in props ? props.onClick : undefined}
        className={classes}
      >
        {inner}
      </button>
    );

  if (magnetic) {
    return <Magnetic className="inline-block">{element}</Magnetic>;
  }
  return element;
}
