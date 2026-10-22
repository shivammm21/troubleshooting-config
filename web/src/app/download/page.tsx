import type { Metadata } from "next";
import { DownloadPageClient } from "@/components/DownloadPageClient";

export const metadata: Metadata = {
  title: "Download Stack Pilot — Free Infrastructure Debugging Tool",
  description:
    "Download Stack Pilot for macOS, Windows, or Linux. Free open-source infrastructure troubleshooting tool for developers and DevOps engineers.",
};

export default function DownloadPage() {
  return <DownloadPageClient />;
}
