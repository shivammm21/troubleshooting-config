import React from "react";
import { FolderGit2, PlayCircle, SearchCode, CheckCircle2, ArrowRight } from "lucide-react";

export function HowItWorks() {
  const steps = [
    {
      step: "01",
      title: "Select a Scenario",
      description: "Choose a supported infrastructure troubleshooting scenario (e.g., Kafka advertised listeners or Redis memory eviction limit).",
      icon: FolderGit2,
      badge: "Scenario Catalog",
      codeSnippet: "stackpilot scenario select kafka-listeners-misconfig",
    },
    {
      step: "02",
      title: "Reproduce the Failure",
      description: "Run the scenario in a controlled, isolated Docker Compose environment with simulated network boundaries or service faults.",
      icon: PlayCircle,
      badge: "Isolation",
      codeSnippet: "stackpilot env up --isolated --fault-injection=on",
    },
    {
      step: "03",
      title: "Investigate Root Cause",
      description: "Review automated diagnostics, correlated multi-container logs, network routing tables, and suggested explanations.",
      icon: SearchCode,
      badge: "Diagnostic Engine",
      codeSnippet: "stackpilot diagnose --inspect-network --deep",
    },
    {
      step: "04",
      title: "Apply and Verify Fix",
      description: "Follow the remediation steps, apply targeted configuration updates, and rerun automated health checks to verify recovery.",
      icon: CheckCircle2,
      badge: "Verification",
      codeSnippet: "stackpilot verify --assert-healthy --strict",
    },
  ];

  return (
    <section id="how-it-works" className="py-20 md:py-28 bg-[#080d16]/70 border-y border-white/5 relative overflow-hidden">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Section Header */}
        <div className="max-w-3xl mx-auto text-center space-y-4 mb-16">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#00ffaa]/10 border border-[#00ffaa]/20 text-[#00ffaa] text-xs font-mono uppercase tracking-wider">
            <span>Deterministic Workflow</span>
          </div>
          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-extrabold text-white tracking-tight">
            How Stack Pilot Works
          </h2>
          <p className="text-base sm:text-lg text-slate-400 leading-relaxed">
            A continuous four-step cycle that turns obscure infrastructure crashes into reproducible, solvable engineering scenarios.
          </p>
        </div>

        {/* 4-Step Connected Process */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 relative">
          {steps.map((item, index) => {
            const Icon = item.icon;
            return (
              <div
                key={index}
                className="relative rounded-2xl bg-[#0c121e] border border-white/5 p-6 flex flex-col justify-between group hover:border-[#00ffaa]/30 transition-all duration-300"
              >
                {/* Step number badge */}
                <div className="flex items-center justify-between mb-4">
                  <span className="text-3xl font-extrabold font-mono text-[#00ffaa]/30 group-hover:text-[#00ffaa] transition-colors">
                    {item.step}
                  </span>
                  <span className="text-[10px] font-mono uppercase px-2 py-0.5 rounded bg-white/5 text-slate-400 border border-white/5">
                    {item.badge}
                  </span>
                </div>

                <div className="space-y-2 mb-4">
                  <div className="flex items-center gap-2">
                    <div className="p-1.5 rounded-lg bg-[#00ffaa]/10 text-[#00ffaa]">
                      <Icon className="w-4 h-4" />
                    </div>
                    <h3 className="text-base font-bold text-white group-hover:text-[#00ffaa] transition-colors">
                      {item.title}
                    </h3>
                  </div>
                  <p className="text-xs text-slate-400 leading-relaxed">
                    {item.description}
                  </p>
                </div>

                {/* Micro Terminal Snippet */}
                <div className="mt-4 pt-3 border-t border-white/5">
                  <div className="rounded-lg bg-[#070a11] p-2 font-mono text-[11px] text-slate-400 flex items-center justify-between border border-white/5">
                    <span className="truncate pr-1">
                      <span className="text-[#00ffaa] mr-1">$</span>
                      {item.codeSnippet}
                    </span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        {/* Interactive workflow summary bar */}
        <div className="mt-12 p-6 rounded-2xl bg-[#0c121e] border border-white/10 flex flex-col md:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="w-3 h-3 rounded-full bg-[#00ffaa] animate-pulse" />
            <span className="text-sm font-medium text-slate-300">
              Scenarios are declaratively defined in standard YAML, allowing your team to version-control failure test suites.
            </span>
          </div>
          <a
            href="#technology"
            className="shrink-0 text-xs font-mono text-[#00ffaa] hover:underline flex items-center gap-1.5 font-semibold"
          >
            <span>Inspect Tech Stack & Schema</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </a>
        </div>
      </div>
    </section>
  );
}
