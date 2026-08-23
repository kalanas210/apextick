# ApexTick

A real time ticket booking experience for the biggest nights in world sport. This is the public marketing and seat selection frontend: a flagship, type led interface built on mock data so the whole thing runs on its own with no backend.

Three flagship series are modelled as distinct brand worlds inside one design system:

- ICC T20 World Cup 2026 (cricket, international)
- Indian Premier League (cricket, franchise)
- Premier League (football, English matchday)

## Stack

- Next.js 16 (App Router) with React 19 and TypeScript
- Tailwind CSS v4 with design tokens defined in `app/globals.css`
- Framer Motion for scroll driven and physical motion
- Lenis for inertial smooth scrolling, disabled under reduced motion
- `next/font` self hosting Bricolage Grotesque, Inter Tight, and Geist Mono
- `next/image` with optimized remote imagery

## Getting started

```bash
npm install
npm run dev
```

Open http://localhost:3000. The app needs nothing else running.

```bash
npm run build   # production build
npm run lint    # eslint
```

## Routes

| Route | Purpose |
| --- | --- |
| `/` | Scroll driven hero, series showcase, fixtures, stats, and the matchday story |
| `/events` | Every fixture, filterable by sport, series, and month |
| `/events/[slug]` | A single fixture: matchup, pricing tiers, and stand selector |
| `/events/[slug]/seats` | Interactive seat map with a live order summary and hold timer |

## Structure

```
app/                 routes and the root layout
components/
  ui/                reusable primitives (buttons, motion, crest, marquee, grain)
  site/              header, footer, logo, smooth scroll
  home/              home page sections
  fixtures/          fixture card, listing explorer, tier panel
  seatmap/           seat map, stadium diagram, tier colors
data/                typed mock data for series, fixtures, teams, tiers
lib/                 formatting, color, seat generation, class helpers
```

## Design system

- Warm near black canvas (`#0B0B0C`) and a bone off white (`#F4F2EC`)
- One signature accent, an electric lime (`#C9F23F`), used sparingly site wide
- A secondary tint per series, used only inside that series' own context
- Bricolage Grotesque for tight display headlines, Inter Tight for body, Geist Mono for tabular numbers
- Hairline borders, a baseline grid, film grain, and graded photography for energy

## Data

Fixture copy — names, imagery, blurbs, and the marketing pages built on them — comes
from `data/events.ts`. Team crests are typographic monograms generated in code, not
official logos.

Everything in the booking flow is live against the booking service: the seat map
reads real availability (and streams changes over STOMP), holds and orders hit the
API, and checkout takes a real payment through Stripe or the backend's offline mock
gateway. Sign-in is Keycloak (authorization code + PKCE), so this app never handles
a password.
