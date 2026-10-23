"use client";

/**
 * /auth/callback
 *
 * Google redirects here after the user approves the OAuth consent screen.
 * URL will be: http://localhost:3000/auth/callback?code=...&state=...
 *
 * This page:
 * 1. Reads the ?code param from the URL.
 * 2. Calls /api/auth/google-exchange to trade the code for an id_token.
 * 3. Calls /api/auth/google to verify the id_token and get a StackPilot JWT.
 * 4. Redirects to http://localhost:9876/done?token=<JWT> so the desktop app
 *    receives the token via its local HTTP listener.
 */

import { useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";

type Status = "loading" | "success" | "error";

export default function AuthCallbackPage() {
  const params = useSearchParams();
  const [status, setStatus] = useState<Status>("loading");
  const [message, setMessage] = useState("Completing sign-in…");

  useEffect(() => {
    const code = params.get("code");
    const error = params.get("error");

    if (error || !code) {
      setStatus("error");
      setMessage(error === "access_denied"
        ? "Sign-in was cancelled. You can close this window."
        : `Google returned an error: ${error ?? "missing code"}. Please try again.`
      );
      // Tell desktop the flow was cancelled
      fetch("http://localhost:9876/done?error=cancelled").catch(() => {});
      return;
    }

    const redirectUri = `${window.location.origin}/auth/callback`;

    async function exchange() {
      try {
        // Step 1: exchange auth code → id_token
        const exchangeRes = await fetch("/api/auth/google-exchange", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ code, redirectUri }),
        });
        const exchangeData = await exchangeRes.json();
        if (!exchangeRes.ok || !exchangeData.idToken) {
          throw new Error(exchangeData.error ?? "Code exchange failed");
        }

        // Step 2: verify id_token → StackPilot JWT
        const authRes = await fetch("/api/auth/google", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ idToken: exchangeData.idToken }),
        });
        const authData = await authRes.json();
        if (!authRes.ok || !authData.token) {
          throw new Error(authData.error ?? "Authentication failed");
        }

        // Step 3: pass the JWT to the desktop app via local listener
        const jwt = encodeURIComponent(authData.token);
        const name = encodeURIComponent(authData.user?.name ?? "");
        const email = encodeURIComponent(authData.user?.email ?? "");
        const userId = encodeURIComponent(authData.user?.id ?? "");

        setStatus("success");
        setMessage(`Signed in as ${authData.user?.name ?? authData.user?.email}. You can close this window.`);

        // Redirect desktop listener
        window.location.href =
          `http://localhost:9876/done?token=${jwt}&name=${name}&email=${email}&userId=${userId}`;
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : "Unknown error";
        setStatus("error");
        setMessage(`Sign-in failed: ${msg}`);
        fetch("http://localhost:9876/done?error=" + encodeURIComponent(msg)).catch(() => {});
      }
    }

    exchange();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="min-h-screen flex items-center justify-center"
      style={{ background: "#070b12" }}>
      <div className="max-w-md w-full mx-4 rounded-2xl border border-slate-700/50 p-10 text-center"
        style={{ background: "#0d121f" }}>

        {/* Logo / icon */}
        <div className="w-14 h-14 rounded-full flex items-center justify-center mx-auto mb-6"
          style={{ background: "#1e3a5f" }}>
          {status === "loading" && (
            <svg className="w-7 h-7 animate-spin text-blue-400" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor"
                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
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
          {status === "error" && "Sign-in failed"}
        </h1>

        <p className="text-sm text-slate-400 leading-relaxed">{message}</p>

        {status !== "loading" && (
          <p className="text-xs text-slate-600 mt-6">
            You can close this browser tab and return to StackPilot.
          </p>
        )}
      </div>
    </div>
  );
}
