"use client";

import React, { useState } from "react";
import { ChevronDown, HelpCircle } from "lucide-react";

interface FAQItem {
  question: string;
  answer: string;
}

const faqs: FAQItem[] = [
  {
    question: "What is Stack Pilot?",
    answer:
      "Stack Pilot is a developer-focused infrastructure troubleshooting tool. It helps engineering teams reproduce complex infrastructure failures in isolated environments, inspect root causes with correlated diagnostics, and automatically verify proposed fixes.",
  },
  {
    question: "Which infrastructure problems can it troubleshoot?",
    answer:
      "Currently, Stack Pilot supports troubleshooting scenarios for Docker container networking, internal DNS resolution drops, Apache Kafka advertised listener misconfigurations, and Redis cache memory and eviction limits. Additional scenarios are easily configurable via declarative YAML files.",
  },
  {
    question: "Does Stack Pilot run locally?",
    answer:
      "Yes! Stack Pilot is designed specifically for local developer workstations and staging machines. Its Spring Boot diagnostic daemon connects directly to your local Docker socket and spins up ephemeral containers without requiring external cloud accounts or sending sensitive internal logs over the internet.",
  },
  {
    question: "Which operating systems are supported?",
    answer:
      "Stack Pilot runs natively on modern 64-bit Linux distributions (Ubuntu, Fedora, Debian, Arch), macOS (both Apple Silicon arm64 and Intel x86_64), and Windows 10/11 with Docker Desktop or WSL 2.",
  },
  {
    question: "Is Stack Pilot free and open source?",
    answer:
      "Yes, the core Stack Pilot tool, scenario runner, and diagnostic schemas are developed open source. You can view the code, report issues, and contribute scenario definitions on our GitHub repository.",
  },
  {
    question: "How do I install Stack Pilot?",
    answer:
      "You can install Stack Pilot quickly using our automated install script (curl -fsSL https://raw.githubusercontent.com/shivammm21/troubleshooting-config/main/install.sh | bash), via Homebrew tap on macOS/Linux, or by downloading the standalone precompiled binary package for your operating system from the download section.",
  },
  {
    question: "Can I create custom failure scenarios?",
    answer:
      "Absolutely. Scenarios are authored in simple YAML files specifying the target services (via Docker Compose), failure injection conditions (such as Toxiproxy network partitions or environment variable faults), and assertion checks used to verify recovery.",
  },
  {
    question: "Where can I report bugs or request features?",
    answer:
      "Bugs, feature requests, and scenario proposals are tracked publicly on our official GitHub Issues page. We welcome issue submissions, feedback on diagnostics, and pull requests for new scenario templates.",
  },
];

function AccordionItem({
  faq,
  index,
  openIndex,
  toggle,
}: {
  faq: FAQItem;
  index: number;
  openIndex: number | null;
  toggle: (i: number) => void;
}) {
  const isOpen = openIndex === index;
  return (
    <div className="rounded-2xl bg-[#0c121e] border border-white/5 overflow-hidden transition-all duration-200">
      <button
        onClick={() => toggle(index)}
        className="w-full p-5 sm:p-6 text-left flex items-center justify-between gap-4 focus:outline-none focus-visible:ring-2 focus-visible:ring-[#00ffaa]"
        aria-expanded={isOpen}
      >
        <span className="text-base sm:text-lg font-semibold text-white tracking-tight">
          {faq.question}
        </span>
        <div
          className={`shrink-0 w-8 h-8 rounded-full bg-white/5 flex items-center justify-center text-slate-400 transition-transform duration-200 ${
            isOpen ? "rotate-180 text-[#00ffaa] bg-[#00ffaa]/10" : ""
          }`}
        >
          <ChevronDown className="w-4 h-4" />
        </div>
      </button>

      {isOpen && (
        <div className="px-5 sm:px-6 pb-6 text-sm text-slate-300 leading-relaxed font-sans border-t border-white/5 pt-4">
          {faq.answer}
        </div>
      )}
    </div>
  );
}

export function FAQ() {
  const [openIndexLeft, setOpenIndexLeft] = useState<number | null>(0);
  const [openIndexRight, setOpenIndexRight] = useState<number | null>(null);

  const mid = Math.ceil(faqs.length / 2);
  const leftFaqs = faqs.slice(0, mid);
  const rightFaqs = faqs.slice(mid);

  return (
    <section id="faq" className="py-20 md:py-28 relative">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="text-center space-y-4 mb-16">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#00ffaa]/10 border border-[#00ffaa]/20 text-[#00ffaa] text-xs font-mono uppercase tracking-wider">
            <HelpCircle className="w-3.5 h-3.5" />
            <span>Frequently Asked Questions</span>
          </div>
          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-extrabold text-white tracking-tight">
            Common questions answered.
          </h2>
          <p className="text-base text-slate-400">
            Have questions about how Stack Pilot works, setup requirements, or architecture?
          </p>
        </div>

        {/* Two-column accordion grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 lg:gap-6 items-start">
          {/* Left column */}
          <div className="space-y-3">
            {leftFaqs.map((faq, index) => (
              <AccordionItem
                key={index}
                faq={faq}
                index={index}
                openIndex={openIndexLeft}
                toggle={(i) =>
                  setOpenIndexLeft(openIndexLeft === i ? null : i)
                }
              />
            ))}
          </div>

          {/* Right column */}
          <div className="space-y-3">
            {rightFaqs.map((faq, index) => (
              <AccordionItem
                key={index}
                faq={faq}
                index={index}
                openIndex={openIndexRight}
                toggle={(i) =>
                  setOpenIndexRight(openIndexRight === i ? null : i)
                }
              />
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
