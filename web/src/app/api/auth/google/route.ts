/**
 * POST /api/auth/google
 *
 * Validates a Google ID token issued by Google Sign-In,
 * creates or fetches the user, and returns a StackPilot JWT.
 *
 * Body: { idToken }
 * Response 200: { token, user: { id, name, email } }
 * Response 401: { error: "..." }
 */
import { NextRequest } from "next/server";
import { v4 as uuidv4 } from "uuid";
import { findUserByEmail, findUserByGoogleId, createUser } from "@/lib/auth-store";
import { signToken } from "@/lib/jwt";

interface GoogleTokenInfo {
  sub: string;
  email: string;
  email_verified: string;
  name: string;
  given_name?: string;
  family_name?: string;
  picture?: string;
  aud: string;
}

async function verifyGoogleToken(idToken: string): Promise<GoogleTokenInfo> {
  const res = await fetch(
    `https://oauth2.googleapis.com/tokeninfo?id_token=${encodeURIComponent(idToken)}`
  );
  if (!res.ok) {
    throw new Error(`Google tokeninfo rejected: ${res.status}`);
  }
  const info = (await res.json()) as GoogleTokenInfo;

  // Validate audience matches our Google Client ID
  const clientId = process.env.GOOGLE_CLIENT_ID;
  if (clientId && info.aud !== clientId) {
    throw new Error(`Token audience mismatch: expected ${clientId}, got ${info.aud}`);
  }
  if (info.email_verified !== "true") {
    throw new Error("Google email is not verified");
  }
  return info;
}

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const idToken: string | undefined = body?.idToken;

    if (!idToken?.trim()) {
      return Response.json({ error: "Google token is required." }, { status: 400 });
    }

    let googleInfo: GoogleTokenInfo;
    try {
      googleInfo = await verifyGoogleToken(idToken.trim());
    } catch (e) {
      console.error("[google-auth] token verification failed:", e);
      return Response.json(
        { error: "Google sign-in failed. Please try again." },
        { status: 401 }
      );
    }

    const emailLower = googleInfo.email.toLowerCase();

    // Find existing user by Google sub or email
    let user = findUserByGoogleId(googleInfo.sub) ?? findUserByEmail(emailLower);

    if (!user) {
      user = {
        id: uuidv4(),
        name: googleInfo.name,
        email: emailLower,
        passwordHash: null,
        googleId: googleInfo.sub,
        verified: true,
        createdAt: new Date().toISOString(),
      };
      createUser(user);
      console.log(`[google-auth] New user created: ${emailLower}`);
    } else {
      console.log(`[google-auth] Existing user signed in: ${emailLower}`);
    }

    const token = signToken({ userId: user.id, email: user.email, name: user.name });

    return Response.json({
      token,
      user: { id: user.id, name: user.name, email: user.email },
    });
  } catch (err) {
    console.error("[google-auth] unexpected error:", err);
    return Response.json(
      { error: "Google sign-in failed. Please try again." },
      { status: 500 }
    );
  }
}
