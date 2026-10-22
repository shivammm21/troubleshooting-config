import type { Metadata } from "next";
import { InnovatorPageClient } from "@/components/InnovatorPageClient";

export const metadata: Metadata = {
  title: "Innovator — Shivam Thorat | Stack Pilot",
  description:
    "Meet Shivam Thorat, the developer and innovator behind Stack Pilot — the infrastructure troubleshooting tool built for modern DevOps engineers.",
};

export default function InnovatorPage() {
  return <InnovatorPageClient />;
}
