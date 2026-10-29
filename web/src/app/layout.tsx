import type { Metadata } from "next";
import "./globals.css";
import { SITE_CONFIG } from "@/config/site";
import { Navbar } from "@/components/Navbar";
import { Footer } from "@/components/Footer";
import { MouseSpotlight } from "@/components/ui/MouseSpotlight";
import { CustomCursor } from "@/components/ui/CustomCursor";
import { SmoothScroll } from "@/components/ui/SmoothScroll";

export const metadata: Metadata = {
  title: `${SITE_CONFIG.name} — ${SITE_CONFIG.tagline}`,
  description: SITE_CONFIG.description,
  keywords: [
    "infrastructure troubleshooting",
    "developer tools",
    "docker debugging",
    "kafka diagnostics",
    "redis monitoring",
    "chaos testing",
    "root cause analysis",
    "DevOps",
    "fault injection",
    "Stack Pilot",
  ],
  authors: [{ name: "Stack Pilot Team" }],
  openGraph: {
    title: `${SITE_CONFIG.name} — Find the Fault. Fix the Stack.`,
    description: SITE_CONFIG.description,
    url: "https://stackpilot.dev",
    siteName: "Stack Pilot",
    locale: "en_US",
    type: "website",
  },
  twitter: {
    card: "summary_large_image",
    title: `${SITE_CONFIG.name} — Find the Fault. Fix the Stack.`,
    description: SITE_CONFIG.description,
  },
  icons: {
    icon: [
      { url: "/logo-white.png", type: "image/png" },
    ],
    shortcut: "/logo-white.png",
    apple: "/logo-white.png",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className="dark scroll-smooth">
      <body className="min-h-screen bg-[#070b12] text-slate-200 antialiased selection:bg-[#00ffaa]/20 selection:text-[#00ffaa] flex flex-col overflow-x-hidden">
        {/* Inertial wheel scrolling + gliding anchor links */}
        <SmoothScroll />

        {/* Global mouse spotlight effect — runs on every page */}
        <MouseSpotlight />

        {/* Circular cursor + red click ripple — desktop pointers only */}
        <CustomCursor />

        {/* Sticky top navigation — visible on every page */}
        <Navbar />

        {/* Page-specific content */}
        <div className="flex-1 relative z-10">
          {children}
        </div>

        {/* Footer — visible on every page */}
        <Footer />
      </body>
    </html>
  );
}
