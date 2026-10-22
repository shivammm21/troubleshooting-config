"use client";

import React from "react";
import Image from "next/image";
import { Monitor, CheckCircle2 } from "lucide-react";

export function HeroToolDisplay() {
  return (
    <div className="w-full max-w-5xl flex flex-col items-center space-y-4">
      {/* Main Container Window */}
      <div className="w-full relative rounded-2xl border border-white/15 bg-[#080d17] shadow-2xl shadow-black/90 overflow-hidden terminal-glow group">
        {/* Native macOS Window Header */}
        <div className="px-4 py-3 bg-[#0a0f19] border-b border-white/10 flex items-center justify-between">
          {/* macOS Window Controls */}
          <div className="flex items-center gap-2">
            <div className="w-3 h-3 rounded-full bg-[#ff5f56] border border-[#e0443e]/50 hover:opacity-80 transition-opacity cursor-pointer" />
            <div className="w-3 h-3 rounded-full bg-[#ffbd2e] border border-[#dea123]/50 hover:opacity-80 transition-opacity cursor-pointer" />
            <div className="w-3 h-3 rounded-full bg-[#27c93f] border border-[#1aab29]/50 hover:opacity-80 transition-opacity cursor-pointer" />
            <span className="ml-3 text-xs font-mono text-slate-400 flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-[#00ffaa] animate-pulse" />
              <span>StackPilot — Docker Environment Manager [macOS]</span>
            </span>
          </div>

          <div className="flex items-center gap-3">
            <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-white/5 text-[#00ffaa] border border-[#00ffaa]/30 hidden sm:inline-flex items-center gap-1">
              <CheckCircle2 className="w-3 h-3" />
              Live Product Screen
            </span>
            <span className="text-[11px] font-mono text-slate-500">v1.0.0</span>
          </div>
        </div>

        {/* Actual Tool Screenshot Display */}
        <div className="relative w-full aspect-[16/10] bg-[#070b14] overflow-hidden">
          <Image
            src="/main-page-tool.png"
            alt="StackPilot Desktop App interface on macOS showing containers, diagnostics, live logs, and root cause analysis"
            fill
            sizes="(max-width: 1280px) 100vw, 1200px"
            className="object-cover object-top transition-transform duration-500 group-hover:scale-[1.01]"
            priority
          />
        </div>

        {/* Bottom subtle bar */}
        <div className="px-4 py-2 bg-[#090e18] border-t border-white/5 flex items-center justify-between text-xs font-mono text-slate-400">
          <div className="flex items-center gap-4">
            <span className="text-emerald-400 flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full bg-emerald-400" />
              Docker Engine: Connected (Port 64202)
            </span>
            <span className="text-slate-600">|</span>
            <span className="text-slate-400">Spring Boot Daemon v1.0.0</span>
          </div>
          <span className="text-slate-500 text-[11px] hidden sm:inline">
            Workspace: desktop-app (8 Containers)
          </span>
        </div>
      </div>
    </div>
  );
}
