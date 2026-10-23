/**
 * POST /api/auth/login
 *
 * Authenticates an existing user with email + password.
 * Returns a signed JWT on success.
 *
 * Body: { email, password }
 * Response 200: { token, user: { id, name, email } }
 * Response 401: { error: "..." }
 */
import { NextRequest } from "next/server";
import bcrypt from "bcryptjs";
import { findUserByEmail } from "@/lib/auth-store";
import { signToken } from "@/lib/jwt";

export async function POST(request: NextRequest) {
  try {
    const { email, password } = await request.json();

    if (!email?.trim() || !password?.trim()) {
      return Response.json({ error: "Email and password are required." }, { status: 400 });
    }

    const emailLower = email.trim().toLowerCase();
    const user = findUserByEmail(emailLower);

    if (!user || !user.verified) {
      // Generic message — don't reveal whether the email exists
      return Response.json({ error: "Invalid email or password." }, { status: 401 });
    }

    if (!user.passwordHash) {
      return Response.json(
        { error: "This account was created with Google Sign-In. Please use Sign in with Google." },
        { status: 401 }
      );
    }

    const passwordOk = await bcrypt.compare(password, user.passwordHash);
    if (!passwordOk) {
      return Response.json({ error: "Invalid email or password." }, { status: 401 });
    }

    const token = signToken({ userId: user.id, email: user.email, name: user.name });

    return Response.json({
      token,
      user: { id: user.id, name: user.name, email: user.email },
    });
  } catch (err) {
    console.error("[login]", err);
    return Response.json({ error: "Login failed. Please try again." }, { status: 500 });
  }
}
