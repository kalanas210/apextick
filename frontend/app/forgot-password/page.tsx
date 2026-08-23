import type { Metadata } from "next";
import { AuthShell } from "@/components/auth/auth-shell";
import { KeycloakSignIn } from "@/components/auth/keycloak-signin";
import { IMG } from "@/data/images";

export const metadata: Metadata = {
  title: "Reset password",
  description: "Reset your ApexTick password and get back to your seats.",
};

export default function ForgotPasswordPage() {
  return (
    <AuthShell
      kicker="Forgot password"
      title="Reset your password"
      subtitle="Enter your email and we will send a link to set a new one."
      image={IMG.playerTunnel}
      alt="A player tunnel opening toward the pitch"
      quote="Happens to everyone. Let us get you back in."
      quoteCaption="ApexTick"
    >
      <KeycloakSignIn action="reset" />
    </AuthShell>
  );
}
