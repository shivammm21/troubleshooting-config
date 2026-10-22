"use client";

import React, { useState } from "react";
import { Terminal, CheckCircle2, AlertTriangle, ArrowRight, Play, RefreshCw, FileText } from "lucide-react";

export function ProductPreview() {
  const [activeStep, setActiveStep] = useState(0);

  const logs = [
    { type: "INFO", text: "Environment initialized (Isolated bridge network: stackpilot-net)" },
    { type: "WARN", text: "Kafka connectivity check failed: broker port 9092 returned connection reset" },
    { type: "ANALYSIS", text: "Broker address may be misconfigured: advertised.listeners points to unreachable host alias" },
    { type: "ACTION", text: "Verify advertised.listeners and client connectivity: set PLAINTEXT://broker:9092" },
    { type: "INFO", text: "Recovery check completed: 4/4 health assertions passed successfully" },
  ];

  return (
    <section id="preview" className="py-20 md:py-28 bg-[#080d16]/80 border-t border-white/5 relative">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Section Header */}
        <div className="max-w-3xl mx-auto text-center space-y-4 mb-16">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#00ffaa]/10 border border-[#00ffaa]/20 text-[#00ffaa] text-xs font-mono uppercase tracking-wider">
            <span>Terminal &amp; GUI Showcase</span>
          </div>
          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-extrabold text-white tracking-tight">
            See Stack Pilot in Action
          </h2>
          <p className="text-base sm:text-lg text-slate-400 leading-relaxed">
            Real-time diagnostic traces, actionable explanations, and automated recovery verification running in unison.
          </p>
        </div>

        {/* 2-column showcase: Terminal preview on left/top, features list on right/bottom */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
          {/* Showcase Terminal Panel */}
          <div className="lg:col-span-7 rounded-2xl bg-[#0a0f1a] border border-white/10 shadow-2xl overflow-hidden terminal-glow">
            {/* Header */}
            <div className="px-4 py-3 bg-[#060a12] border-b border-white/5 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <div className="w-3 h-3 rounded-full bg-red-500/80" />
                <div className="w-3 h-3 rounded-full bg-yellow-500/80" />
                <div className="w-3 h-3 rounded-full bg-green-500/80" />
                <span className="ml-2 text-xs font-mono text-slate-400">
                  stackpilot-cli — diagnostic execution log
                </span>
              </div>
              <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-white/5 text-amber-300/80 border border-amber-500/20">
                Illustrative sample data
              </span>
            </div>

            {/* Terminal output */}
            <div className="p-6 font-mono text-xs sm:text-sm space-y-3 bg-[#070b13] min-h-[320px]">
              <div className="text-slate-500 pb-2 border-b border-white/5 flex items-center justify-between text-xs">
                <span>$ stackpilot scenario run kafka-broker-unreachable</span>
                <span className="text-slate-600">[PID 4920]</span>
              </div>

              {logs.map((log, index) => {
                let badgeClass = "text-sky-400 bg-sky-500/10 border-sky-500/20";
                if (log.type === "WARN") badgeClass = "text-amber-400 bg-amber-500/10 border-amber-500/20";
                if (log.type === "ANALYSIS") badgeClass = "text-red-400 bg-red-500/10 border-red-500/20";
                if (log.type === "ACTION") badgeClass = "text-[#00ffaa] bg-[#00ffaa]/10 border-[#00ffaa]/20";
                if (log.type === "INFO" && index === logs.length - 1)
                  badgeClass = "text-emerald-400 bg-emerald-500/10 border-emerald-500/20";

                return (
                  <div key={index} className="flex items-start gap-2.5 animate-fadeIn">
                    <span
                      className={`shrink-0 px-2 py-0.5 rounded text-[11px] font-bold border ${badgeClass}`}
                    >
                      [{log.type}]
                    </span>
                    <span className="text-slate-300 leading-relaxed font-mono text-xs sm:text-sm">
                      {log.text}
                    </span>
                  </div>
                );
              })}

              <div className="pt-4 flex items-center gap-2 text-xs text-slate-500">
                <span className="w-2 h-2 rounded-full bg-[#00ffaa] animate-ping" />
                <span>Verification session completed with 0 errors remaining.</span>
              </div>
            </div>

            {/* Status footer */}
            <div className="px-4 py-2.5 bg-[#060a12] border-t border-white/5 flex items-center justify-between text-xs font-mono text-slate-400">
              <span className="flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-emerald-400" />
                <span>Engine: Java Spring Boot</span>
              </span>
              <span className="text-slate-500">Scenario Time: 1.48s</span>
            </div>
          </div>

          {/* Beside Feature Highlights */}
          <div className="lg:col-span-5 space-y-6">
            <div className="space-y-4">
              <div className="p-4 rounded-xl bg-[#0c121e] border border-white/5 hover:border-[#00ffaa]/30 transition-colors">
                <div className="flex items-center gap-3">
                  <div className="p-2 rounded-lg bg-[#00ffaa]/10 text-[#00ffaa]">
                    <CheckCircle2 className="w-5 h-5" />
                  </div>
                  <div>
                    <h3 className="text-sm font-bold text-white">Clear Diagnostic Summaries</h3>
                    <p className="text-xs text-slate-400 mt-0.5">
                      No obscure stack traces. Get clean, categorized fault breakdowns with root-cause likelihood.
                    </p>
                  </div>
                </div>
              </div>

              <div className="p-4 rounded-xl bg-[#0c121e] border border-white/5 hover:border-[#00ffaa]/30 transition-colors">
                <div className="flex items-center gap-3">
                  <div className="p-2 rounded-lg bg-sky-500/10 text-sky-400">
                    <FileText className="w-5 h-5" />
                  </div>
                  <div>
                    <h3 className="text-sm font-bold text-white">Actionable Troubleshooting Guidance</h3>
                    <p className="text-xs text-slate-400 mt-0.5">
                      Clear remediation steps pinpointing the exact config file, line, and environment key to fix.
                    </p>
                  </div>
                </div>
              </div>

              <div className="p-4 rounded-xl bg-[#0c121e] border border-white/5 hover:border-[#00ffaa]/30 transition-colors">
                <div className="flex items-center gap-3">
                  <div className="p-2 rounded-lg bg-amber-500/10 text-amber-400">
                    <RefreshCw className="w-5 h-5" />
                  </div>
                  <div>
                    <h3 className="text-sm font-bold text-white">Reproducible Test Scenarios</h3>
                    <p className="text-xs text-slate-400 mt-0.5">
                      Package infrastructure bugs into deterministic YAML test cases that your entire team can run locally.
                    </p>
                  </div>
                </div>
              </div>

              <div className="p-4 rounded-xl bg-[#0c121e] border border-white/5 hover:border-[#00ffaa]/30 transition-colors">
                <div className="flex items-center gap-3">
                  <div className="p-2 rounded-lg bg-emerald-500/10 text-emerald-400">
                    <Play className="w-5 h-5" />
                  </div>
                  <div>
                    <h3 className="text-sm font-bold text-white">Automated Recovery Verification</h3>
                    <p className="text-xs text-slate-400 mt-0.5">
                      Verify fixes programmatically with assertion suites before merging changes or pushing to staging.
                    </p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
