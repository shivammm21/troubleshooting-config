"use client";

import React, { useState } from "react";
import Image from "next/image";
import Link from "next/link";
import {
  Download,
  Copy,
  Check,
  ExternalLink,
  Shield,
  Star,
  Package,
  ChevronRight,
} from "lucide-react";
import { SITE_CONFIG, WINDOWS_SETUP_URL, WINDOWS_CHECKSUM_URL } from "@/config/site";

// ─── Copy Button ──────────────────────────────────────────────────────────────

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);
  const handleCopy = () => {
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };
  return (
    <button
      onClick={handleCopy}
      className="shrink-0 p-1.5 rounded-md text-slate-400 hover:text-[#00ffaa] hover:bg-[#00ffaa]/10 transition-all"
      title="Copy"
    >
      {copied ? <Check className="w-4 h-4 text-[#00ffaa]" /> : <Copy className="w-4 h-4" />}
    </button>
  );
}

// ─── Download links ──────────────────────────────────────────────────────────

// WINDOWS_SETUP_URL points at the release asset produced by
// .github/workflows/release.yml (stable "latest/download" alias).
const WINDOWS_CHECKSUM_COMMAND = "Get-FileHash .\\StackPilot-Setup.exe -Algorithm SHA256";

// ─── Platform cards data ───────────────────────────────────────────────────────

const platforms = [
  {
    os: "windows",
    name: "Windows",
    subtitle: "Windows 10 / 11 (64-bit)",
    logoSrc: "/downloads/windows-icon-42340.png",
    logoAlt: "Windows logo",
    logoBg: "bg-[#00ADEF]/10",
    borderColor: "border-[#00ADEF]/30",
    glowColor: "hover:shadow-[#00ADEF]/10",
    accentColor: "#00ADEF",
    requirement: "Windows 10 version 2004+ · Docker Desktop 4.x · WSL 2",
    arch: "x86_64",
    format: ".exe installer",
    size: "173.5 MB",
    isReady: true,
    downloadUrl: WINDOWS_SETUP_URL,
    extras: [
      { label: "SHA-256 checksum", url: WINDOWS_CHECKSUM_URL },
    ],
  },
  {
    os: "macos",
    name: "macOS",
    subtitle: "Apple Silicon & Intel Universal",
    logoSrc: "/downloads/apple-logo-icon-14916.gif",
    logoAlt: "Apple macOS logo",
    logoBg: "bg-white/10",
    borderColor: "border-white/20",
    glowColor: "hover:shadow-white/5",
    accentColor: "#f1f5f9",
    requirement: "macOS 12 Monterey+ · Docker Desktop 4.x or OrbStack",
    arch: "Universal (arm64 / x86_64)",
    format: ".dmg disk image",
    size: "48.1 MB",
    isReady: false,
    downloadUrl: SITE_CONFIG.release.notesUrl,
    extras: [],
  },
  {
    os: "linux",
    name: "Linux",
    subtitle: "Ubuntu · Fedora · Debian · Arch",
    logoSrc: "/downloads/cartoon-png-31571.png",
    logoAlt: "Linux Tux logo",
    logoBg: "bg-[#FCC624]/10",
    borderColor: "border-[#FCC624]/30",
    glowColor: "hover:shadow-[#FCC624]/10",
    accentColor: "#FCC624",
    requirement: "Linux x86_64 · Docker Engine 24+ · systemd",
    arch: "x86_64",
    format: ".tar.gz / AppImage",
    size: "42.8 MB",
    isReady: false,
    downloadUrl: SITE_CONFIG.release.notesUrl,
    extras: [],
  },
];

// ─── Platform Card ─────────────────────────────────────────────────────────────

function PlatformCard({ p }: { p: (typeof platforms)[number] }) {
  return (
    <div
      className={`relative flex flex-col rounded-3xl bg-[#0b1120] border ${p.borderColor} shadow-xl transition-all duration-300 hover:shadow-2xl ${p.glowColor} overflow-hidden group`}
    >
      {/* Top glow strip */}
      <div
        className="absolute top-0 left-0 right-0 h-[2px] opacity-60"
        style={{ background: `linear-gradient(to right, transparent, ${p.accentColor}, transparent)` }}
      />

      <div className="p-8 flex flex-col gap-6 flex-1">
        {/* Logo + name */}
        <div className="flex items-center gap-4">
          <div className={`w-16 h-16 rounded-2xl ${p.logoBg} flex items-center justify-center shrink-0 p-2`}>
            <Image
              src={p.logoSrc}
              alt={p.logoAlt}
              width={48}
              height={48}
              className="w-full h-full object-contain"
              unoptimized
            />
          </div>
          <div>
            <h2 className="text-2xl font-extrabold text-white">{p.name}</h2>
            <p className="text-sm text-slate-400 mt-0.5">{p.subtitle}</p>
            {!p.isReady && (
              <span className="inline-block mt-2 px-2 py-0.5 rounded-full bg-white/5 border border-white/10 text-[10px] font-mono uppercase tracking-widest text-slate-400">
                Coming soon
              </span>
            )}
          </div>
        </div>

        {/* Metadata grid */}
        <div className="grid grid-cols-2 gap-3 text-xs font-mono">
          {[
            { label: "Architecture", value: p.arch },
            { label: "Format", value: p.format },
            { label: "Size", value: p.size },
            { label: "Version", value: SITE_CONFIG.release.version },
          ].map(({ label, value }) => (
            <div key={label} className="bg-[#080d17] rounded-xl px-3 py-2.5 border border-white/5">
              <p className="text-slate-500 mb-1">{label}</p>
              <p className="text-slate-200 font-semibold truncate">{value}</p>
            </div>
          ))}
        </div>

        {/* System requirement */}
        <div className="flex items-start gap-2 text-xs text-slate-400 bg-white/[0.03] rounded-xl px-4 py-3 border border-white/5">
          <Shield className="w-3.5 h-3.5 shrink-0 mt-0.5 text-slate-500" />
          <span>{p.requirement}</span>
        </div>

        {/* Spacer */}
        <div className="flex-1" />

        {/* Primary download */}
        {p.isReady ? (
          <a
            href={p.downloadUrl}
            className="flex items-center justify-center gap-2.5 w-full py-3.5 rounded-xl font-bold text-sm transition-all duration-200 active:scale-95"
            style={{
              background: `linear-gradient(135deg, ${p.accentColor}22, ${p.accentColor}11)`,
              border: `1.5px solid ${p.accentColor}55`,
              color: p.accentColor,
            }}
            onMouseEnter={(e) => {
              (e.currentTarget as HTMLAnchorElement).style.background = `${p.accentColor}22`;
              (e.currentTarget as HTMLAnchorElement).style.borderColor = `${p.accentColor}99`;
            }}
            onMouseLeave={(e) => {
              (e.currentTarget as HTMLAnchorElement).style.background = `linear-gradient(135deg, ${p.accentColor}22, ${p.accentColor}11)`;
              (e.currentTarget as HTMLAnchorElement).style.borderColor = `${p.accentColor}55`;
            }}
          >
            <Download className="w-4 h-4" />
            Download for {p.name}
          </a>
        ) : (
          <span className="flex items-center justify-center gap-2.5 w-full py-3.5 rounded-xl font-bold text-sm bg-white/[0.03] border border-white/10 text-slate-500 cursor-not-allowed">
            <Download className="w-4 h-4" />
            Not available yet
          </span>
        )}

        {/* Alternate links */}
        {p.extras.length > 0 && (
          <div className="flex flex-wrap gap-2">
            {p.extras.map((ex) => (
              <a
                key={ex.label}
                href={ex.url}
                className="flex items-center gap-1.5 text-xs text-slate-400 hover:text-slate-200 transition-colors px-3 py-1.5 rounded-lg bg-white/[0.03] border border-white/5 hover:border-white/10"
              >
                <Package className="w-3 h-3" />
                {ex.label}
              </a>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

// ─── Main page ─────────────────────────────────────────────────────────────────

export function DownloadPageClient() {
  return (
    <main className="text-white">
      {/* Subtle background glows */}
      <div className="fixed inset-0 pointer-events-none overflow-hidden">
        <div className="absolute top-0 left-1/4 w-[600px] h-[400px] bg-[#00ffaa]/5 rounded-full blur-[160px]" />
        <div className="absolute top-1/3 right-1/4 w-[500px] h-[350px] bg-sky-500/5 rounded-full blur-[140px]" />
        <div className="absolute bottom-0 left-1/2 w-[700px] h-[400px] bg-violet-500/5 rounded-full blur-[160px] -translate-x-1/2" />
      </div>

      <div className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-20 md:py-28">


        {/* Header */}
        <div className="text-center space-y-5 mb-16">
          <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-[#00ffaa]/10 border border-[#00ffaa]/25 text-[#00ffaa] text-xs font-mono font-medium">
            <Star className="w-3.5 h-3.5" />
            <span>{SITE_CONFIG.release.channel} · {SITE_CONFIG.release.version}</span>
          </div>

          <h1 className="text-4xl sm:text-5xl lg:text-6xl font-extrabold text-white tracking-tight">
            Download{" "}
            <span className="text-transparent bg-clip-text bg-gradient-to-r from-[#00ffaa] via-[#38bdf8] to-emerald-300">
              Stack Pilot
            </span>
          </h1>

          <p className="text-base sm:text-lg text-slate-400 max-w-2xl mx-auto leading-relaxed">
            Free and open-source. No account required. Pick your platform below and get
            started in minutes.
          </p>

          {/* Version badge row */}
          <div className="flex flex-wrap items-center justify-center gap-3 pt-2 text-xs font-mono text-slate-500">
            <span className="px-3 py-1 rounded-full bg-white/5 border border-white/10">
              Released {SITE_CONFIG.release.releaseDate}
            </span>
            <a
              href={SITE_CONFIG.release.notesUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-white/5 border border-white/10 hover:border-[#00ffaa]/40 hover:text-[#00ffaa] transition-colors"
            >
              Release notes <ExternalLink className="w-3 h-3" />
            </a>
            <a
              href={SITE_CONFIG.github.repoUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-white/5 border border-white/10 hover:border-[#00ffaa]/40 hover:text-[#00ffaa] transition-colors"
            >
              GitHub <ExternalLink className="w-3 h-3" />
            </a>
          </div>
        </div>

        {/* Platform cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 lg:gap-8">
          {platforms.map((p) => (
            <PlatformCard key={p.os} p={p} />
          ))}
        </div>

        {/* Checksum / verification */}
        <div className="mt-14 rounded-2xl bg-[#0b1120] border border-white/5 p-6 md:p-8">
          <div className="flex items-start gap-3 mb-4">
            <Shield className="w-5 h-5 text-[#00ffaa] shrink-0 mt-0.5" />
            <div>
              <h3 className="text-base font-bold text-white">Verify your download</h3>
              <p className="text-sm text-slate-400 mt-1">
                Compare the SHA-256 checksum of your downloaded file with the value below to ensure integrity.
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2 bg-[#060a12] rounded-xl px-4 py-3 border border-white/5 font-mono text-xs text-slate-300 mt-2">
            <span className="truncate flex-1 text-slate-400">
              {WINDOWS_CHECKSUM_COMMAND}
            </span>
            <CopyButton text={WINDOWS_CHECKSUM_COMMAND} />
          </div>
          <a
            href={WINDOWS_CHECKSUM_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="mt-3 inline-flex items-center gap-1.5 text-xs font-mono text-[#00ffaa] hover:underline"
          >
            Compare against the published SHA-256 file <ExternalLink className="w-3 h-3" />
          </a>
        </div>

        {/* Footer note */}
        <div className="mt-10 text-center text-xs text-slate-500 font-mono space-y-1">
          <p>
            Having trouble?{" "}
            <a
              href={SITE_CONFIG.github.issuesUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="text-[#00ffaa] hover:underline inline-flex items-center gap-1"
            >
              Open an issue on GitHub <ChevronRight className="w-3 h-3" />
            </a>
          </p>
          <p className="text-slate-600">Stack Pilot is MIT licensed. Free forever.</p>
        </div>
      </div>
    </main>
  );
}
