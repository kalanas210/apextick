import { IMG, unsplash } from "@/data/images";
import type { Sport } from "./types";

interface ImageOpts {
  w?: number;
  h?: number;
  q?: number;
}

/**
 * The photograph for an event. The seeded season carries its own, but an event
 * an operator creates in the panel has no image at all — rather than a hole in
 * the grid, it gets the house shot for its sport.
 */
export function eventImage(
  event: { image?: string | null; sport?: Sport | null },
  opts: ImageOpts = {},
): string {
  const own = event.image?.trim();
  const house = event.sport === "cricket" ? IMG.cricketStadiumDusk : IMG.footballStadiumPacked;
  return unsplash(own || house, opts);
}

/** The same fallback for a series band, which is editorial rather than per-sport. */
export function seriesImage(
  series: { image?: string | null; sport?: Sport | null } | null | undefined,
  opts: ImageOpts = {},
): string {
  return eventImage(series ?? {}, opts);
}
