"use client";

import React, { useState, useEffect } from "react";
import Link from "next/link";
import Image from "next/image";
import { usePathname } from "next/navigation";
import { Download, Menu, X, ArrowUpRight, Sparkles } from "lucide-react";
import { SITE_CONFIG } from "@/config/site";

/** Pages that render without the marketing chrome. */
const AUTH_ROUTES = ["/signin", "/signup", "/auth/callback"];

export function Navbar() {
  const [scrolled, setScrolled] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [heroDownloadVisible, setHeroDownloadVisible] = useState(false);
  const pathname = usePathname();

  useEffect(() => {
    const handleScroll = () => {
      setScrolled(window.scrollY > 20);
      // Close mobile menu on scroll
      if (mobileMenuOpen) setMobileMenuOpen(false);
    };
    window.addEventListener("scroll", handleScroll, { passive: true });
    return () => window.removeEventListener("scroll", handleScroll);
  }, [mobileMenuOpen]);

  useEffect(() => {
    const handler = (e: Event) => {
      setHeroDownloadVisible((e as CustomEvent<boolean>).detail);
    };
    window.addEventListener("hero-download-visible", handler);
    return () => window.removeEventListener("hero-download-visible", handler);
  }, []);

  // Anchor links only work on the home page
  const isHome = pathname === "/";

  // Auth screens stand on their own - they are opened by the desktop app and
  // should not be wrapped in the marketing navigation.
  if (AUTH_ROUTES.includes(pathname)) return null;

  const navLinks = [
    { name: "Features", href: isHome ? "#features" : "/#features" },
    { name: "Tool Tour", href: isHome ? "#tool-tour" : "/#tool-tour" },
    { name: "FAQ", href: isHome ? "#faq" : "/#faq" },
  ];

  return (
    <header
      className={`fixed top-0 left-0 right-0 z-50 transition-all duration-300 ${
        scrolled
          ? "bg-[#070b12]/90 backdrop-blur-md border-b border-[#131b2e]/60 shadow-lg shadow-black/60 py-3"
          : "bg-transparent border-b border-transparent py-5"
      }`}
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between">
          {/* Logo & Brand */}
          <Link
            href="/"
            className="flex items-center gap-3.5 group focus:outline-none focus-visible:ring-2 focus-visible:ring-[#00ffaa] rounded-lg p-1"
          >
            <div className="w-12 h-12 flex items-center justify-center shrink-0">
              <Image
                src="/logo-white.png"
                alt="Stack Pilot Logo"
                width={48}
                height={48}
                className="w-full h-full object-contain group-hover:scale-105 transition-transform drop-shadow-[0_0_12px_rgba(255,255,255,0.15)]"
                priority
              />
            </div>
            <div className="flex flex-col">
              <span className="font-bold text-lg tracking-tight text-white flex items-center gap-2">
                Stack Pilot
              </span>
              <span className="text-xs text-slate-400 hidden sm:inline-block font-mono">
                infra troubleshooting
              </span>
            </div>
          </Link>

          {/* Desktop Nav Links */}
          <nav className="hidden md:flex items-center gap-1 bg-[#0d131f]/60 p-1.5 rounded-full border border-white/5 backdrop-blur-sm">
            {navLinks.map((link) => (
              <a
                key={link.name}
                href={link.href}
                className="px-4 py-1.5 text-sm font-medium text-slate-300 hover:text-white rounded-full transition-colors hover:bg-white/5"
              >
                {link.name}
              </a>
            ))}

            {/* Innovator link — page route */}
            <Link
              href="/innovator"
              className={`flex items-center gap-1.5 px-4 py-1.5 text-sm font-medium rounded-full transition-all ${
                pathname === "/innovator"
                  ? "bg-[#00ffaa]/15 text-[#00ffaa] border border-[#00ffaa]/20"
                  : "text-slate-300 hover:text-white hover:bg-white/5"
              }`}
            >
              <Sparkles className="w-3.5 h-3.5" />
              Innovator
            </Link>
          </nav>

          {/* Desktop CTAs */}
          <div className="hidden md:flex items-center gap-3">
            <a
              href={SITE_CONFIG.github.repoUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="text-xs font-mono text-slate-400 hover:text-white flex items-center gap-1.5 px-3 py-2 rounded-lg border border-transparent hover:border-white/10 hover:bg-white/5 transition-all"
            >
              <span>GitHub</span>
              <ArrowUpRight className="w-3.5 h-3.5 text-slate-500" />
            </a>

            <div
              className={`transition-all duration-300 ${
                heroDownloadVisible
                  ? "opacity-0 pointer-events-none scale-95"
                  : "opacity-100 scale-100"
              }`}
            >
              <Link
                href="/download"
                className="flex items-center gap-2 px-4 py-2 rounded-lg bg-[#00ffaa] text-[#070b12] font-semibold text-sm hover:bg-[#00e599] transition-all shadow-md shadow-[#00ffaa]/20 hover:shadow-lg hover:shadow-[#00ffaa]/30 active:scale-95"
              >
                <Download className="w-4 h-4" />
                <span>Download</span>
              </Link>
            </div>
          </div>

          {/* Mobile Menu Button */}
          <div className="md:hidden flex items-center gap-2">
            <div
              className={`transition-all duration-300 ${
                heroDownloadVisible
                  ? "opacity-0 pointer-events-none scale-95"
                  : "opacity-100 scale-100"
              }`}
            >
              <Link
                href="/download"
                className="px-3 py-1.5 rounded-lg bg-[#00ffaa] text-[#070b12] font-semibold text-xs flex items-center gap-1.5"
              >
                <Download className="w-3.5 h-3.5" />
                Download
              </Link>
            </div>
            <button
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              className="p-2 rounded-lg text-slate-400 hover:text-white hover:bg-white/5 focus:outline-none focus:ring-2 focus:ring-[#00ffaa]"
              aria-label="Toggle navigation menu"
            >
              {mobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
            </button>
          </div>
        </div>
      </div>

      {/* Mobile Dropdown Menu */}
      {mobileMenuOpen && (
        <div className="md:hidden bg-[#070b12]/80 backdrop-blur-md border-b border-white/10 px-4 pt-3 pb-6 space-y-3 mt-2 shadow-2xl">
          <div className="flex flex-col space-y-1">
            {navLinks.map((link) => (
              <a
                key={link.name}
                href={link.href}
                onClick={() => setMobileMenuOpen(false)}
                className="px-3 py-2 rounded-lg text-sm font-medium text-slate-200 hover:bg-white/5 hover:text-[#00ffaa] transition-colors"
              >
                {link.name}
              </a>
            ))}

            {/* Innovator mobile link */}
            <Link
              href="/innovator"
              onClick={() => setMobileMenuOpen(false)}
              className={`flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                pathname === "/innovator"
                  ? "text-[#00ffaa] bg-[#00ffaa]/10"
                  : "text-slate-200 hover:bg-white/5 hover:text-[#00ffaa]"
              }`}
            >
              <Sparkles className="w-4 h-4" />
              Innovator
            </Link>
          </div>

          <div className="pt-3 border-t border-white/10 flex flex-col gap-2">
            <a
              href={SITE_CONFIG.github.repoUrl}
              target="_blank"
              rel="noopener noreferrer"
              onClick={() => setMobileMenuOpen(false)}
              className="flex items-center justify-between px-3 py-2 text-sm text-slate-300 rounded-lg hover:bg-white/5"
            >
              <span>View Source on GitHub</span>
              <ArrowUpRight className="w-4 h-4 text-slate-400" />
            </a>
            <Link
              href="/download"
              onClick={() => setMobileMenuOpen(false)}
              className="flex items-center justify-center gap-2 w-full py-2.5 rounded-lg bg-[#00ffaa] text-[#070b12] font-bold text-sm shadow-md shadow-[#00ffaa]/20"
            >
              <Download className="w-4 h-4" />
              Download Stack Pilot ({SITE_CONFIG.release.version})
            </Link>
          </div>
        </div>
      )}
    </header>
  );
}
