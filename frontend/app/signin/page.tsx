import type { Metadata } from "next";
import { AuthShell } from "@/components/auth/auth-shell";
import { KeycloakSignIn } from "@/components/auth/keycloak-signin";
import { IMG } from "@/data/images";

export const metadata: Metadata = {
  title: "Sign in",
  description:
    "Sign in to ApexTick to keep your seats close for the biggest nights in world sport.",
};

export default function SignInPage() {
  return (
    <AuthShell
      kicker="Welcome back"
      title="Sign in"
      subtitle="Pick up where you left off and keep your seats within reach."
      image={IMG.cricketStadiumDusk}
      alt="A floodlit cricket stadium at dusk"
      quote="The best seats go to the ones who show up."
      quoteCaption="ICC T20 World Cup 2026"
    >
      <KeycloakSignIn action="signin" />
    </AuthShell>
  );
}
