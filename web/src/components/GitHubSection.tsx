import React from "react";
import { BookOpen, GitPullRequest, Bug, ArrowUpRight, Star, Code2 } from "lucide-react";
import { SITE_CONFIG } from "@/config/site";

function GitHubIcon({ className }: { className?: string }) {
  return (
    <svg
      className={className || "w-4 h-4"}
      viewBox="0 0 24 24"
      fill="currentColor"
      aria-hidden="true"
    >
      <path
        fillRule="evenodd"
        clipRule="evenodd"
        d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.53 1.032 1.53 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z"
      />
    </svg>
  );
}

export function GitHubSection() {
  return (
    <section className="py-20 md:py-28 bg-[#080d16]/60 border-t border-white/5 relative">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="max-w-4xl mx-auto rounded-3xl bg-gradient-to-b from-[#0c121e] to-[#080d15] border border-white/10 p-8 sm:p-12 relative overflow-hidden text-center sm:text-left">
          {/* Subtle glow */}
          <div className="absolute top-0 right-0 w-80 h-80 bg-[#00ffaa]/5 rounded-full blur-3xl pointer-events-none" />

          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center relative z-10">
            <div className="lg:col-span-8 space-y-4">
              <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/5 border border-white/10 text-slate-300 text-xs font-mono">
                <GitHubIcon className="w-3.5 h-3.5 text-white" />
                <span>Open Source Repository</span>
              </div>

              <h2 className="text-2xl sm:text-3xl lg:text-4xl font-extrabold text-white tracking-tight">
                Built for developers. Designed for better debugging.
              </h2>

              <p className="text-sm sm:text-base text-slate-400 leading-relaxed max-w-xl">
                Stack Pilot is developed collaboratively. Inspect the source code, examine scenario schemas, submit scenario templates, or report issues directly on GitHub.
              </p>

              {/* Action Buttons */}
              <div className="pt-2 flex flex-wrap items-center justify-center sm:justify-start gap-3">
                <a
                  href={SITE_CONFIG.github.repoUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="px-5 py-2.5 rounded-xl bg-white text-[#070b12] font-bold text-sm flex items-center gap-2 hover:bg-slate-200 transition-all shadow-md active:scale-95"
                >
                  <GitHubIcon className="w-4 h-4 text-[#070b12]" />
                  <span>View on GitHub</span>
                  <ArrowUpRight className="w-4 h-4 text-slate-600" />
                </a>

                <a
                  href={SITE_CONFIG.github.docsUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="px-5 py-2.5 rounded-xl bg-[#0e1626] border border-white/10 text-slate-200 hover:text-white hover:border-[#00ffaa]/40 font-semibold text-sm flex items-center gap-2 transition-all"
                >
                  <BookOpen className="w-4 h-4 text-[#00ffaa]" />
                  <span>Documentation</span>
                </a>
              </div>
            </div>

            {/* Contribution & Community Quick Links */}
            <div className="lg:col-span-4 space-y-3 font-mono text-xs">
              <a
                href={SITE_CONFIG.github.contributingUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="p-3.5 rounded-xl bg-[#080d16] border border-white/5 hover:border-white/20 transition-all flex items-center justify-between group"
              >
                <div className="flex items-center gap-2.5 text-slate-300 group-hover:text-white">
                  <GitPullRequest className="w-4 h-4 text-sky-400" />
                  <span>Contributing Guidelines</span>
                </div>
                <ArrowUpRight className="w-3.5 h-3.5 text-slate-500 group-hover:text-white" />
              </a>

              <a
                href={SITE_CONFIG.github.issuesUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="p-3.5 rounded-xl bg-[#080d16] border border-white/5 hover:border-white/20 transition-all flex items-center justify-between group"
              >
                <div className="flex items-center gap-2.5 text-slate-300 group-hover:text-white">
                  <Bug className="w-4 h-4 text-amber-400" />
                  <span>Issue Reporting &amp; RFEs</span>
                </div>
                <ArrowUpRight className="w-3.5 h-3.5 text-slate-500 group-hover:text-white" />
              </a>

              <a
                href={SITE_CONFIG.github.repoUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="p-3.5 rounded-xl bg-[#080d16] border border-white/5 hover:border-white/20 transition-all flex items-center justify-between group"
              >
                <div className="flex items-center gap-2.5 text-slate-300 group-hover:text-white">
                  <Star className="w-4 h-4 text-yellow-400" />
                  <span>Star on GitHub</span>
                </div>
                <ArrowUpRight className="w-3.5 h-3.5 text-slate-500 group-hover:text-white" />
              </a>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
