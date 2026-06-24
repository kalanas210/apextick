import Link from "next/link";
import Image from "next/image";
import { cn } from "@/lib/cn";

/** ApexTick wordmark using the brand ticket mark from /public. */
export function Logo({ className }: { className?: string }) {
  return (
    <Link
      href="/"
      aria-label="ApexTick, home"
      className={cn("group inline-flex items-center gap-2.5", className)}
    >
      <Image
        src="/logo.png"
        alt=""
        width={28}
        height={28}
        className="h-7 w-7 shrink-0 object-contain"
      />
      <span className="font-display text-[1.15rem] font-bold leading-none tracking-tight">
        Apex<span className="text-accent">Tick</span>
      </span>
    </Link>
  );
}
