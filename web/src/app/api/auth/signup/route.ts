/**
 * POST /api/auth/signup
 *
 * Step 1 of registration: validate name/email/password, hash password,
 * store pending user, generate + email a 6-digit OTP.
 *
 * Body: { name, email, password }
 * Response 200: { message: "OTP sent" }
 * Response 400: { error: "..." }
 */
import { NextRequest } from "next/server";
import bcrypt from "bcryptjs";
import { v4 as uuidv4 } from "uuid";
import { findUserByEmail, saveOtp } from "@/lib/auth-store";
import { sendOtpEmail } from "@/lib/mailer";

function generateOtp(): string {
  return String(Math.floor(100000 + Math.random() * 900000));
}

export async function POST(request: NextRequest) {
  try {
    const { name, email, password } = await request.json();

    if (!name?.trim() || !email?.trim() || !password?.trim()) {
      return Response.json({ error: "Name, email, and password are required." }, { status: 400 });
    }

    const emailLower = email.trim().toLowerCase();
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(emailLower)) {
      return Response.json({ error: "Invalid email address." }, { status: 400 });
    }

    if (password.length < 8) {
      return Response.json({ error: "Password must be at least 8 characters." }, { status: 400 });
    }

    const existing = findUserByEmail(emailLower);
    if (existing?.verified) {
      return Response.json({ error: "An account with this email already exists." }, { status: 400 });
    }

    const passwordHash = await bcrypt.hash(password, 12);
    const otp = generateOtp();
    const expiresAt = Date.now() + 10 * 60 * 1000; // 10 minutes

    saveOtp({
      email: emailLower,
      otp,
      expiresAt,
      pendingUser: {
        id: uuidv4(),
        name: name.trim(),
        passwordHash,
      },
    });

    await sendOtpEmail(emailLower, name.trim(), otp);

    return Response.json({ message: "Verification code sent to your email." });
  } catch (err) {
    console.error("[signup]", err);
    return Response.json({ error: "Failed to send verification email. Please try again." }, { status: 500 });
  }
}
