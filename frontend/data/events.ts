import { IMG } from "./images";
import type {
  Fixture,
  PricingTier,
  SeatingSection,
  Series,
  SeriesId,
  Team,
} from "./types";

/* ----------------------------------------------------------------------------
   Teams
   Plain-text names with a brand color for the typographic crests. No logos.
---------------------------------------------------------------------------- */

const team = (
  name: string,
  short: string,
  monogram: string,
  color: string,
  flag?: string,
  logo?: string,
): Team => ({ name, short, monogram, color, flag, logo });

const T = {
  // International cricket (national flags are public domain)
  ind: team("India", "IND", "IN", "#2a4fb0", "in"),
  aus: team("Australia", "AUS", "AU", "#0e7a4e", "au"),
  eng: team("England", "ENG", "EN", "#1c2f5a", "gb-eng"),
  pak: team("Pakistan", "PAK", "PK", "#0b6e4f", "pk"),
  rsa: team("South Africa", "RSA", "SA", "#0a6e3b", "za"),
  nzl: team("New Zealand", "NZL", "NZ", "#3a3f47", "nz"),
  sri: team("Sri Lanka", "SL", "SL", "#1b4f9c", "lk"),
  win: team("West Indies", "WI", "WI", "#6e0e2b"),

  // IPL franchises
  mi: team("Mumbai Indians", "MI", "MI", "#0a4da2"),
  csk: team("Chennai Super Kings", "CSK", "CS", "#d9a400"),
  rcb: team("Royal Challengers Bengaluru", "RCB", "RC", "#c8102e"),
  kkr: team("Kolkata Knight Riders", "KKR", "KK", "#5b3a92"),
  gt: team("Gujarat Titans", "GT", "GT", "#143b66"),
  rr: team("Rajasthan Royals", "RR", "RR", "#e6007e"),
  dc: team("Delhi Capitals", "DC", "DC", "#17449b"),
  srh: team("Sunrisers Hyderabad", "SRH", "SH", "#f26522"),

  // Premier League clubs
  ars: team("Arsenal", "ARS", "AR", "#ef0107", undefined, "/logos/ars.png"),
  mci: team("Manchester City", "MCI", "MC", "#6cabdd", undefined, "/logos/mci.png"),
  liv: team("Liverpool", "LIV", "LI", "#c8102e", undefined, "/logos/liv.png"),
  mun: team("Manchester United", "MUN", "MU", "#da291c", undefined, "/logos/mun.png"),
  che: team("Chelsea", "CHE", "CH", "#1f6dd0", undefined, "/logos/che.png"),
  tot: team("Tottenham Hotspur", "TOT", "TH", "#9fb4d8", undefined, "/logos/tot.png"),
  new: team("Newcastle United", "NEW", "NU", "#cfd2d4", undefined, "/logos/new.png"),
  avl: team("Aston Villa", "AVL", "AV", "#8c2754", undefined, "/logos/avl.png"),
} satisfies Record<string, Team>;

/* ----------------------------------------------------------------------------
   Tier + section builders
---------------------------------------------------------------------------- */

const tiers = (prices: {
  standard: number;
  premium: number;
  gold: number;
  suite: number;
}): PricingTier[] => [
  {
    id: "standard",
    name: "Standard",
    price: prices.standard,
    remaining: 1840,
    perks: ["Full match access", "General concourse", "Open seating zones"],
  },
  {
    id: "premium",
    name: "Premium",
    price: prices.premium,
    remaining: 612,
    perks: ["Elevated sightlines", "Padded seating", "Express entry gate"],
  },
  {
    id: "gold",
    name: "Gold",
    price: prices.gold,
    remaining: 188,
    perks: ["Center stand view", "Lounge access", "In-seat service"],
  },
  {
    id: "suite",
    name: "Suite",
    price: prices.suite,
    remaining: 0,
    perks: ["Private box", "Hosted dining", "Dedicated concierge"],
  },
];

const standSet: SeatingSection[] = [
  { id: "north", name: "North Stand", tierId: "gold", side: "n", rows: 4, seatsPerRow: 18 },
  { id: "east", name: "East Stand", tierId: "premium", side: "e", rows: 5, seatsPerRow: 12 },
  { id: "south", name: "South Stand", tierId: "standard", side: "s", rows: 6, seatsPerRow: 18 },
  { id: "west", name: "West Stand", tierId: "premium", side: "w", rows: 5, seatsPerRow: 12 },
];

/* ----------------------------------------------------------------------------
   Series, the three brand worlds
---------------------------------------------------------------------------- */

export const series: Record<SeriesId, Series> = {
  "icc-t20-2026": {
    id: "icc-t20-2026",
    name: "ICC T20 World Cup 2026",
    shortName: "T20 World Cup",
    sport: "cricket",
    tint: "#46b1ff",
    kicker: "International / Cricket",
    blurb:
      "Twenty nations, one trophy, decided across the subcontinent under lights.",
    story:
      "The global game arrives at full volume. Forty eight matches, twenty teams, and a knockout run where a single over can rewrite a nation's summer. From the first ball in Colombo to the final under the Ahmedabad floodlights, this is cricket at its loudest.",
    image: IMG.cricketStadiumDusk,
    mobileHeroImage: "/virat.jpg",
    currency: "INR",
    currencySymbol: "₹",
    cities: ["Ahmedabad", "Mumbai", "Kolkata", "Colombo"],
    scale: "48 matches",
  },
  "ipl-2026": {
    id: "ipl-2026",
    name: "Indian Premier League",
    shortName: "IPL 2026",
    sport: "cricket",
    tint: "#ff3d77",
    kicker: "Franchise / Cricket",
    blurb:
      "Ten cities, floodlit nights, and the most relentless league in the sport.",
    story:
      "Two months of full houses, music between the overs, and rivalries that shut down whole cities. The IPL is cricket rebuilt for the night: faster, louder, and impossible to look away from. Every seat is inside the noise.",
    image: "/2020-t20w-worldcup-final-mcg.jpg",
    mobileHeroImage: "/aus.webp",
    currency: "INR",
    currencySymbol: "₹",
    cities: ["Mumbai", "Chennai", "Bengaluru", "Kolkata", "Ahmedabad"],
    scale: "74 nights",
  },
  "premier-league": {
    id: "premier-league",
    name: "Premier League",
    shortName: "Premier League",
    sport: "football",
    tint: "#ed3f2c",
    kicker: "Matchday / Football",
    blurb:
      "The English season at full tilt, from the first whistle to stoppage time.",
    story:
      "Floodlights, terraces, and ninety minutes that decide a season. The Premier League is matchday culture distilled: the walk to the ground, the roar at kickoff, and the away end that never sits down. Iconic stadiums, historic rivalries, every weekend.",
    image: "/pre.jpg",
    mobileHeroImage: "/ronaldo.jpg",
    currency: "GBP",
    currencySymbol: "£",
    cities: ["London", "Manchester", "Liverpool", "Newcastle", "Birmingham"],
    scale: "38 rounds",
  },
  "fifa-world-cup": {
    id: "fifa-world-cup",
    name: "FIFA World Cup",
    shortName: "World Cup",
    sport: "football",
    tint: "#00a859",
    kicker: "International / Football",
    blurb:
      "The world's game, played on the biggest stage. Thirty-two nations, one dream.",
    story:
      "A month of pure footballing drama. The FIFA World Cup brings the globe together for a festival of sport, where legends are made and history is written. Every four years, the world stops to watch.",
    image: IMG.worldCupStadium,
    mobileHeroImage: "/fifa.jpg",
    currency: "USD",
    currencySymbol: "$",
    cities: ["New York", "Los Angeles", "Miami", "Dallas", "Toronto"],
    scale: "64 matches",
  },
};

export const seriesList: Series[] = [
  series["icc-t20-2026"],
  series["ipl-2026"],
  series["premier-league"],
  series["fifa-world-cup"],
];

/* ----------------------------------------------------------------------------
   Fixtures
---------------------------------------------------------------------------- */

const iccTiers = tiers({ standard: 3500, premium: 7500, gold: 14000, suite: 32000 });
const iplTiers = tiers({ standard: 2500, premium: 6000, gold: 12000, suite: 25000 });
const plTiers = tiers({ standard: 55, premium: 95, gold: 160, suite: 420 });

export const fixtures: Fixture[] = [
  /* ICC T20 World Cup 2026 */
  {
    id: "icc-ind-pak",
    slug: "india-pakistan-group-stage",
    seriesId: "icc-t20-2026",
    home: T.ind,
    away: T.pak,
    date: "2026-02-21",
    time: "19:00",
    stadium: "Narendra Modi Stadium",
    city: "Ahmedabad",
    country: "India",
    stage: "Group Stage",
    status: "selling-fast",
    image: IMG.cricketStadiumDusk,
    blurb:
      "The fixture that stops a billion clocks. A hundred and thirty thousand seats, one rivalry, settled in three hours.",
    tiers: iccTiers,
    sections: standSet,
  },
  {
    id: "icc-aus-eng",
    slug: "australia-england-super-8",
    seriesId: "icc-t20-2026",
    home: T.aus,
    away: T.eng,
    date: "2026-02-26",
    time: "19:00",
    stadium: "Eden Gardens",
    city: "Kolkata",
    country: "India",
    stage: "Super 8",
    status: "onsale",
    image: IMG.cricketStrikeDust,
    blurb:
      "Two heavyweight white-ball sides under the Eden Gardens roar, with a semi-final place on the line.",
    tiers: iccTiers,
    sections: standSet,
  },
  {
    id: "icc-rsa-nz",
    slug: "south-africa-new-zealand-super-8",
    seriesId: "icc-t20-2026",
    home: T.rsa,
    away: T.nzl,
    date: "2026-02-23",
    time: "15:30",
    stadium: "R. Premadasa Stadium",
    city: "Colombo",
    country: "Sri Lanka",
    stage: "Super 8",
    status: "onsale",
    image: IMG.cricketBatsman,
    blurb:
      "A Colombo afternoon, spin-friendly and tense, between two sides who know each other far too well.",
    tiers: iccTiers,
    sections: standSet,
  },
  {
    id: "icc-ind-aus-sf",
    slug: "india-australia-semi-final",
    seriesId: "icc-t20-2026",
    home: T.ind,
    away: T.aus,
    date: "2026-03-04",
    time: "19:00",
    stadium: "Wankhede Stadium",
    city: "Mumbai",
    country: "India",
    stage: "Semi Final",
    status: "selling-fast",
    image: IMG.cricketStadiumDusk,
    blurb:
      "Mumbai under lights, a sea-breeze chasing the ball to the boundary, and a final spot for the winner.",
    tiers: iccTiers,
    sections: standSet,
  },
  {
    id: "icc-final",
    slug: "world-cup-final",
    seriesId: "icc-t20-2026",
    home: T.ind,
    away: T.eng,
    date: "2026-03-08",
    time: "19:00",
    stadium: "Narendra Modi Stadium",
    city: "Ahmedabad",
    country: "India",
    stage: "Final",
    status: "final-release",
    image: IMG.cricketStrikeDust,
    blurb:
      "One night, one trophy, the largest cricket ground on earth at capacity. The last seats of the tournament.",
    tiers: iccTiers,
    sections: standSet,
  },

  /* Indian Premier League 2026 */
  {
    id: "ipl-mi-csk",
    slug: "mumbai-indians-chennai-super-kings",
    seriesId: "ipl-2026",
    home: T.mi,
    away: T.csk,
    date: "2026-04-12",
    time: "19:30",
    stadium: "Wankhede Stadium",
    city: "Mumbai",
    country: "India",
    stage: "League / Night 9",
    status: "selling-fast",
    image: IMG.cricketBatDrive,
    blurb:
      "Blue against yellow, the league's oldest grudge, on a Wankhede deck built for sixes.",
    tiers: iplTiers,
    sections: standSet,
  },
  {
    id: "ipl-rcb-kkr",
    slug: "bengaluru-kolkata-night",
    seriesId: "ipl-2026",
    home: T.rcb,
    away: T.kkr,
    date: "2026-04-15",
    time: "19:30",
    stadium: "M. Chinnaswamy Stadium",
    city: "Bengaluru",
    country: "India",
    stage: "League / Night 12",
    status: "onsale",
    image: IMG.cricketBall,
    blurb:
      "Chinnaswamy is the shortest boundary on the circuit. Bring an appetite for run chases.",
    tiers: iplTiers,
    sections: standSet,
  },
  {
    id: "ipl-gt-rr",
    slug: "gujarat-titans-rajasthan-royals",
    seriesId: "ipl-2026",
    home: T.gt,
    away: T.rr,
    date: "2026-04-18",
    time: "15:30",
    stadium: "Narendra Modi Stadium",
    city: "Ahmedabad",
    country: "India",
    stage: "League / Night 15",
    status: "onsale",
    image: IMG.cricketBatsman,
    blurb:
      "A day game in Ahmedabad, two of the league's youngest sides swinging from ball one.",
    tiers: iplTiers,
    sections: standSet,
  },
  {
    id: "ipl-csk-mi-return",
    slug: "chennai-mumbai-return",
    seriesId: "ipl-2026",
    home: T.csk,
    away: T.mi,
    date: "2026-04-25",
    time: "19:30",
    stadium: "M. A. Chidambaram Stadium",
    city: "Chennai",
    country: "India",
    stage: "League / Night 22",
    status: "selling-fast",
    image: IMG.cricketStrikeDust,
    blurb:
      "The return leg at Chepauk, where the crowd noise has its own weather system.",
    tiers: iplTiers,
    sections: standSet,
  },
  {
    id: "ipl-qualifier",
    slug: "qualifier-one",
    seriesId: "ipl-2026",
    home: T.mi,
    away: T.gt,
    date: "2026-05-24",
    time: "19:30",
    stadium: "Eden Gardens",
    city: "Kolkata",
    country: "India",
    stage: "Qualifier 1",
    status: "final-release",
    image: IMG.cricketBatDrive,
    blurb:
      "Win and you are in the final. Lose and there is one more road back. The playoffs begin in Kolkata.",
    tiers: iplTiers,
    sections: standSet,
  },

  /* Premier League */
  {
    id: "pl-ars-mci",
    slug: "arsenal-manchester-city",
    seriesId: "premier-league",
    home: T.ars,
    away: T.mci,
    date: "2026-02-22",
    time: "16:30",
    stadium: "Emirates Stadium",
    city: "London",
    country: "England",
    stage: "Matchday 27",
    status: "selling-fast",
    image: IMG.footballStadiumPacked,
    blurb:
      "The title race in ninety minutes. North London hosts the champions with everything still to play for.",
    tiers: plTiers,
    sections: standSet,
  },
  {
    id: "pl-liv-mun",
    slug: "liverpool-manchester-united",
    seriesId: "premier-league",
    home: T.liv,
    away: T.mun,
    date: "2026-03-01",
    time: "16:00",
    stadium: "Anfield",
    city: "Liverpool",
    country: "England",
    stage: "Matchday 28",
    status: "selling-fast",
    image: IMG.footballNight,
    blurb:
      "England's biggest rivalry, under the Anfield lights, with the Kop in full voice from the first minute.",
    tiers: plTiers,
    sections: standSet,
  },
  {
    id: "pl-mci-tot",
    slug: "manchester-city-tottenham",
    seriesId: "premier-league",
    home: T.mci,
    away: T.tot,
    date: "2026-03-07",
    time: "15:00",
    stadium: "Etihad Stadium",
    city: "Manchester",
    country: "England",
    stage: "Matchday 29",
    status: "onsale",
    image: IMG.footballStadiumPano,
    blurb:
      "A Saturday three o'clock at the Etihad, the way the league was meant to be watched.",
    tiers: plTiers,
    sections: standSet,
  },
  {
    id: "pl-new-che",
    slug: "newcastle-chelsea",
    seriesId: "premier-league",
    home: T.new,
    away: T.che,
    date: "2026-03-14",
    time: "12:30",
    stadium: "St James' Park",
    city: "Newcastle",
    country: "England",
    stage: "Matchday 30",
    status: "onsale",
    image: IMG.footballKick,
    blurb:
      "An early kickoff on Tyneside, where the noise comes down the stands like weather off the river.",
    tiers: plTiers,
    sections: standSet,
  },
  {
    id: "pl-avl-ars",
    slug: "aston-villa-arsenal",
    seriesId: "premier-league",
    home: T.avl,
    away: T.ars,
    date: "2026-03-21",
    time: "17:30",
    stadium: "Villa Park",
    city: "Birmingham",
    country: "England",
    stage: "Matchday 31",
    status: "final-release",
    image: IMG.footballsSunset,
    blurb:
      "A floodlit evening at Villa Park, with the visitors chasing every point in the run-in.",
    tiers: plTiers,
    sections: standSet,
  },
  {
    id: "pl-tot-che",
    slug: "tottenham-chelsea-derby",
    seriesId: "premier-league",
    home: T.tot,
    away: T.che,
    date: "2026-04-04",
    time: "17:30",
    stadium: "Tottenham Hotspur Stadium",
    city: "London",
    country: "England",
    stage: "Matchday 32",
    status: "selling-fast",
    image: IMG.footballStadiumPano,
    blurb:
      "A London derby with European places on the line, under the sharpest roof in the league.",
    tiers: plTiers,
    sections: standSet,
  },
];

/* ----------------------------------------------------------------------------
   Lookups
---------------------------------------------------------------------------- */

export function getFixture(slug: string): Fixture | undefined {
  return fixtures.find((f) => f.slug === slug);
}

export function getSeries(id: SeriesId): Series {
  return series[id];
}

export function fixturesBySeries(id: SeriesId): Fixture[] {
  return fixtures.filter((f) => f.seriesId === id);
}

export function tierById(fixture: Fixture, tierId: string): PricingTier | undefined {
  return fixture.tiers.find((t) => t.id === tierId);
}

/** Stable, sorted by date for editorial grids. */
export const fixturesByDate: Fixture[] = [...fixtures].sort((a, b) =>
  a.date < b.date ? -1 : a.date > b.date ? 1 : 0,
);

/** A few hand-picked marquee fixtures for the home showcase. */
export const featuredFixtures: Fixture[] = [
  "india-pakistan-group-stage",
  "mumbai-indians-chennai-super-kings",
  "liverpool-manchester-united",
  "world-cup-final",
]
  .map(getFixture)
  .filter((f): f is Fixture => Boolean(f));
