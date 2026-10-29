"use client";

import React from "react";
import Link from "next/link";
import Image from "next/image";
import { usePathname } from "next/navigation";
import { BookOpen, Heart, ArrowUp } from "lucide-react";
import { SITE_CONFIG } from "@/config/site";

/** Pages that render without the marketing footer (they are opened by the app). */
const AUTH_ROUTES = ["/signin", "/signup", "/auth/callback"];

export function Footer() {
  const currentYear = 2026;
  const pathname = usePathname();

  if (AUTH_ROUTES.includes(pathname)) return null;

  return (
    <footer className="bg-[#05080f] border-t border-white/5 py-12 md:py-16 text-slate-400 text-xs sm:text-sm">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 md:grid-cols-12 gap-8 mb-12">
          {/* Brand info */}
          <div className="md:col-span-5 space-y-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 flex items-center justify-center shrink-0">
                <Image
                  src="/logo-white.png"
                  alt="Stack Pilot Logo"
                  width={40}
                  height={40}
                  className="w-full h-full object-contain"
                />
              </div>
              <span className="font-bold text-base text-white tracking-tight">Stack Pilot</span>
            </div>
            <p className="text-slate-400 text-xs sm:text-sm leading-relaxed max-w-sm">
              Developer-focused infrastructure troubleshooting tool for reproducing faults, investigating root causes, and validating fixes in controlled environments.
            </p>
            <p className="text-[11px] font-mono text-slate-500">
              Tagline: {SITE_CONFIG.tagline}
            </p>
          </div>

          {/* Product + Resources — 2 col on mobile, split on desktop */}
          <div className="md:col-span-5 grid grid-cols-2 gap-8">
            {/* Product links */}
            <div className="space-y-3">
              <h4 className="text-xs font-mono font-semibold uppercase tracking-wider text-slate-200">
                Product
              </h4>
              <ul className="space-y-2 text-xs">
                <li>
                  <a href="#features" className="hover:text-white transition-colors">
                    Features
                  </a>
                </li>
                <li>
                  <a href="#tool-tour" className="hover:text-white transition-colors">
                    Tool Tour
                  </a>
                </li>
                <li>
                  <a href="#faq" className="hover:text-white transition-colors">
                    FAQ
                  </a>
                </li>
                <li>
                  <a
                    href={SITE_CONFIG.release.notesUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="hover:text-[#00ffaa] transition-colors font-medium"
                  >
                    Download
                  </a>
                </li>
              </ul>
            </div>

            {/* Resources */}
            <div className="space-y-3">
              <h4 className="text-xs font-mono font-semibold uppercase tracking-wider text-slate-200">
                Resources
              </h4>
              <ul className="space-y-2 text-xs">
                <li>
                  <a
                    href={SITE_CONFIG.github.docsUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="hover:text-white transition-colors"
                  >
                    Documentation
                  </a>
                </li>
                <li>
                  <a
                    href={SITE_CONFIG.github.repoUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="hover:text-white transition-colors"
                  >
                    GitHub Repository
                  </a>
                </li>
                <li>
                  <a
                    href={SITE_CONFIG.github.issuesUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="hover:text-white transition-colors"
                  >
                    Report an Issue
                  </a>
                </li>
                <li>
                  <a
                    href={SITE_CONFIG.github.contributingUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="hover:text-white transition-colors"
                  >
                    Contributing
                  </a>
                </li>
                <li>
                  <a href="#faq" className="hover:text-white transition-colors">
                    Frequently Asked Questions
                  </a>
                </li>
              </ul>
            </div>
          </div>

          {/* Back to top & channel status */}
          <div className="md:col-span-2 flex flex-col justify-between items-start md:items-end space-y-4">
            <a
              href="#"
              className="p-2.5 rounded-xl bg-white/5 border border-white/5 hover:border-white/10 hover:bg-white/10 text-slate-300 hover:text-white transition-all flex items-center gap-2 text-xs font-mono"
            >
              <span>Back to Top</span>
              <ArrowUp className="w-3.5 h-3.5" />
            </a>

            <div className="text-right text-[11px] font-mono text-slate-500">
              <span className="inline-block w-2 h-2 rounded-full bg-[#00ffaa] mr-1.5" />
              <span>Status: Systems Operational</span>
            </div>
          </div>
        </div>

        {/* Bottom copyright notice */}
        <div className="pt-8 border-t border-white/5 flex flex-col sm:flex-row items-center justify-between gap-4 text-xs text-slate-400">
          <div>
            &copy; {currentYear} Stack Pilot. All rights reserved.
          </div>
          <div className="flex items-center gap-6 text-[11px] font-mono text-slate-400">
            <span>Find the Fault. Fix the Stack.</span>
          </div>
        </div>
      </div>
    </footer>
  );
}
