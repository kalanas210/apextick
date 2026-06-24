import type { Metadata } from "next";
import { AuthShell } from "@/components/auth/auth-shell";
import { RegisterForm } from "@/components/auth/register-form";
import { IMG } from "@/data/images";

export const metadata: Metadata = {
  title: "Create account",
  description:
    "Create an ApexTick account for one way into the ICC T20 World Cup, the Indian Premier League, and the Premier League.",
};

export default function RegisterPage() {
  return (
    <AuthShell
      kicker="Join ApexTick"
      title="Create your account"
      subtitle="One account for every fixture, every series, every seat."
      image={IMG.footballStadiumPacked}
      alt="A packed football stadium under lights"
      quote="Join the room. The best nights in sport, one tap away."
      quoteCaption="Premier League"
    >
      <RegisterForm />
    </AuthShell>
  );
}
