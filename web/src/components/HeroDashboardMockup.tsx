"use client";

import React, { useState } from "react";
import {
  Server,
  Activity,
  AlertTriangle,
  CheckCircle2,
  Cpu,
  Database,
  Layers,
  Terminal,
  RotateCw,
  Search,
  ExternalLink,
  ChevronRight,
  ShieldCheck,
} from "lucide-react";

export function HeroDashboardMockup() {
  const [activeTab, setActiveTab] = useState<"overview" | "diagnostics" | "remediation">("overview");
  const [isVerifying, setIsVerifying] = useState(false);
  const [fixApplied, setFixApplied] = useState(false);

  const handleApplyFix = () => {
    setIsVerifying(true);
    setTimeout(() => {
      setIsVerifying(false);
      setFixApplied(true);
    }, 1200);
  };

  const handleReset = () => {
    setFixApplied(false);
    setIsVerifying(false);
  };

  return (
    <div className="relative rounded-2xl border border-white/10 bg-[#0c121e]/90 shadow-2xl shadow-black/80 backdrop-blur-xl overflow-hidden terminal-glow">
      {/* Window Title Bar */}
      <div className="px-4 py-3 bg-[#080d16] border-b border-white/5 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-full bg-red-500/80" />
          <div className="w-3 h-3 rounded-full bg-yellow-500/80" />
          <div className="w-3 h-3 rounded-full bg-green-500/80" />
          <span className="ml-3 text-xs font-mono text-slate-400 flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-[#00ffaa] animate-pulse" />
            stackpilot-cluster-preview [isolated-env: docker-compose]
          </span>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-white/5 text-slate-400 border border-white/5">
            Illustrative Mockup Preview
          </span>
        </div>
      </div>

      {/* Mockup Sub-header Navigation */}
      <div className="px-4 py-2.5 bg-[#090f19] border-b border-white/5 flex items-center justify-between text-xs font-mono">
        <div className="flex items-center gap-1">
          <button
            onClick={() => setActiveTab("overview")}
            className={`px-3 py-1 rounded transition-colors ${
              activeTab === "overview"
                ? "bg-[#00ffaa]/15 text-[#00ffaa] border border-[#00ffaa]/30"
                : "text-slate-400 hover:text-white"
            }`}
          >
            Topology & Health
          </button>
          <button
            onClick={() => setActiveTab("diagnostics")}
            className={`px-3 py-1 rounded transition-colors ${
              activeTab === "diagnostics"
                ? "bg-[#00ffaa]/15 text-[#00ffaa] border border-[#00ffaa]/30"
                : "text-slate-400 hover:text-white"
            }`}
          >
            Root Cause Logs (4)
          </button>
          <button
            onClick={() => setActiveTab("remediation")}
            className={`px-3 py-1 rounded transition-colors ${
              activeTab === "remediation"
                ? "bg-[#00ffaa]/15 text-[#00ffaa] border border-[#00ffaa]/30"
                : "text-slate-400 hover:text-white"
            }`}
          >
            Remediation Plan
          </button>
        </div>

        <button
          onClick={handleReset}
          title="Reset simulation state"
          className="text-slate-500 hover:text-slate-300 transition-colors p-1"
        >
          <RotateCw className="w-3.5 h-3.5" />
        </button>
      </div>

      {/* Main Content Area */}
      <div className="p-4 sm:p-5 space-y-4 text-xs font-sans">
        {/* Service Cards Grid */}
        <div className="grid grid-cols-3 gap-2.5">
          {/* Docker Container Status */}
          <div className="p-3 rounded-xl bg-[#0e1626] border border-white/5 flex flex-col justify-between">
            <div className="flex items-center justify-between mb-2">
              <span className="text-[11px] font-mono text-slate-400 flex items-center gap-1">
                <Layers className="w-3.5 h-3.5 text-sky-400" />
                Containers
              </span>
              <span className="text-[10px] px-1.5 py-0.5 rounded bg-emerald-500/10 text-emerald-400 font-mono">
                3/4 UP
              </span>
            </div>
            <div className="space-y-1 text-[11px] font-mono">
              <div className="flex items-center justify-between text-slate-300">
                <span>api-gateway</span>
                <span className="text-emerald-400">● 200 OK</span>
              </div>
              <div className="flex items-center justify-between text-slate-300">
                <span>kafka-broker</span>
                <span className={fixApplied ? "text-emerald-400" : "text-amber-400"}>
                  {fixApplied ? "● HEALTHY" : "▲ DEGRADED"}
                </span>
              </div>
              <div className="flex items-center justify-between text-slate-300">
                <span>redis-cache</span>
                <span className="text-emerald-400">● HEALTHY</span>
              </div>
            </div>
          </div>

          {/* Kafka Diagnostics */}
          <div className={`p-3 rounded-xl border transition-all ${
            fixApplied
              ? "bg-[#0e1626] border-emerald-500/20"
              : "bg-[#181512] border-amber-500/30"
          }`}>
            <div className="flex items-center justify-between mb-2">
              <span className="text-[11px] font-mono text-slate-400 flex items-center gap-1">
                <Activity className="w-3.5 h-3.5 text-amber-400" />
                Kafka Cluster
              </span>
              <span className={`text-[10px] px-1.5 py-0.5 rounded font-mono ${
                fixApplied
                  ? "bg-emerald-500/10 text-emerald-400"
                  : "bg-amber-500/15 text-amber-300"
              }`}>
                {fixApplied ? "CONNECTED" : "PORT UNREACH"}
              </span>
            </div>
            <p className="text-[11px] text-slate-300 leading-tight">
              {fixApplied
                ? "Broker 9092 advertised listeners validated."
                : "Leader not available for partition [events-v1]."}
            </p>
          </div>

          {/* Redis Cache Health */}
          <div className="p-3 rounded-xl bg-[#0e1626] border border-white/5">
            <div className="flex items-center justify-between mb-2">
              <span className="text-[11px] font-mono text-slate-400 flex items-center gap-1">
                <Database className="w-3.5 h-3.5 text-red-400" />
                Redis Cache
              </span>
              <span className="text-[10px] px-1.5 py-0.5 rounded bg-emerald-500/10 text-emerald-400 font-mono">
                12ms LAT
              </span>
            </div>
            <p className="text-[11px] text-slate-300 leading-tight">
              Mem: 142MB / 512MB
              <br />
              Hit ratio: 98.4%
            </p>
          </div>
        </div>

        {/* Dynamic Issue & Root-Cause Analysis Panel */}
        <div className={`p-3.5 rounded-xl border transition-all ${
          fixApplied
            ? "bg-emerald-950/20 border-emerald-500/30"
            : "bg-red-950/25 border-red-500/30"
        }`}>
          <div className="flex items-start justify-between">
            <div className="flex items-start gap-2.5">
              <div className={`p-1.5 rounded-lg mt-0.5 ${
                fixApplied ? "bg-emerald-500/20 text-emerald-400" : "bg-red-500/20 text-red-400"
              }`}>
                {fixApplied ? (
                  <CheckCircle2 className="w-4 h-4" />
                ) : (
                  <AlertTriangle className="w-4 h-4" />
                )}
              </div>
              <div>
                <h4 className="font-semibold text-white text-xs flex items-center gap-2">
                  {fixApplied
                    ? "Remediation Successful — Cluster Restored"
                    : "Fault Detected: Kafka Advertised Listeners Mismatch"}
                  <span className="text-[10px] font-mono text-slate-400 bg-black/40 px-1.5 py-0.2 rounded">
                    SCENARIO-KFK-04
                  </span>
                </h4>
                <p className="text-slate-300 text-[11px] mt-0.5">
                  {fixApplied
                    ? "KAFKA_ADVERTISED_LISTENERS set to PLAINTEXT://broker:9092. Consumers reconnecting smoothly."
                    : "Client at service-api failed to resolve host localhost:9092 from internal Docker bridge network."}
                </p>
              </div>
            </div>

            {/* Quick action button inside mockup */}
            {!fixApplied ? (
              <button
                onClick={handleApplyFix}
                disabled={isVerifying}
                className="shrink-0 ml-3 px-3 py-1.5 rounded-lg bg-[#00ffaa] hover:bg-[#00e599] text-[#070b12] font-semibold text-[11px] flex items-center gap-1.5 shadow-sm transition-all"
              >
                {isVerifying ? (
                  <>
                    <RotateCw className="w-3 h-3 animate-spin" />
                    <span>Verifying...</span>
                  </>
                ) : (
                  <>
                    <ShieldCheck className="w-3.5 h-3.5" />
                    <span>Apply Patch</span>
                  </>
                )}
              </button>
            ) : (
              <span className="shrink-0 text-[10px] font-mono text-emerald-400 bg-emerald-500/10 px-2 py-1 rounded border border-emerald-500/20">
                HEALTH CHECK PASS
              </span>
            )}
          </div>
        </div>

        {/* Live Terminal Log Stream Mockup */}
        <div className="rounded-xl bg-[#070b13] p-3 border border-white/5 font-mono text-[11px] space-y-1.5 text-slate-400">
          <div className="flex items-center justify-between text-[10px] text-slate-500 pb-1 border-b border-white/5">
            <span className="flex items-center gap-1.5 text-slate-400">
              <Terminal className="w-3 h-3 text-[#00ffaa]" />
              DIAGNOSTIC TRACE BUFFER
            </span>
            <span>Toxiproxy Injector: IDLE</span>
          </div>
          <div className="text-slate-500">[0.00s] [SYSTEM] Stack Pilot Daemon v0.4.2 started</div>
          <div className="text-sky-400/90">[0.14s] [DOCKER] Inspecting compose bridge net: app_default</div>
          {fixApplied ? (
            <>
              <div className="text-emerald-400 font-medium">
                [0.42s] [REMEDIATION] Applied: KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092
              </div>
              <div className="text-emerald-400 font-medium">
                [0.78s] [HEALTH] Broker ping: 2ms RTT — Recovery confirmed.
              </div>
            </>
          ) : (
            <>
              <div className="text-amber-400">
                [0.31s] [PROBE] Dial tcp 127.0.0.1:9092: connect: connection refused
              </div>
              <div className="text-red-400 font-medium">
                [0.35s] [ROOT-CAUSE] Docker container cannot resolve host alias from internal network
              </div>
              <div className="text-[#00ffaa]">
                [SUGGESTION] Update config to use container DNS alias or bridge gateway IP.
              </div>
            </>
          )}
        </div>
      </div>

      {/* Mockup Footer bar */}
      <div className="px-4 py-2 bg-[#090f19] border-t border-white/5 flex items-center justify-between text-[11px] font-mono text-slate-400">
        <div className="flex items-center gap-3">
          <span className="flex items-center gap-1">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
            Daemon Active
          </span>
          <span className="text-slate-600">|</span>
          <span>Engine: Java Spring Boot 3.3</span>
          <span className="text-slate-600">|</span>
          <span>Storage: SQLite local</span>
        </div>
        <span className="text-slate-500 hidden sm:inline">Interactive Demo Preview</span>
      </div>
    </div>
  );
}
