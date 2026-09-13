# ApexTick

A real time ticket booking experience for the biggest nights in world sport. This is the public marketing and seat selection frontend: a flagship, type led interface that renders whatever is in the booking service's catalog, from the home page down to the seat.

Each series is its own brand world inside one design system — its colour, cities and
photography come from the catalog, so a series an operator adds arrives with its own
look rather than a fallback one. The demo season ships four:

- ICC T20 World Cup 2026 (cricket, international)
- Indian Premier League (cricket, franchise)
- Premier League (football, English matchday)
- FIFA World Cup (football, international)

## Stack

- Next.js 16 (App Router) with React 19 and TypeScript
- Tailwind CSS v4 with design tokens defined in `app/globals.css`
- Framer Motion for scroll driven and physical motion
- Lenis for inertial smooth scrolling, disabled under reduced motion
- `next/font` self hosting Bricolage Grotesque, Inter Tight, and Geist Mono
- `next/image` with optimized remote imagery

## Getting started

```bash
npm ci --ignore-scripts
npm run dev
```

Open http://localhost:3000. Every page reads the booking service and signing in needs
Keycloak, so start the backend first (the root README's Getting started). The browser
finds the API on port 8081 of the same host, or wherever `NEXT_PUBLIC_API_URL` says;
server-rendered pages use `API_INTERNAL_URL` when they run inside Docker. With no API
the storefront says the catalog is unavailable rather than showing an empty one.

```bash
npm run build   # production build
npm run lint    # eslint
npm test        # Vitest: helpers, and components rendered to static markup
```

## Routes

| Route | Purpose |
| --- | --- |
| `/` | Scroll driven hero, series showcase, fixtures, stats, and the matchday story |
| `/events` | Every fixture, filterable by sport, series, and month |
| `/events/[slug]` | A single fixture: matchup, pricing tiers, and stand selector |
| `/events/[slug]/seats` | Interactive seat map with a live order summary and hold timer |
| `/checkout/[orderId]` | Paying for a held order, 3-D Secure included, against the payment window's countdown |
| `/orders/[id]` | One order: its status, and its tickets with QR codes and PDFs |
| `/account` | The signed-in customer's orders |
| `/signin`, `/register`, `/forgot-password` | Hand off to Keycloak, which owns every credential |
| `/admin` | The admin panel: events and layouts, orders and refunds, seats, event cancellation (the `admin` role) |
| `/admin/scan` | The gate scanner (the `admin` or `scanner` role) |

## Structure

```
app/                 routes and the root layout
components/
  ui/                reusable primitives (buttons, motion, crest, marquee, grain, dialogs, tables)
  site/              header, footer, logo, smooth scroll, the demo and test-mode labels
  home/              home page sections
  fixtures/          fixture card, listing explorer, tier panel
  seatmap/           live seat map, stadium diagram, tier colors
  auth/              the Keycloak hand-off, and the gates for signed-in and admin pages
  checkout/          the payment forms (Stripe Elements, the mock gateway) and order summary
  account/           orders and tickets
  admin/             the admin panel and the gate scanner
hooks/               React Query hooks over the API, and the session
data/                curated photography ids (the only data still in the repo)
lib/                 API clients (browser and server), formatting, checkout and site-mode helpers
```

## Design system

- Warm near black canvas (`#0B0B0C`) and a bone off white (`#F4F2EC`)
- One signature accent, an electric lime (`#C9F23F`), used sparingly site wide
- A secondary tint per series, used only inside that series' own context
- Bricolage Grotesque for tight display headlines, Inter Tight for body, Geist Mono for tabular numbers
- Hairline borders, a baseline grid, film grain, and graded photography for energy

## Data

Every page is the booking service's catalog. Fixtures, series, teams, tiers, stands,
prices, availability, photography and copy are read from the API — so an event an
operator creates, prices and publishes in the admin panel is on the home page, in the
grid, on its own page and on the seat map immediately, and nothing on screen claims a
number the API would contradict. Team crests fall back to typographic monograms
generated in code; they are not official logos.

The three storefront routes render on the server and are always dynamic: availability
changes by the second and an event can be pulled from sale at any moment. In the
browser the API is same-origin behind Caddy (or `NEXT_PUBLIC_API_URL`); on the server
it is `API_INTERNAL_URL`, the service name on the compose network.

The booking flow is live too: the seat map streams changes over STOMP, holds and
orders hit the API, and checkout takes a real payment through Stripe or the backend's
offline mock gateway. Sign-in is Keycloak (authorization code + PKCE), so this app
never handles a password.

Whether the deployment is the demo, and whether checkout takes real money, come from
the API too (`GET /api/payments/config`): the Demo badge, the "demonstration site" line
and the test-mode notes appear only when they are true.
