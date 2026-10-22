import React from "react";
import {
  Flame,
  Search,
  Box,
  Radio,
  Database,
  Wrench,
  CheckCircle,
  Terminal,
  Sparkles,
} from "lucide-react";

interface FeatureCardProps {
  icon: React.ElementType;
  title: string;
  description: string;
  badge?: string;
  accentColor?: string;
}

function FeatureCard({ icon: Icon, title, description, badge, accentColor = "text-[#00ffaa]" }: FeatureCardProps) {
  return (
    <div className="relative group p-6 rounded-2xl bg-[#0c121e] border border-white/5 hover:border-white/15 transition-all duration-300 hover:shadow-xl hover:shadow-black/50 flex flex-col justify-between">
      {/* Subtle corner highlight */}
      <div className="absolute top-0 right-0 w-24 h-24 bg-gradient-to-bl from-white/[0.03] to-transparent rounded-tr-2xl pointer-events-none" />

      <div>
        <div className="flex items-center justify-between mb-4">
          <div className="w-10 h-10 rounded-xl bg-white/5 border border-white/10 flex items-center justify-center group-hover:scale-105 group-hover:border-[#00ffaa]/40 transition-all">
            <Icon className={`w-5 h-5 ${accentColor}`} />
          </div>
          {badge && (
            <span className="text-[10px] font-mono uppercase tracking-wider px-2 py-0.5 rounded bg-white/5 text-slate-400 border border-white/5">
              {badge}
            </span>
          )}
        </div>

        <h3 className="text-lg font-bold text-white tracking-tight mb-2 group-hover:text-[#00ffaa] transition-colors">
          {title}
        </h3>

        <p className="text-sm text-slate-400 leading-relaxed font-normal">
          {description}
        </p>
      </div>

      <div className="pt-4 mt-4 border-t border-white/5 flex items-center justify-between text-xs font-mono text-slate-500">
        <span>Subsystem Tool</span>
        <span className="text-[#00ffaa] opacity-0 group-hover:opacity-100 transition-opacity flex items-center gap-1">
          Explore &rarr;
        </span>
      </div>
    </div>
  );
}

export function Features() {
  const features = [
    {
      icon: Flame,
      title: "Failure Simulation",
      description:
        "Reproduce supported infrastructure failures in an isolated, controlled environment without affecting shared staging clusters or local state.",
      badge: "Chaos Engine",
      accentColor: "text-amber-400",
    },
    {
      icon: Search,
      title: "Root-Cause Analysis",
      description:
        "Inspect relevant logs, configuration, connectivity, and service health to help identify likely root causes with actionable explanations.",
      badge: "Diagnostics",
      accentColor: "text-[#00ffaa]",
    },
    {
      icon: Box,
      title: "Docker Troubleshooting",
      description:
        "Investigate container failures, service discovery, internal networking, DNS lookup drops, and compose configuration issues.",
      badge: "Containers",
      accentColor: "text-sky-400",
    },
    {
      icon: Radio,
      title: "Kafka Diagnostics",
      description:
        "Help investigate broker connectivity, advertised.listeners configuration problems, topic health, and cluster leader partition issues.",
      badge: "Brokers",
      accentColor: "text-indigo-400",
    },
    {
      icon: Database,
      title: "Redis Diagnostics",
      description:
        "Help diagnose supported cache connectivity, memory thresholds, eviction behaviors, and resource-related degradation issues.",
      badge: "In-Memory Caches",
      accentColor: "text-rose-400",
    },
    {
      icon: Wrench,
      title: "Guided Remediation",
      description:
        "Provide actionable troubleshooting suggestions and precise configuration patches based on detected infrastructure issues.",
      badge: "Heuristics",
      accentColor: "text-[#00ffaa]",
    },
    {
      icon: CheckCircle,
      title: "Recovery Verification",
      description:
        "Re-run relevant health checks and network probes to verify whether a proposed fix resolved the problem before committing code.",
      badge: "Verification",
      accentColor: "text-emerald-400",
    },
    {
      icon: Terminal,
      title: "Developer-Friendly Workflow",
      description:
        "Offer clear diagnostic results, structured JSON/YAML logs, and reproducible troubleshooting scenarios runnable via CLI or Web UI.",
      badge: "DX First",
      accentColor: "text-cyan-400",
    },
  ];

  return (
    <section id="features" className="py-20 md:py-28 relative">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="max-w-3xl mx-auto text-center space-y-4 mb-16">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#00ffaa]/10 border border-[#00ffaa]/20 text-[#00ffaa] text-xs font-mono uppercase tracking-wider">
            <span>Comprehensive Toolkit</span>
          </div>
          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-extrabold text-white tracking-tight">
            Engineered to demystify complex infrastructure.
          </h2>
          <p className="text-base sm:text-lg text-slate-400 leading-relaxed">
            Stack Pilot arms developers with purpose-built diagnostics designed for modern containerized stacks, message brokers, and distributed services.
          </p>
        </div>

        {/* Feature Cards Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          {features.map((feature, idx) => (
            <FeatureCard key={idx} {...feature} />
          ))}
        </div>
      </div>
    </section>
  );
}
