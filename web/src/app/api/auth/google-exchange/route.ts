/**
 * POST /api/auth/google-exchange
 *
 * Called by the StackPilot desktop app after receiving an OAuth
 * authorization code from Google's callback redirect.
 *
 * Exchanges the auth code + redirect URI for Google tokens using the
 * client secret (which must stay server-side, never in the desktop app).
 *
 * Body:    { code: string, redirectUri: string }
 * Success: { idToken: string }
 * Error:   { error: string }
 */
import { NextRequest } from "next/server";

interface GoogleTokenResponse {
  id_token?: string;
  access_token?: string;
  error?: string;
  error_description?: string;
}

export async function POST(request: NextRequest) {
  try {
    const { code, redirectUri } = await request.json();

    if (!code?.trim() || !redirectUri?.trim()) {
      return Response.json({ error: "code and redirectUri are required." }, { status: 400 });
    }

    const clientId = process.env.GOOGLE_CLIENT_ID;
    const clientSecret = process.env.GOOGLE_CLIENT_SECRET;

    if (!clientId || !clientSecret) {
      console.error("[google-exchange] GOOGLE_CLIENT_ID or GOOGLE_CLIENT_SECRET not configured");
      return Response.json({ error: "Google OAuth not configured on server." }, { status: 500 });
    }

    // Exchange auth code for tokens
    const params = new URLSearchParams({
      code,
      client_id: clientId,
      client_secret: clientSecret,
      redirect_uri: redirectUri,
      grant_type: "authorization_code",
    });

    const res = await fetch("https://oauth2.googleapis.com/token", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: params.toString(),
    });

    const data = (await res.json()) as GoogleTokenResponse;

    if (!res.ok || data.error) {
      console.error("[google-exchange] Token exchange failed:", data.error, data.error_description);
      return Response.json(
        { error: data.error_description ?? "Google token exchange failed." },
        { status: 401 }
      );
    }

    if (!data.id_token) {
      return Response.json({ error: "Google did not return an ID token." }, { status: 401 });
    }

    return Response.json({ idToken: data.id_token });
  } catch (err) {
    console.error("[google-exchange] unexpected error:", err);
    return Response.json({ error: "Token exchange failed. Please try again." }, { status: 500 });
  }
}
