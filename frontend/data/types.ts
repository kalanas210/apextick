/**
 * Domain types for the ApexTick catalog.
 * Everything the marketing and booking surfaces render is built from these.
 */

export type Sport = "cricket" | "football";

export type SeriesId = "icc-t20-2026" | "ipl-2026" | "premier-league" | "fifa-world-cup";

export type FixtureStatus = "onsale" | "selling-fast" | "final-release";

export interface Team {
  /** Full display name, e.g. "India" or "Arsenal" */
  name: string;
  /** Three-letter code used in compact UI, e.g. "IND" */
  short: string;
  /** Monogram printed inside the crest, two or three letters */
  monogram: string;
  /** Brand color for the typographic crest */
  color: string;
  /**
   * ISO country code for national sides (e.g. "in", "au", "gb-eng"). Present
   * only on national teams, which render a public-domain flag instead of a
   * coded crest. Clubs leave this undefined and keep the crest.
   */
  flag?: string;
  /** Path to a club badge in /public (e.g. "/logos/ars.png"). Clubs only. */
  logo?: string;
}

export interface PricingTier {
  id: string;
  name: string;
  /** Price in the series currency, whole units */
  price: number;
  /** Short selling points for the tier */
  perks: string[];
  /** Seats remaining in this tier, for scarcity cues */
  remaining: number;
}

export interface SeatingSection {
  id: string;
  name: string;
  /** Links the stand to a pricing tier */
  tierId: string;
  /** Position around the field of play for the seat map layout */
  side: "n" | "s" | "e" | "w";
  rows: number;
  seatsPerRow: number;
}

export interface Fixture {
  id: string;
  slug: string;
  seriesId: SeriesId;
  home: Team;
  away: Team;
  /** ISO date, e.g. "2026-02-21" */
  date: string;
  /** 24h local kickoff, e.g. "19:00" */
  time: string;
  stadium: string;
  city: string;
  country: string;
  /** Round or stage label, e.g. "Group Stage" or "Matchday 28" */
  stage: string;
  status: FixtureStatus;
  /** Curated Unsplash photo id */
  image: string;
  blurb: string;
  tiers: PricingTier[];
  sections: SeatingSection[];
}

export interface Series {
  id: SeriesId;
  name: string;
  shortName: string;
  sport: Sport;
  /** Event tint, used only inside this series' own context */
  tint: string;
  kicker: string;
  blurb: string;
  /** Longer editorial paragraph for the series header */
  story: string;
  /** Curated Unsplash photo id for the hero stack */
  image: string;
  /** Optional custom image for mobile hero */
  mobileHeroImage?: string;
  currency: "INR" | "GBP" | "USD";
  currencySymbol: string;
  cities: string[];
  /** Headline stat shown in showcase, e.g. "48 matches" */
  scale: string;
}
