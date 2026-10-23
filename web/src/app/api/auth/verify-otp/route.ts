/**
 * POST /api/auth/verify-otp
 *
 * Step 2 of registration: verify the OTP, create the user account,
 * and return a signed JWT so the desktop can authenticate immediately.
 *
 * Body: { email, otp }
 * Response 200: { token, user: { id, name, email } }
 * Response 400: { error: "..." }
 */
import { NextRequest } from "next/server";
import { consumeOtp, createUser, findUserByEmail } from "@/lib/auth-store";
import { signToken } from "@/lib/jwt";

export async function POST(request: NextRequest) {
  try {
    const { email, otp } = await request.json();

    if (!email?.trim() || !otp?.trim()) {
      return Response.json({ error: "Email and verification code are required." }, { status: 400 });
    }

    const emailLower = email.trim().toLowerCase();
    const entry = consumeOtp(emailLower, otp.trim());

    if (!entry) {
      return Response.json(
        { error: "Invalid or expired verification code. Please request a new one." },
        { status: 400 }
      );
    }

    if (!entry.pendingUser) {
      return Response.json({ error: "Signup session expired. Please start again." }, { status: 400 });
    }

    // Don't re-create if somehow already exists (double submit)
    const existing = findUserByEmail(emailLower);
    const user = existing ?? {
      id: entry.pendingUser.id,
      name: entry.pendingUser.name,
      email: emailLower,
      passwordHash: entry.pendingUser.passwordHash,
      googleId: null,
      verified: true,
      createdAt: new Date().toISOString(),
    };

    if (!existing) {
      createUser(user);
    }

    const token = signToken({ userId: user.id, email: user.email, name: user.name });

    return Response.json({
      token,
      user: { id: user.id, name: user.name, email: user.email },
    });
  } catch (err) {
    console.error("[verify-otp]", err);
    return Response.json({ error: "Verification failed. Please try again." }, { status: 500 });
  }
}
