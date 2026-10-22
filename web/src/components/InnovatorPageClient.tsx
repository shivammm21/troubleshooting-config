"use client";

import React from "react";
import {
  Mail,
  Code2,
  Server,
  Layers,
  Wrench,
  GitBranch,
  Terminal,
  ExternalLink,
  Sparkles,
  Heart,
} from "lucide-react";

// Brand icon SVGs (lucide-react removed brand icons)
function GithubIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0 1 12 6.844a9.59 9.59 0 0 1 2.504.337c1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.02 10.02 0 0 0 22 12.017C22 6.484 17.522 2 12 2Z" />
    </svg>
  );
}

function LinkedinIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M20.447 20.452h-3.554v-5.569c0-1.328-.027-3.037-1.852-3.037-1.853 0-2.136 1.445-2.136 2.939v5.667H9.351V9h3.414v1.561h.046c.477-.9 1.637-1.85 3.37-1.85 3.601 0 4.267 2.37 4.267 5.455v6.286ZM5.337 7.433a2.062 2.062 0 0 1-2.063-2.065 2.064 2.064 0 1 1 2.063 2.065Zm1.782 13.019H3.555V9h3.564v11.452ZM22.225 0H1.771C.792 0 0 .774 0 1.729v20.542C0 23.227.792 24 1.771 24h20.451C23.2 24 24 23.227 24 22.271V1.729C24 .774 23.2 0 22.222 0h.003Z" />
    </svg>
  );
}

// ─── Timeline Item ────────────────────────────────────────────────────────────
function TimelineItem({
  year,
  title,
  desc,
  accent,
}: {
  year: string;
  title: string;
  desc: string;
  accent: string;
}) {
  return (
    <div className="flex gap-5 group">
      <div className="flex flex-col items-center">
        <div
          className="w-10 h-10 rounded-full flex items-center justify-center shrink-0 border-2 text-xs font-bold font-mono transition-all group-hover:scale-110"
          style={{
            borderColor: accent,
            color: accent,
            background: `${accent}10`,
          }}
        >
          {year.slice(2)}
        </div>
        <div className="w-px flex-1 mt-2 bg-white/5" />
      </div>
      <div className="pb-10 pt-1.5">
        <p className="text-xs font-mono text-slate-500 mb-1">{year}</p>
        <p className="text-base font-bold text-white mb-1">{title}</p>
        <p className="text-sm text-slate-400 leading-relaxed">{desc}</p>
      </div>
    </div>
  );
}

// ─── Social Link ──────────────────────────────────────────────────────────────
function SocialLink({
  href,
  icon: Icon,
  label,
  handle,
}: {
  href: string;
  icon: React.ElementType;
  label: string;
  handle: string;
}) {
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      className="flex items-center gap-3 px-5 py-3.5 rounded-xl bg-[#0b1120] border border-white/5 hover:border-[#00ffaa]/30 hover:bg-[#00ffaa]/5 transition-all group"
    >
      <Icon className="w-5 h-5 text-slate-400 group-hover:text-[#00ffaa] transition-colors" />
      <div>
        <p className="text-xs text-slate-500">{label}</p>
        <p className="text-sm font-semibold text-slate-200 group-hover:text-white transition-colors">
          {handle}
        </p>
      </div>
      <ExternalLink className="w-3.5 h-3.5 text-slate-600 group-hover:text-[#00ffaa] ml-auto transition-colors" />
    </a>
  );
}

// ─── Main Component ───────────────────────────────────────────────────────────
export function InnovatorPageClient() {
  const timeline = [
    {
      year: "2024",
      title: "Started Stack Pilot",
      desc: "Identified a gap in local infrastructure debugging tooling. Started building Stack Pilot to reproduce and diagnose Docker, Kafka, and Redis failures deterministically.",
      accent: "#00ffaa",
    },
    {
      year: "2024",
      title: "Core Diagnostic Engine",
      desc: "Built the Spring Boot daemon that connects directly to the local Docker socket, inspects container states, and performs automated root-cause heuristics.",
      accent: "#38bdf8",
    },
    {
      year: "2025",
      title: "Multi-Stack Scenario Support",
      desc: "Expanded Stack Pilot to support Kafka advertised listener misconfigurations and Redis memory eviction edge cases, backed by declarative YAML scenario files.",
      accent: "#a78bfa",
    },
    {
      year: "2025",
      title: "Desktop UI & macOS Release",
      desc: "Shipped the full desktop application UI with real-time container monitoring, log inspection, guided remediation flows, and a one-click recovery verifier.",
      accent: "#f59e0b",
    },
    {
      year: "2026",
      title: "Public Beta Launch",
      desc: "Released Stack Pilot as open-source public beta. Building the community of DevOps engineers and developers who debug infrastructure the smart way.",
      accent: "#00ffaa",
    },
  ];

  return (
    <main className="text-white min-h-screen">
      {/* Background glows */}
      <div className="fixed inset-0 pointer-events-none overflow-hidden">
        <div className="absolute top-0 right-1/4 w-[600px] h-[500px] bg-[#00ffaa]/4 rounded-full blur-[180px]" />
        <div className="absolute top-1/3 left-1/4 w-[500px] h-[400px] bg-violet-500/4 rounded-full blur-[160px]" />
        <div className="absolute bottom-0 left-1/2 -translate-x-1/2 w-[700px] h-[400px] bg-sky-500/4 rounded-full blur-[180px]" />
      </div>

      <div className="relative max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-20 md:py-28">

        {/* ── Hero Section ────────────────────────────────── */}
        <div className="flex flex-col md:flex-row items-center md:items-start gap-8 md:gap-10 mb-20">

          {/* Avatar */}
          <div className="relative shrink-0 w-full md:w-auto flex justify-center md:block">
            <div className="w-48 h-56 sm:w-56 sm:h-64 md:w-80 md:h-[420px] rounded-3xl border border-[#00ffaa]/20 shadow-2xl shadow-[#00ffaa]/10 overflow-hidden">
              <img
                src="/innovator.png"
                alt="Shivam Thorat"
                className="w-full h-full object-cover object-top"
              />
            </div>
            {/* Online indicator */}
            <div className="absolute -bottom-2 -right-2 flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-[#0b1120] border border-[#00ffaa]/30 text-[10px] font-mono text-[#00ffaa]">
              <span className="w-1.5 h-1.5 rounded-full bg-[#00ffaa] animate-pulse" />
              Building
            </div>
          </div>

          {/* Info */}
          <div className="flex-1 text-center md:text-left space-y-4">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#00ffaa]/10 border border-[#00ffaa]/20 text-[#00ffaa] text-xs font-mono">
              <Sparkles className="w-3.5 h-3.5" />
              Creator & Innovator
            </div>

            <h1 className="text-3xl sm:text-4xl md:text-5xl font-extrabold text-white tracking-tight">
              Shivam{" "}
              <span className="text-transparent bg-clip-text bg-gradient-to-r from-[#00ffaa] via-[#38bdf8] to-emerald-300">
                Thorat
              </span>
            </h1>

            <p className="text-lg text-slate-400 font-medium">
              Software Engineer · Infrastructure Tooling · Open Source
            </p>

            <p className="text-base text-slate-300 leading-relaxed max-w-2xl">
              I'm a software engineer passionate about building developer tools that make the
              hard parts of infrastructure work feel simple. I created{" "}
              <span className="text-[#00ffaa] font-semibold">Stack Pilot</span> because I
              experienced firsthand how painful it is to debug Kafka misconfigurations, Redis
              memory pressure, and Docker networking failures without the right tooling.
            </p>

            <p className="text-base text-slate-400 leading-relaxed max-w-2xl">
              Stack Pilot is my answer to that problem — a deterministic, local-first tool
              that helps engineers reproduce, inspect, and fix infrastructure failures fast.
              I believe great developer tooling should remove friction, not add it.
            </p>

            {/* Social links */}
            <div className="grid grid-cols-1 sm:grid-cols-2 md:flex md:flex-wrap items-center justify-center md:justify-start gap-3 pt-2 w-full">
              <SocialLink
                href="https://github.com/shivammm21"
                icon={GithubIcon}
                label="GitHub"
                handle="@shivammm21"
              />
              <SocialLink
                href="https://linkedin.com/in/shivam-thorat"
                icon={LinkedinIcon}
                label="LinkedIn"
                handle="Shivam Thorat"
              />
              <SocialLink
                href="mailto:shivamthorat2103@gmail.com"
                icon={Mail}
                label="Email"
                handle="shivamthorat2103@gmail.com"
              />
            </div>
          </div>
        </div>

        {/* ── About Stack Pilot ─────────────────────────────── */}
        <section className="mb-20">
          <div className="flex items-center gap-3 mb-8">
            <div className="w-8 h-8 rounded-lg bg-[#00ffaa]/10 flex items-center justify-center">
              <Wrench className="w-4 h-4 text-[#00ffaa]" />
            </div>
            <h2 className="text-2xl font-extrabold text-white">Why I Built Stack Pilot</h2>
          </div>

          <div className="grid md:grid-cols-2 gap-5">
            {[
              {
                icon: Server,
                color: "#38bdf8",
                title: "The Problem",
                body: "Debugging infrastructure failures like Docker container DNS drops, Kafka advertised listener misconfigurations, and Redis memory evictions is notoriously painful. Engineers spend hours chasing logs that don't tell the full story.",
              },
              {
                icon: Layers,
                color: "#00ffaa",
                title: "The Solution",
                body: "Stack Pilot creates a controlled environment to reproduce the exact failure, runs automated heuristics to identify the root cause, and walks you through a verified remediation — all locally, with zero cloud dependencies.",
              },
              {
                icon: Code2,
                color: "#a78bfa",
                title: "The Architecture",
                body: "A Spring Boot daemon connects to the Docker socket, managing ephemeral containers via Docker Compose. Toxiproxy injects controlled network faults. The UI presents correlated diagnostics in a clean, actionable interface.",
              },
              {
                icon: Heart,
                color: "#f59e0b",
                title: "The Philosophy",
                body: "Great tooling should feel invisible. Stack Pilot is designed to reduce cognitive load — not add configuration overhead. Every feature is built around a real debugging scenario that engineers actually face.",
              },
            ].map(({ icon: Icon, color, title, body }) => (
              <div
                key={title}
                className="p-6 rounded-2xl bg-[#0b1120] border border-white/5 hover:border-white/10 transition-all"
              >
                <div
                  className="w-10 h-10 rounded-xl flex items-center justify-center mb-4"
                  style={{ background: `${color}15` }}
                >
                  <Icon className="w-5 h-5" style={{ color }} />
                </div>
                <h3 className="text-base font-bold text-white mb-2">{title}</h3>
                <p className="text-sm text-slate-400 leading-relaxed">{body}</p>
              </div>
            ))}
          </div>
        </section>

        {/* ── Timeline ─────────────────────────────────────── */}
        <section className="mb-20">
          <div className="flex items-center gap-3 mb-8">
            <div className="w-8 h-8 rounded-lg bg-violet-500/10 flex items-center justify-center">
              <GitBranch className="w-4 h-4 text-violet-400" />
            </div>
            <h2 className="text-2xl font-extrabold text-white">Stack Pilot Journey</h2>
          </div>

          <div>
            {timeline.map((item) => (
              <TimelineItem key={item.title} {...item} />
            ))}
          </div>
        </section>

        {/* ── CTA ──────────────────────────────────────────── */}
        <section className="rounded-3xl bg-gradient-to-br from-[#0d1b2a] via-[#0b1120] to-[#070b12] border border-[#00ffaa]/15 p-8 md:p-12 text-center relative overflow-hidden">
          <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[400px] h-[200px] bg-[#00ffaa]/5 rounded-full blur-[100px] pointer-events-none" />
          <div className="relative">
            <p className="text-xs font-mono text-[#00ffaa] uppercase tracking-widest mb-4">
              Let's Connect
            </p>
            <h2 className="text-2xl sm:text-3xl font-extrabold text-white mb-4">
              Interested in Stack Pilot or want to collaborate?
            </h2>
            <p className="text-slate-400 text-base max-w-xl mx-auto mb-8">
              Whether you have a bug report, a feature idea, or just want to talk
              infrastructure tooling — I'd love to hear from you.
            </p>
            <div className="flex flex-wrap items-center justify-center gap-4">
              <a
                href="https://github.com/shivammm21/troubleshooting-config"
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center gap-2 px-6 py-3 rounded-xl bg-[#00ffaa] text-[#070b12] font-bold text-sm hover:bg-[#00e599] transition-all shadow-lg shadow-[#00ffaa]/20 active:scale-95"
              >
                <GithubIcon className="w-4 h-4" />
                View on GitHub
              </a>
              <a
                href="mailto:shivamthorat2103@gmail.com"
                className="flex items-center gap-2 px-6 py-3 rounded-xl bg-white/5 border border-white/10 text-slate-200 font-semibold text-sm hover:border-[#00ffaa]/30 hover:bg-[#00ffaa]/5 hover:text-white transition-all"
              >
                <Mail className="w-4 h-4" />
                Send a message
              </a>
            </div>
          </div>
        </section>

      </div>
    </main>
  );
}
