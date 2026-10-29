import type { Metadata } from "next";
import { AuthPanel } from "@/components/AuthPanel";

export const metadata: Metadata = {
  title: "Sign in — Stack Pilot",
  description: "Sign in to Stack Pilot with your Google account and return to the desktop app.",
};

export default function SignInPage() {
  return <AuthPanel mode="signin" />;
}
