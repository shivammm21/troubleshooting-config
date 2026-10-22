"use client";

import React from "react";
import Image from "next/image";

interface FeatureShowcaseItem {
  title: string;
  description: string;
  imageSrc: string;
  imageAlt: string;
  glowColor: string; // for ambient back-glow
}

export function ProductScreenshots() {
  const showcases: FeatureShowcaseItem[] = [
    {
      title: "Containers Overview",
      description:
        "Gain real-time visibility across all running microservices and dependencies. Monitor container uptime, health status, port bindings, and restart loops directly from your local Docker environment.",
      imageSrc: "/tool-ss/Containers.png",
      imageAlt: "StackPilot Containers Management Dashboard",
      glowColor: "from-blue-500/20 via-cyan-500/10 to-transparent",
    },
    {
      title: "Real-Time Live Logs",
      description:
        "Stream container logs with live auto-scroll, log pause, and level highlighting ([INFO], [WARN], [ERROR]). Instantly identify connection refused faults and trace execution without leaving the dashboard.",
      imageSrc: "/tool-ss/Logs.png",
      imageAlt: "StackPilot Real-Time Live Logs Stream",
      glowColor: "from-[#00ffaa]/20 via-teal-500/10 to-transparent",
    },
    {
      title: "Root-Cause Diagnosis",
      description:
        "Automated heuristics analyze container failures, identify root causes (such as unresolvable container hosts or exit code 1), and provide actionable, step-by-step remediation fixes.",
      imageSrc: "/tool-ss/Digonostic result.png",
      imageAlt: "StackPilot Root-Cause Diagnosis and Remediation Plan",
      glowColor: "from-amber-500/20 via-orange-500/10 to-transparent",
    },
  ];

  return (
    <section id="tool-tour" className="py-24 md:py-32 bg-[#05080f] relative overflow-hidden">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* 2x2 Clean Grid as shown in reference design */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-x-12 gap-y-16 lg:gap-x-16 lg:gap-y-20">
          {showcases.map((item, index) => (
            <div key={index} className="flex flex-col group">
              {/* Image Container with floating ambient glow */}
              <div className="relative mb-8 rounded-2xl flex items-center justify-center">
                {/* Subtle colorful ambient back-glow behind the card */}
                <div
                  className={`absolute -inset-4 bg-gradient-to-r ${item.glowColor} rounded-3xl blur-2xl opacity-40 group-hover:opacity-75 transition-opacity duration-500 pointer-events-none`}
                />

                {/* Screenshot Card */}
                <div className="relative w-full aspect-[16/10] rounded-xl overflow-hidden shadow-2xl shadow-black/80 border border-white/5 bg-[#0a0f19] flex items-center justify-center group-hover:scale-[1.015] transition-transform duration-300">
                  <Image
                    src={item.imageSrc}
                    alt={item.imageAlt}
                    fill
                    sizes="(max-width: 768px) 100vw, 600px"
                    className="object-contain p-1 sm:p-2"
                    priority={index < 2}
                  />
                </div>
              </div>

              {/* Text content below image: bold title + muted paragraph */}
              <div className="space-y-2 max-w-xl">
                <h3 className="text-xl sm:text-2xl font-bold text-white tracking-tight group-hover:text-[#00ffaa] transition-colors">
                  {item.title}
                </h3>
                <p className="text-sm sm:text-base text-slate-400 leading-relaxed font-normal">
                  {item.description}
                </p>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
