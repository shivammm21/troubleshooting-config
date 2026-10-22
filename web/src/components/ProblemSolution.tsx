import React from "react";
import { AlertCircle, Network, MessageSquare, Database, FileText, CheckCircle2, ArrowRight } from "lucide-react";

export function ProblemSolution() {
  const challenges = [
    {
      icon: AlertCircle,
      title: "Difficult-to-reproduce bugs",
      desc: "Infrastructure failures happen intermittently in staging or production, but are nearly impossible to recreate cleanly on local workstations.",
    },
    {
      icon: Network,
      title: "Unclear container networking",
      desc: "Docker bridge networks, internal DNS lookups, port mappings, and host resolution failures create silent black-box communication drops.",
    },
    {
      icon: MessageSquare,
      title: "Misconfigured messaging brokers",
      desc: "Apache Kafka listener configurations (advertised.listeners vs listeners), partition rebalances, and coordinator timeouts lead to cryptic errors.",
    },
    {
      icon: Database,
      title: "Database & cache resource limits",
      desc: "Redis memory exhaustion, eviction spikes, connection pool starvation, and latency spikes break downstream microservices unexpectedly.",
    },
    {
      icon: FileText,
      title: "Time-consuming manual log analysis",
      desc: "Engineers spend hours correlating gigabytes of asynchronous log lines across multiple containers just to uncover a single missing environment variable.",
    },
  ];

  return (
    <section className="py-20 md:py-28 relative border-t border-white/5 bg-[#080d16]/50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Section Header */}
        <div className="max-w-3xl mx-auto text-center space-y-4 mb-16">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-red-500/10 border border-red-500/20 text-red-400 text-xs font-mono uppercase tracking-wider">
            <span>The Troubleshooting Bottleneck</span>
          </div>
          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-extrabold text-white tracking-tight">
            Infrastructure failures shouldn&apos;t slow down your team.
          </h2>
          <p className="text-base sm:text-lg text-slate-400 leading-relaxed">
            Debugging distributed local environments is notoriously difficult. Stack Pilot gives your team a structured, repeatable way to reproduce failures, find root causes, and verify resolutions.
          </p>
        </div>

        {/* Comparison: The Pain Points vs Stack Pilot Solution */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-stretch">
          {/* Challenges column */}
          <div className="lg:col-span-6 space-y-3.5">
            <h3 className="text-sm font-mono text-slate-400 uppercase tracking-wider px-1">
              Common Developer Frustrations
            </h3>
            {challenges.map((item, index) => {
              const Icon = item.icon;
              return (
                <div
                  key={index}
                  className="p-4 rounded-xl bg-[#0c121e] border border-white/5 hover:border-red-500/30 transition-colors flex items-start gap-4 group"
                >
                  <div className="p-2 rounded-lg bg-red-500/10 text-red-400 group-hover:bg-red-500/20 transition-colors shrink-0">
                    <Icon className="w-4 h-4" />
                  </div>
                  <div>
                    <h4 className="text-sm font-semibold text-white group-hover:text-red-300 transition-colors">
                      {item.title}
                    </h4>
                    <p className="text-xs text-slate-400 mt-1 leading-relaxed">
                      {item.desc}
                    </p>
                  </div>
                </div>
              );
            })}
          </div>

          {/* Solution column */}
          <div className="lg:col-span-6 flex flex-col justify-between p-6 sm:p-8 rounded-2xl bg-gradient-to-b from-[#0e1726] to-[#0a101b] border border-[#00ffaa]/20 relative overflow-hidden shadow-xl shadow-black/40">
            {/* Ambient accent */}
            <div className="absolute top-0 right-0 w-64 h-64 bg-[#00ffaa]/5 rounded-full blur-3xl pointer-events-none" />

            <div className="space-y-6 relative z-10">
              <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#00ffaa]/10 border border-[#00ffaa]/30 text-[#00ffaa] text-xs font-mono">
                <span>The Stack Pilot Approach</span>
              </div>

              <h3 className="text-2xl sm:text-3xl font-bold text-white tracking-tight">
                Deterministic, isolated troubleshooting workflows.
              </h3>

              <p className="text-slate-300 text-sm sm:text-base leading-relaxed">
                Rather than blindly restarting containers or changing random configurations until errors disappear, Stack Pilot empowers teams to tackle infrastructure failures systematically:
              </p>

              <div className="space-y-4 pt-2">
                <div className="flex items-start gap-3">
                  <div className="p-1 rounded bg-[#00ffaa]/15 text-[#00ffaa] mt-0.5">
                    <CheckCircle2 className="w-4 h-4" />
                  </div>
                  <div>
                    <h4 className="text-sm font-semibold text-white">Controlled Failure Injection</h4>
                    <p className="text-xs text-slate-400 mt-0.5">
                      Spin up supported environments with isolated chaos injection without risking shared staging resources.
                    </p>
                  </div>
                </div>

                <div className="flex items-start gap-3">
                  <div className="p-1 rounded bg-[#00ffaa]/15 text-[#00ffaa] mt-0.5">
                    <CheckCircle2 className="w-4 h-4" />
                  </div>
                  <div>
                    <h4 className="text-sm font-semibold text-white">Automated Cross-Service Inspection</h4>
                    <p className="text-xs text-slate-400 mt-0.5">
                      Instantly cross-reference container networks, process logs, port bindings, and protocol health.
                    </p>
                  </div>
                </div>

                <div className="flex items-start gap-3">
                  <div className="p-1 rounded bg-[#00ffaa]/15 text-[#00ffaa] mt-0.5">
                    <CheckCircle2 className="w-4 h-4" />
                  </div>
                  <div>
                    <h4 className="text-sm font-semibold text-white">Continuous Recovery Verification</h4>
                    <p className="text-xs text-slate-400 mt-0.5">
                      Verify that applied fixes actually resolve the underlying issue by re-running deterministic health checks.
                    </p>
                  </div>
                </div>
              </div>
            </div>

            <div className="pt-8 border-t border-white/10 mt-6 flex items-center justify-between text-xs text-slate-400 font-mono">
              <span>Reproduce → Investigate → Remediate → Validate</span>
              <a
                href="#how-it-works"
                className="text-[#00ffaa] hover:underline flex items-center gap-1 font-sans font-semibold"
              >
                See Workflow <ArrowRight className="w-3.5 h-3.5" />
              </a>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
