import type { Metadata, Viewport } from "next";
import { Bricolage_Grotesque, Inter_Tight, Geist_Mono } from "next/font/google";
import "./globals.css";
import { Grain } from "@/components/ui/grain";
import { ScrollProgress } from "@/components/ui/scroll-progress";
import { SmoothScroll } from "@/components/site/smooth-scroll";
import { Chrome } from "@/components/site/chrome";
import { Footer } from "@/components/site/footer";
import Providers from "./providers";

const display = Bricolage_Grotesque({
  subsets: ["latin"],
  variable: "--font-bricolage",
  display: "swap",
});

const body = Inter_Tight({
  subsets: ["latin"],
  variable: "--font-inter-tight",
  display: "swap",
});

const mono = Geist_Mono({
  subsets: ["latin"],
  variable: "--font-geist-mono",
  display: "swap",
});

export const metadata: Metadata = {
  title: {
    default: "ApexTick, tickets for the biggest nights in world sport",
    template: "%s, ApexTick",
  },
  description:
    "Real-time seat selection for the ICC T20 World Cup 2026, the Indian Premier League, and the Premier League. Pick your seat, feel the room, never miss the moment.",
  keywords: [
    "sports tickets",
    "cricket",
    "football",
    "T20 World Cup",
    "Indian Premier League",
    "Premier League",
  ],
};

export const viewport: Viewport = {
  themeColor: "#0b0b0c",
  colorScheme: "dark",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html
      lang="en"
      className={`${display.variable} ${body.variable} ${mono.variable} h-full antialiased`}
    >
      <body className="relative min-h-full bg-ink text-bone">
        <a
          href="#main"
          className="skip-link rounded-full bg-accent px-5 py-2.5 text-sm font-medium text-accent-ink"
        >
          Skip to content
        </a>
        <span id="top" aria-hidden className="absolute left-0 top-0" />
        <Grain />
        <SmoothScroll />
        <ScrollProgress />
        <Providers>
          <Chrome footer={<Footer />}>{children}</Chrome>
        </Providers>
      </body>
    </html>
  );
}
