import type { Metadata } from "next";
import { AuthPanel } from "@/components/AuthPanel";

export const metadata: Metadata = {
  title: "Create account — Stack Pilot",
  description: "Create your Stack Pilot account with Google in one step.",
};

export default function SignUpPage() {
  return <AuthPanel mode="signup" />;
}
