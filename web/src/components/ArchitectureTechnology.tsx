import React from "react";
import { Layers, Server, Database, Radio, Cpu, Code2, Network, ShieldCheck } from "lucide-react";

export function ArchitectureTechnology() {
  const technologies = [
    {
      category: "Container Orchestration",
      name: "Docker & Docker Compose",
      description: "Provides isolated container runtimes, bridge networking, and local multi-service composition.",
      status: "Integrated",
      statusColor: "text-emerald-400 bg-emerald-500/10 border-emerald-500/20",
      icon: Layers,
    },
    {
      category: "Message Streaming",
      name: "Apache Kafka",
      description: "Investigate partition leaders, broker listener configs, zookeeper/kraft coordination, and consumer group lags.",
      status: "Integrated",
      statusColor: "text-emerald-400 bg-emerald-500/10 border-emerald-500/20",
      icon: Radio,
    },
    {
      category: "In-Memory Store",
      name: "Redis",
      description: "Diagnose connection drops, memory exhaustion, eviction policies, and cluster failovers.",
      status: "Integrated",
      statusColor: "text-emerald-400 bg-emerald-500/10 border-emerald-500/20",
      icon: Database,
    },
    {
      category: "Network Fault-Injection",
      name: "Toxiproxy",
      description: "Simulate latency, bandwidth limitations, TCP slice drops, and connection reset faults in test scenarios.",
      status: "Integrated",
      statusColor: "text-emerald-400 bg-emerald-500/10 border-emerald-500/20",
      icon: Network,
    },
    {
      category: "Frontend UI",
      name: "React & Next.js",
      description: "Fast, responsive web dashboard offering real-time telemetry, visual logs, and scenario runners.",
      status: "Integrated",
      statusColor: "text-emerald-400 bg-emerald-500/10 border-emerald-500/20",
      icon: Code2,
    },
    {
      category: "Backend Engine",
      name: "Java Spring Boot",
      description: "High-performance diagnostic daemon managing Docker sockets, process supervisors, and scenario lifecycles.",
      status: "Integrated",
      statusColor: "text-emerald-400 bg-emerald-500/10 border-emerald-500/20",
      icon: Server,
    },
    {
      category: "Local State & History",
      name: "SQLite",
      description: "Zero-dependency embedded database for storing local troubleshooting run histories and scenario metadata.",
      status: "Integrated",
      statusColor: "text-emerald-400 bg-emerald-500/10 border-emerald-500/20",
      icon: Database,
    },
    {
      category: "Declarative Scenarios",
      name: "YAML Configuration",
      description: "Define custom infrastructure fault scenarios, health assertions, and remediation steps in standard YAML.",
      status: "Integrated",
      statusColor: "text-emerald-400 bg-emerald-500/10 border-emerald-500/20",
      icon: Cpu,
    },
  ];

  return (
    <section id="technology" className="py-20 md:py-28 relative">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Section Header */}
        <div className="max-w-3xl mx-auto text-center space-y-4 mb-16">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#00ffaa]/10 border border-[#00ffaa]/20 text-[#00ffaa] text-xs font-mono uppercase tracking-wider">
            <span>Stack Architecture</span>
          </div>
          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-extrabold text-white tracking-tight">
            Built for modern infrastructure workflows.
          </h2>
          <p className="text-base sm:text-lg text-slate-400 leading-relaxed">
            Stack Pilot couples robust battle-tested open-source tooling with a decoupled architecture. Here is our architectural breakdown and active integrations.
          </p>
        </div>

        {/* Architecture Diagram Preview Card */}
        <div className="mb-12 p-6 sm:p-8 rounded-2xl bg-[#0c121e] border border-white/10 relative overflow-hidden">
          <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4 pb-6 border-b border-white/5 mb-6">
            <div>
              <h3 className="text-base font-bold text-white flex items-center gap-2">
                <span>Architecture Diagram</span>
                <span className="text-xs font-mono text-slate-400 px-2 py-0.5 rounded bg-white/5">
                  Decoupled Daemon & Client
                </span>
              </h3>
              <p className="text-xs text-slate-400 mt-1">
                Local lightweight client communicates over UNIX socket / HTTP with local Spring Boot daemon orchestrating Docker and Toxiproxy.
              </p>
            </div>
            <div className="flex items-center gap-2 text-xs font-mono">
              <span className="inline-block w-2.5 h-2.5 rounded-full bg-emerald-400" />
              <span className="text-slate-300">Active Integrated Components</span>
            </div>
          </div>

          {/* Architecture Blocks Grid */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 font-mono text-xs">
            {/* Layer 1: Client */}
            <div className="p-4 rounded-xl bg-[#080d15] border border-sky-500/20 space-y-2">
              <div className="text-sky-400 font-bold flex items-center justify-between">
                <span>1. Presentation Layer</span>
                <span className="text-[10px] bg-sky-500/10 px-1.5 py-0.5 rounded">UI / CLI</span>
              </div>
              <p className="text-slate-400 text-[11px] font-sans">
                React Web Interface &amp; terminal CLI client for managing runs, viewing logs, and running interactive verification.
              </p>
            </div>

            {/* Layer 2: Daemon Core */}
            <div className="p-4 rounded-xl bg-[#080d15] border border-[#00ffaa]/30 space-y-2 relative shadow-lg shadow-[#00ffaa]/5">
              <div className="text-[#00ffaa] font-bold flex items-center justify-between">
                <span>2. Engine Core (Daemon)</span>
                <span className="text-[10px] bg-[#00ffaa]/15 text-[#00ffaa] px-1.5 py-0.5 rounded">Spring Boot</span>
              </div>
              <p className="text-slate-400 text-[11px] font-sans">
                Controls scenario lifecycle, interfaces with Docker API socket, manages Toxiproxy, and stores test runs in local SQLite.
              </p>
            </div>

            {/* Layer 3: Managed Target Environment */}
            <div className="p-4 rounded-xl bg-[#080d15] border border-amber-500/20 space-y-2">
              <div className="text-amber-400 font-bold flex items-center justify-between">
                <span>3. Isolated Sandbox</span>
                <span className="text-[10px] bg-amber-500/10 px-1.5 py-0.5 rounded">Docker Compose</span>
              </div>
              <p className="text-slate-400 text-[11px] font-sans">
                Ephemeral containers running Kafka, Redis, and app dependencies with fault injection proxies.
              </p>
            </div>
          </div>
        </div>

        {/* Technology Cards Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          {technologies.map((tech, index) => {
            const Icon = tech.icon;
            return (
              <div
                key={index}
                className="p-5 rounded-xl bg-[#0c121e] border border-white/5 hover:border-white/15 transition-all flex flex-col justify-between"
              >
                <div>
                  <div className="flex items-center justify-between mb-3">
                    <div className="p-2 rounded-lg bg-white/5 text-slate-300">
                      <Icon className="w-4 h-4" />
                    </div>
                    <span className={`text-[10px] font-mono uppercase px-2 py-0.5 rounded border ${tech.statusColor}`}>
                      {tech.status}
                    </span>
                  </div>
                  <span className="text-[11px] font-mono text-slate-400 block mb-1">
                    {tech.category}
                  </span>
                  <h4 className="text-sm font-bold text-white mb-2">{tech.name}</h4>
                  <p className="text-xs text-slate-400 leading-relaxed font-sans">
                    {tech.description}
                  </p>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
