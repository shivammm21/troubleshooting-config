"use client";

/**
 * /auth/callback
 *
 * Google redirects here after OAuth consent. This page:
 * 1. Reads ?code from the URL via useSearchParams (wrapped in Suspense).
 * 2. Calls /api/auth/google-exchange → id_token.
 * 3. Calls /api/auth/google → StackPilot JWT.
 * 4. Redirects to http://localhost:9876/done?token=<JWT>&state=<state>
 *    for the desktop listener, which checks the state it generated.
 *
 * useSearchParams() requires a Suspense boundary in Next.js 15/16 when the
 * page participates in static prerendering. We wrap the inner component in
 * <Suspense> to satisfy that requirement.
 */

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";

type Status = "loading" | "success" | "error";

// ── Inner component (uses useSearchParams — must be inside Suspense) ──────────

function CallbackInner() {
  const params = useSearchParams();
  const [status, setStatus] = useState<Status>("loading");
  const [message, setMessage] = useState("Completing sign-in…");
  // True when the flow was started by the desktop app and this tab will hand
  // the session over to it instead of ending here.
  const [handoff, setHandoff] = useState(false);

  useEffect(() => {
    const code  = params.get("code");
    const error = params.get("error");
    // Echoed back by Google from /api/auth/google/start - the desktop compares
    // it with the value it generated before opening the browser.
    const state = params.get("state") ?? "";
    setHandoff(Boolean(state));

    if (error || !code) {
      setStatus("error");
      setMessage(
        error === "access_denied"
          ? "Sign-in was cancelled. You can close this window."
          : `Google returned an error: ${error ?? "missing code"}. Please try again.`
      );
      fetch(`http://localhost:9876/done?error=cancelled&state=${encodeURIComponent(state)}`).catch(() => {});
      return;
    }

    // Google requires the *same* redirect_uri at code exchange, so prefer the
    // canonical site URL (must match Google Console) over whatever host this tab
    // happened to be opened with (localhost vs 127.0.0.1, proxy domains, ...).
    const siteUrl = (process.env.NEXT_PUBLIC_SITE_URL ?? window.location.origin).replace(/\/$/, "");
    const redirectUri = `${siteUrl}/auth/callback`;

    async function exchange() {
      try {
        // Step 1: auth code → id_token
        const exchangeRes = await fetch("/api/auth/google-exchange", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ code, redirectUri }),
        });
        const exchangeData = await exchangeRes.json();
        if (!exchangeRes.ok || !exchangeData.idToken) {
          throw new Error(exchangeData.error ?? "Code exchange failed");
        }

        // Step 2: id_token → StackPilot JWT
        const authRes = await fetch("/api/auth/google", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ idToken: exchangeData.idToken }),
        });
        const authData = await authRes.json();
        if (!authRes.ok || !authData.token) {
          throw new Error(authData.error ?? "Authentication failed");
        }

        // Step 3: send JWT to desktop local listener
        const jwt    = encodeURIComponent(authData.token);
        const name   = encodeURIComponent(authData.user?.name  ?? "");
        const email  = encodeURIComponent(authData.user?.email ?? "");
        const userId = encodeURIComponent(authData.user?.id    ?? "");

        setStatus("success");
        setMessage(
          `Signed in as ${authData.user?.name ?? authData.user?.email}.`
        );

        // `state` is only present when the desktop app opened /signin or
        // /signup. In that case hand the finished session to the app so it can
        // continue on its own; a site-only visit just stays on this card.
        if (state) {
          setMessage(
            `Signed in as ${authData.user?.name ?? authData.user?.email}. Returning to the StackPilot app…`
          );
          window.location.href =
            `http://localhost:9876/done?token=${jwt}&name=${name}&email=${email}&userId=${userId}` +
            `&state=${encodeURIComponent(state)}`;
        }
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : "Unknown error";
        setStatus("error");
        setMessage(`Sign-in failed: ${msg}`);
        fetch(
          "http://localhost:9876/done?error=" +
            encodeURIComponent(msg) +
            `&state=${encodeURIComponent(state)}`
        ).catch(() => {});
      }
    }

    exchange();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div
      className="min-h-screen flex items-center justify-center"
      style={{ background: "#070b12" }}
    >
      <div
        className="max-w-md w-full mx-4 rounded-2xl border border-slate-700/50 p-10 text-center"
        style={{ background: "#0d121f" }}
      >
        {/* Status icon */}
        <div
          className="w-14 h-14 rounded-full flex items-center justify-center mx-auto mb-6"
          style={{ background: "#1e3a5f" }}
        >
          {status === "loading" && (
            <svg className="w-7 h-7 animate-spin text-blue-400" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
          )}
          {status === "success" && (
            <svg className="w-7 h-7 text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
            </svg>
          )}
          {status === "error" && (
            <svg className="w-7 h-7 text-red-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          )}
        </div>

        <h1 className="text-xl font-bold text-slate-100 mb-2">
          {status === "loading" && "Completing sign-in…"}
          {status === "success" && "Signed in successfully!"}
          {status === "error"   && "Sign-in failed"}
        </h1>

        <p className="text-sm text-slate-400 leading-relaxed">{message}</p>

        {status === "success" && !handoff && (
          <a
            href="/download"
            className="inline-flex items-center justify-center mt-6 h-10 px-5 rounded-lg bg-[#2563eb] hover:bg-[#3b82f6] text-sm font-medium text-white transition-colors"
          >
            Get the StackPilot app
          </a>
        )}

        {status !== "loading" && (
          <p className="text-xs text-slate-600 mt-6">
            You can close this browser tab and return to StackPilot.
          </p>
        )}
      </div>
    </div>
  );
}

// ── Fallback shown while Suspense resolves ────────────────────────────────────

function CallbackFallback() {
  return (
    <div
      className="min-h-screen flex items-center justify-center"
      style={{ background: "#070b12" }}
    >
      <div
        className="w-14 h-14 rounded-full flex items-center justify-center"
        style={{ background: "#1e3a5f" }}
      >
        <svg className="w-7 h-7 animate-spin text-blue-400" fill="none" viewBox="0 0 24 24">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
      </div>
    </div>
  );
}

// ── Page export (Suspense wrapper required for useSearchParams) ───────────────

export default function AuthCallbackPage() {
  return (
    <Suspense fallback={<CallbackFallback />}>
      <CallbackInner />
    </Suspense>
  );
}
