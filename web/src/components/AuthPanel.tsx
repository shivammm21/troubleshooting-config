"use client";

/**
 * The site's sign-in / sign-up card.
 *
 * The desktop app opens `/signin?desktop=1&state=<uuid>` (or `/signup`) in the
 * system browser, so the user always lands on StackPilot first and taps
 * "Continue with Google" here. That button goes to `/api/auth/google/start`,
 * which sends the browser to Google; Google returns to `/auth/callback`, which
 * signs the user in on the site and hands the session back to the app that
 * started the flow (the `state` value).
 */

import { Suspense } from "react";
import Image from "next/image";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, ShieldCheck } from "lucide-react";

export type AuthMode = "signin" | "signup";

/** The four-colour Google "G", drawn inline so no extra request is needed. */
function GoogleGlyph() {
  return (
    <svg viewBox="0 0 48 48" className="w-[18px] h-[18px]" aria-hidden="true">
      <path
        fill="#4285F4"
        d="M45.12 24.5c0-1.56-.14-3.06-.4-4.5H24v8.51h11.84c-.51 2.75-2.06 5.08-4.39 6.64v5.52h7.11c4.16-3.83 6.56-9.47 6.56-16.17z"
      />
      <path
        fill="#34A853"
        d="M24 46c5.94 0 10.92-1.97 14.56-5.33l-7.11-5.52c-1.97 1.32-4.49 2.1-7.45 2.1-5.73 0-10.58-3.87-12.31-9.07H4.34v5.7C7.96 41.07 15.4 46 24 46z"
      />
      <path
        fill="#FBBC05"
        d="M11.69 28.18C11.25 26.86 11 25.45 11 24s.25-2.86.69-4.18v-5.7H4.34C2.85 17.09 2 20.45 2 24s.85 6.91 2.34 9.88l7.35-5.7z"
      />
      <path
        fill="#EA4335"
        d="M24 10.75c3.23 0 6.13 1.11 8.41 3.29l6.31-6.31C34.91 4.18 29.93 2 24 2 15.4 2 7.96 6.93 4.34 14.12l7.35 5.7c1.73-5.2 6.58-9.07 12.31-9.07z"
      />
    </svg>
  );
}

function Card({ mode }: { mode: AuthMode }) {
  const params = useSearchParams();
  const state = params.get("state") ?? "";
  const desktop = params.get("desktop") === "1";

  // `state` keeps the round-trip tied to the app window that opened this page.
  const googleParams = new URLSearchParams();
  googleParams.set("intent", mode);
  if (state) googleParams.set("state", state);

  const otherParams = new URLSearchParams();
  if (state) otherParams.set("state", state);
  if (desktop) otherParams.set("desktop", "1");

  const isSignIn = mode === "signin";

  return (
    <div className="relative min-h-screen flex items-center justify-center px-4 py-24 overflow-hidden">
      {/* Soft brand glow behind the card */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            "radial-gradient(600px 320px at 50% 12%, rgba(37,99,235,0.16), transparent 70%), radial-gradient(460px 260px at 50% 92%, rgba(0,255,170,0.07), transparent 70%)",
        }}
      />

      <div className="relative w-full max-w-[440px]">
        <div className="rounded-2xl border border-white/8 bg-[#0d131f]/90 backdrop-blur-xl p-8 sm:p-10 shadow-[0_24px_80px_-24px_rgba(0,0,0,0.9)]">
          {/* Brand */}
          <div className="flex items-center gap-3 mb-8">
            <Image
              src="/logo-white.png"
              alt="Stack Pilot"
              width={40}
              height={40}
              className="w-9 h-9 object-contain"
              priority
            />
            <span className="text-lg font-bold tracking-tight text-white">Stack Pilot</span>
          </div>

          <h1 className="text-[26px] leading-tight font-semibold text-white tracking-tight">
            {isSignIn ? "Sign in to Stack Pilot" : "Create your account"}
          </h1>
          <p className="mt-2 text-sm text-slate-400 leading-relaxed">
            {isSignIn
              ? "Use your Google account to open the desktop app — no password to remember."
              : "Continue with Google and we'll set up your Stack Pilot account in one step."}
          </p>

          {/* The one action this page exists for */}
          <a
            href={`/api/auth/google/start?${googleParams.toString()}`}
            className="group mt-8 flex items-center justify-center gap-3 h-12 rounded-xl border border-white/10 bg-[#131b2e] text-sm font-medium text-white transition-all hover:bg-[#17223a] hover:border-[#2563eb]/60 focus:outline-none focus-visible:ring-2 focus-visible:ring-[#3b82f6]"
          >
            <GoogleGlyph />
            {isSignIn ? "Continue with Google" : "Sign up with Google"}
          </a>

          <div className="mt-4 flex items-start gap-2 text-[11px] leading-relaxed text-slate-500">
            <ShieldCheck className="w-3.5 h-3.5 mt-[1px] shrink-0 text-slate-500" />
            <span>
              {desktop
                ? "You'll finish on Google, then this browser returns to the Stack Pilot app automatically."
                : "You'll finish on Google and come straight back here, signed in."}
            </span>
          </div>

          {/* Switch between sign in and sign up, keeping the app handoff intact */}
          <div className="mt-8 pt-6 border-t border-white/5 text-center text-sm text-slate-400">
            {isSignIn ? "New to Stack Pilot? " : "Already have an account? "}
            <Link
              href={`/${isSignIn ? "signup" : "signin"}${
                otherParams.toString() ? `?${otherParams.toString()}` : ""
              }`}
              className="font-medium text-[#00ffaa] hover:underline"
            >
              {isSignIn ? "Create an account" : "Sign in"}
            </Link>
          </div>
        </div>

        <div className="mt-6 text-center">
          <Link
            href="/"
            className="inline-flex items-center gap-1.5 text-xs text-slate-500 hover:text-slate-300 transition-colors"
          >
            <ArrowLeft className="w-3 h-3" />
            Back to stackpilot
          </Link>
        </div>
      </div>
    </div>
  );
}

/** Shown while the search params resolve — same footprint, so nothing jumps. */
function CardFallback() {
  return (
    <div className="min-h-screen flex items-center justify-center px-4">
      <div className="w-full max-w-[440px] h-[360px] rounded-2xl border border-white/8 bg-[#0d131f]/70 animate-pulse" />
    </div>
  );
}

export function AuthPanel({ mode }: { mode: AuthMode }) {
  return (
    <Suspense fallback={<CardFallback />}>
      <Card mode={mode} />
    </Suspense>
  );
}
