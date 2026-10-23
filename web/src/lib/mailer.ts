/**
 * OTP email sender.
 *
 * Configure via environment variables in web/.env.local:
 *   SMTP_HOST=smtp.gmail.com
 *   SMTP_PORT=587
 *   SMTP_USER=you@gmail.com
 *   SMTP_PASS=your-app-password
 *   SMTP_FROM=StackPilot <you@gmail.com>
 *
 * For development without an SMTP server: set SMTP_LOG_ONLY=true
 * and the OTP will be printed to the Next.js dev console instead
 * of actually being emailed.
 */
import nodemailer, { SentMessageInfo } from "nodemailer";

function createTransport() {
  if (process.env.SMTP_LOG_ONLY === "true") {
    // Development mode — log OTPs to console, no real email sent.
    return nodemailer.createTransport({ jsonTransport: true });
  }
  return nodemailer.createTransport({
    host: process.env.SMTP_HOST ?? "smtp.gmail.com",
    port: Number(process.env.SMTP_PORT ?? 587),
    secure: false,
    auth: {
      user: process.env.SMTP_USER,
      pass: process.env.SMTP_PASS,
    },
  });
}

export async function sendOtpEmail(to: string, name: string, otp: string): Promise<void> {
  const transport = createTransport();
  const from = process.env.SMTP_FROM ?? "StackPilot <noreply@stackpilot.dev>";

  const html = `
    <!DOCTYPE html>
    <html>
      <body style="font-family:sans-serif;background:#070b12;color:#e2e8f0;padding:40px;margin:0">
        <div style="max-width:480px;margin:0 auto;background:#0d121f;border:1px solid #1e2b45;border-radius:12px;padding:32px">
          <h1 style="color:#2563eb;margin:0 0 8px 0;font-size:24px">StackPilot</h1>
          <p style="color:#94a3b8;margin:0 0 24px 0;font-size:13px">Diagnose · Understand · Fix</p>
          <p style="color:#f8fafc;font-size:16px;margin:0 0 8px 0">Hi ${name},</p>
          <p style="color:#94a3b8;font-size:14px;margin:0 0 24px 0">
            Use the code below to verify your email address. It expires in <strong style="color:#f8fafc">10 minutes</strong>.
          </p>
          <div style="background:#090d16;border:1px solid #1e2b45;border-radius:8px;padding:20px;text-align:center;margin:0 0 24px 0">
            <span style="font-size:36px;font-weight:bold;letter-spacing:8px;color:#3b82f6;font-family:monospace">${otp}</span>
          </div>
          <p style="color:#64748b;font-size:12px;margin:0">
            If you didn't request this code, you can safely ignore this email.
          </p>
        </div>
      </body>
    </html>
  `;

  const info = await transport.sendMail({
    from,
    to,
    subject: `${otp} is your StackPilot verification code`,
    html,
    text: `Your StackPilot verification code is: ${otp}. It expires in 10 minutes.`,
  });

  if (process.env.SMTP_LOG_ONLY === "true") {
    console.log(`\n[StackPilot OTP] To: ${to} | Code: ${otp}\n`);
  } else {
    console.log(`OTP email sent to ${to}: ${(info as SentMessageInfo).messageId}`);
  }
}
