/**
 * Curated Unsplash photography, referenced by id so the data module stays clean.
 * All shots are stadium, pitch, and matchday atmosphere graded site wide.
 */

export const IMG = {
  cricketStadiumDusk: "1540747913346-19e32dc3e97e",
  cricketBatDrive: "1624526267942-ab0ff8a3e972",
  cricketStrikeDust: "1593766827228-8737b4534aa6",
  cricketBall: "1531415074968-036ba1b575da",
  cricketBatsman: "1589801258579-18e091f4ca26",
  footballStadiumPacked: "1522778119026-d647f0596c20",
  footballStadiumPano: "1429962714451-bb934ecdc4ec",
  footballNight: "1431324155629-1a6deb1dec8d",
  footballsSunset: "1551958219-acbc608c6377",
  footballKick: "1574629810360-7efbbe195018",
  playerTunnel: "1577223625816-7546f13df25d",
  grassLine: "1459865264687-595d652de67e",
  pitchAerial: "1556056504-5c7696c4c28d",
  worldCupStadium: "1647849402208-01775e9c9fb3",
} as const;

export type ImageKey = keyof typeof IMG;

interface UnsplashOpts {
  w?: number;
  h?: number;
  q?: number;
}

/** Build an optimized Unsplash delivery URL from a photo id. */
export function unsplash(id: string, opts: UnsplashOpts = {}): string {
  if (id.startsWith("http") || id.startsWith("/")) return id;
  const { w = 1600, h, q = 80 } = opts;
  const params = new URLSearchParams({
    auto: "format",
    fit: "crop",
    w: String(w),
    q: String(q),
  });
  if (h) params.set("h", String(h));
  return `https://images.unsplash.com/photo-${id}?${params.toString()}`;
}
