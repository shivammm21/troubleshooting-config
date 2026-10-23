"use client";

import React from "react";
import Image from "next/image";
import { Monitor } from "lucide-react";

export function HeroToolDisplay() {
  return (
    <div className="w-full max-w-5xl flex flex-col items-center space-y-4">
      {/* Main Container Window */}
      <div className="w-full relative rounded-2xl border border-white/15 bg-[#080d17] shadow-2xl shadow-black/90 overflow-hidden terminal-glow group">

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
