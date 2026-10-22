"use client";

import React, { useState } from "react";
import { Download, Terminal, Check, Copy, ExternalLink, ShieldAlert, Laptop, Apple, Monitor } from "lucide-react";
import { SITE_CONFIG } from "@/config/site";

export function DownloadSection() {
  const [selectedOS, setSelectedOS] = useState<"linux" | "macos" | "windows">("linux");
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  const release = SITE_CONFIG.release;
  const currentPlatform = release.platforms.find((p) => p.os === selectedOS) || release.platforms[0];

  const handleCopy = (text: string, key: string) => {
    navigator.clipboard.writeText(text);
    setCopiedKey(key);
    setTimeout(() => setCopiedKey(null), 2000);
  };

  return (
    <section id="download" className="py-20 md:py-28 relative">
      {/* Background glow */}
      <div className="absolute inset-0 bg-gradient-to-b from-transparent via-[#00ffaa]/5 to-transparent pointer-events-none" />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        <div className="max-w-4xl mx-auto text-center space-y-4 mb-14">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#00ffaa]/10 border border-[#00ffaa]/25 text-[#00ffaa] text-xs font-mono uppercase tracking-wider">
            <span>Official Releases</span>
          </div>
          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-extrabold text-white tracking-tight">
            Take control of your infrastructure debugging.
          </h2>
          <p className="text-base sm:text-lg text-slate-300 leading-relaxed max-w-2xl mx-auto">
            Get started with Stack Pilot and make troubleshooting more structured, reproducible, and developer-friendly.
          </p>
        </div>

        {/* Download Card Container */}
        <div className="max-w-3xl mx-auto rounded-3xl bg-[#0c121e] border border-white/10 shadow-2xl p-6 sm:p-10 relative overflow-hidden">
          {/* Release status banner */}
          <div className="flex flex-wrap items-center justify-between gap-4 pb-6 border-b border-white/10">
            <div className="flex items-center gap-3">
              <span className="text-xl font-bold text-white font-mono">{release.version}</span>
              <span className="px-2.5 py-0.5 rounded-full bg-[#00ffaa]/15 text-[#00ffaa] border border-[#00ffaa]/30 text-xs font-semibold">
                {release.channel}
              </span>
            </div>
            <div className="flex items-center gap-4 text-xs font-mono text-slate-400">
              <span>Released: {release.releaseDate}</span>
              <a
                href={release.notesUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="text-[#00ffaa] hover:underline flex items-center gap-1"
              >
                Release Notes &rarr;
              </a>
            </div>
          </div>

          {/* OS Selector Tabs */}
          <div className="mt-8">
            <div className="grid grid-cols-3 gap-2 p-1.5 rounded-xl bg-[#080d15] border border-white/5">
              <button
                onClick={() => setSelectedOS("linux")}
                className={`flex items-center justify-center gap-2 py-3 rounded-lg text-xs sm:text-sm font-semibold transition-all ${
                  selectedOS === "linux"
                    ? "bg-[#0c121e] text-[#00ffaa] shadow-md border border-[#00ffaa]/30"
                    : "text-slate-400 hover:text-white"
                }`}
              >
                <Terminal className="w-4 h-4" />
                <span>Linux</span>
              </button>

              <button
                onClick={() => setSelectedOS("macos")}
                className={`flex items-center justify-center gap-2 py-3 rounded-lg text-xs sm:text-sm font-semibold transition-all ${
                  selectedOS === "macos"
                    ? "bg-[#0c121e] text-[#00ffaa] shadow-md border border-[#00ffaa]/30"
                    : "text-slate-400 hover:text-white"
                }`}
              >
                <Apple className="w-4 h-4" />
                <span>macOS</span>
              </button>

              <button
                onClick={() => setSelectedOS("windows")}
                className={`flex items-center justify-center gap-2 py-3 rounded-lg text-xs sm:text-sm font-semibold transition-all ${
                  selectedOS === "windows"
                    ? "bg-[#0c121e] text-[#00ffaa] shadow-md border border-[#00ffaa]/30"
                    : "text-slate-400 hover:text-white"
                }`}
              >
                <Monitor className="w-4 h-4" />
                <span>Windows</span>
              </button>
            </div>
          </div>

          {/* Active Platform Details & Primary Download CTA */}
          <div className="mt-8 p-6 rounded-2xl bg-[#080d16] border border-white/5 flex flex-col md:flex-row items-center justify-between gap-6">
            <div className="space-y-1 text-center md:text-left">
              <h4 className="text-base font-bold text-white flex items-center justify-center md:justify-start gap-2">
                <span>{currentPlatform.label}</span>
                <span className="text-xs font-mono text-slate-400">({currentPlatform.arch})</span>
              </h4>
              <p className="text-xs text-slate-400 font-mono">
                Format: {currentPlatform.format} &bull; Size: {currentPlatform.size}
              </p>
            </div>

            {currentPlatform.isReady ? (
              <a
                href={currentPlatform.downloadUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="w-full md:w-auto px-8 py-3.5 rounded-xl bg-[#00ffaa] hover:bg-[#00e599] text-[#070b12] font-bold text-sm sm:text-base flex items-center justify-center gap-2.5 shadow-lg shadow-[#00ffaa]/20 hover:shadow-[#00ffaa]/35 transition-all"
              >
                <Download className="w-5 h-5 text-[#070b12]" />
                <span>Download for {currentPlatform.label.split(" ")[0]}</span>
              </a>
            ) : (
              <a
                href={SITE_CONFIG.github.repoUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="w-full md:w-auto px-6 py-3 rounded-xl bg-white/5 text-slate-300 border border-white/10 hover:border-white/20 text-xs font-mono flex items-center justify-center gap-2"
              >
                <span>Coming Soon &bull; View Source on GitHub</span>
              </a>
            )}
          </div>

          {/* CLI Quick Install Options */}
          <div className="mt-8 space-y-3">
            <h5 className="text-xs font-mono uppercase tracking-wider text-slate-400">
              Or install via terminal package manager
            </h5>

            {/* Curl / Bash */}
            <div className="p-3 rounded-xl bg-[#080d15] border border-white/5 flex items-center justify-between text-xs font-mono">
              <div className="flex items-center gap-2 overflow-x-auto whitespace-nowrap scrollbar-none pr-2 text-slate-300">
                <span className="text-[#00ffaa]">$</span>
                <span>{SITE_CONFIG.quickInstall.curl}</span>
              </div>
              <button
                onClick={() => handleCopy(SITE_CONFIG.quickInstall.curl, "curl")}
                className="shrink-0 p-1.5 rounded hover:bg-white/10 text-slate-400 hover:text-white"
                title="Copy command"
              >
                {copiedKey === "curl" ? <Check className="w-4 h-4 text-[#00ffaa]" /> : <Copy className="w-4 h-4" />}
              </button>
            </div>

            {/* Homebrew */}
            <div className="p-3 rounded-xl bg-[#080d15] border border-white/5 flex items-center justify-between text-xs font-mono">
              <div className="flex items-center gap-2 overflow-x-auto whitespace-nowrap scrollbar-none pr-2 text-slate-300">
                <span className="text-[#00ffaa]">$</span>
                <span>{SITE_CONFIG.quickInstall.brew}</span>
              </div>
              <button
                onClick={() => handleCopy(SITE_CONFIG.quickInstall.brew, "brew")}
                className="shrink-0 p-1.5 rounded hover:bg-white/10 text-slate-400 hover:text-white"
                title="Copy command"
              >
                {copiedKey === "brew" ? <Check className="w-4 h-4 text-[#00ffaa]" /> : <Copy className="w-4 h-4" />}
              </button>
            </div>
          </div>

          {/* Checksum & Verification */}
          {release.checksumSha256 && (
            <div className="mt-8 pt-6 border-t border-white/5 space-y-2">
              <div className="flex items-center justify-between text-xs font-mono text-slate-400">
                <span className="flex items-center gap-1.5">
                  <Terminal className="w-3.5 h-3.5 text-[#00ffaa]" />
                  SHA-256 Checksum Verification
                </span>
                <button
                  onClick={() => handleCopy(release.checksumSha256!, "sha")}
                  className="text-xs text-[#00ffaa] hover:underline flex items-center gap-1"
                >
                  {copiedKey === "sha" ? "Copied SHA!" : "Copy Hash"}
                </button>
              </div>
              <div className="p-2.5 rounded-lg bg-[#070b13] border border-white/5 font-mono text-[11px] text-slate-400 break-all select-all">
                {release.checksumSha256}
              </div>
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
