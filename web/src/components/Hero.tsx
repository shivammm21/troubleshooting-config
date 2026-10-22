"use client";

import React, { useState, useEffect, useRef } from "react";
import Link from "next/link";
import { Download, Terminal, ChevronRight, Copy, Check, Sparkles, Shield, ArrowDown } from "lucide-react";
import { SITE_CONFIG } from "@/config/site";
import { HeroToolDisplay } from "./HeroToolDisplay";
import { ShaderAnimation } from "@/components/ui/shader-animation";

export function Hero() {
  const [copied, setCopied] = useState(false);
  const command = SITE_CONFIG.quickInstall.curl;
  const downloadBtnRef = useRef<HTMLAnchorElement>(null);

  const copyToClipboard = () => {
    navigator.clipboard.writeText(command);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  // Broadcast hero-download-btn visibility so Navbar can react
  useEffect(() => {
    const el = downloadBtnRef.current;
    if (!el) return;

    const observer = new IntersectionObserver(
      ([entry]) => {
        window.dispatchEvent(
          new CustomEvent("hero-download-visible", { detail: entry.isIntersecting })
        );
      },
      { threshold: 0.5 }
    );

    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  return (
    <section className="relative pt-32 pb-20 md:pt-40 md:pb-28 overflow-hidden min-h-[90vh]">
      {/* Dynamic WebGL Shader Animation — Hero Section Only */}
      <div className="absolute inset-0 z-0 pointer-events-none opacity-50 mix-blend-screen overflow-hidden">
        <ShaderAnimation className="w-full h-full" />
      </div>

      {/* Hero Bottom & Edge Fades so shader smoothly transitions into the next section */}
      <div className="absolute inset-0 z-0 pointer-events-none bg-gradient-to-b from-[#070b12]/80 via-transparent to-[#070b12]" />
      <div className="absolute bottom-0 left-0 right-0 h-40 bg-gradient-to-t from-[#070b12] to-transparent z-0 pointer-events-none" />

      {/* Background radial glow effect */}
      <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[800px] h-[450px] bg-[#00ffaa]/10 rounded-full blur-[140px] pointer-events-none" />
      <div className="absolute top-1/3 right-1/4 w-[500px] h-[350px] bg-sky-500/10 rounded-full blur-[120px] pointer-events-none" />

      {/* Subtle grid pattern background */}
      <div className="absolute inset-0 bg-[linear-gradient(to_right,#1e293b0f_1px,transparent_1px),linear-gradient(to_bottom,#1e293b0f_1px,transparent_1px)] bg-[size:4rem_4rem] [mask-image:radial-gradient(ellipse_60%_50%_at_50%_0%,#000_70%,transparent_100%)] pointer-events-none z-0" />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        <div className="flex flex-col items-center gap-12 lg:gap-14">
          {/* Top Row: Centered Value Proposition, Headline & CTAs */}
          <div className="w-full max-w-4xl text-center space-y-6 flex flex-col items-center">

            {/* Main Headline */}
            <h1 className="text-3xl sm:text-5xl md:text-5xl lg:text-6xl font-extrabold text-white tracking-tight leading-tight sm:whitespace-nowrap">
              Find the Fault.{" "}
              <span className="text-transparent bg-clip-text bg-gradient-to-r from-[#00ffaa] via-[#38bdf8] to-emerald-300">
                Fix the Stack.
              </span>
            </h1>

            {/* Supporting Description */}
            <p className="text-base sm:text-lg md:text-xl text-slate-300 max-w-2xl mx-auto leading-relaxed font-normal">
              Reproduce infrastructure failures, investigate root causes, and validate fixes in a controlled environment. Give your development team a better way to troubleshoot complex infrastructure issues.
            </p>

            {/* CTAs */}
            <div className="flex flex-col sm:flex-row items-center justify-center gap-4 pt-2 w-full sm:w-auto">
              <Link
                href="/download"
                ref={downloadBtnRef}
                className="w-full sm:w-auto px-8 py-3.5 rounded-xl bg-[#00ffaa] text-[#070b12] font-bold text-sm sm:text-base flex items-center justify-center gap-2.5 hover:bg-[#00e599] transition-all shadow-lg shadow-[#00ffaa]/20 hover:shadow-[#00ffaa]/35 active:scale-95 group"
              >
                <Download className="w-4 h-4 text-[#070b12] group-hover:-translate-y-0.5 transition-transform" />
                <span>Download Stack Pilot</span>
              </Link>

              <a
                href="#features"
                className="w-full sm:w-auto px-7 py-3.5 rounded-xl bg-[#0d131f] text-slate-200 border border-white/10 hover:border-[#00ffaa]/40 hover:bg-white/5 font-semibold text-sm sm:text-base flex items-center justify-center gap-2 transition-all"
              >
                <span>Explore Features</span>
                <ChevronRight className="w-4 h-4 text-slate-400" />
              </a>
            </div>

            {/* Supporting tagline note */}
            <div className="flex items-center gap-2 text-xs text-slate-400 pt-1">
              <Shield className="w-3.5 h-3.5 text-[#00ffaa]" />
              <span>Built for developers and DevOps engineers.</span>
            </div>
          </div>

          {/* Bottom Row: Full-width macOS Window & Interactive Tool Toggle */}
          <div className="w-full max-w-5xl">
            <HeroToolDisplay />
          </div>
        </div>
      </div>
    </section>
  );
}
