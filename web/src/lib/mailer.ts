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
import nodemailer from "nodemailer";
import type { SentMessageInfo } from "nodemailer";

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

/**
 * Absolute URL of the white StackPilot mark used in the mail header.
 *
 * Mail clients fetch images from their own servers, so this must be an absolute,
 * publicly reachable URL - a localhost or relative path renders as a broken image.
 * Override with EMAIL_LOGO_URL if the asset ever moves.
 */
const EMAIL_LOGO_URL =
  process.env.EMAIL_LOGO_URL ?? "https://stackpilotconfig.vercel.app/logo-white.png";

/** Names come from user input - keep them out of the markup as text. */
function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

/**
 * Renders the verification email.
 *
 * Email HTML is stuck in 2005: everything is table-based with inline styles,
 * because Gmail/Outlook strip <style> blocks and ignore flexbox, max-height and
 * friends. Keep the markup and the styles inline when editing.
 */
export function renderOtpEmail(params: { name: string; otp: string; to: string }): string {
  const safeName = escapeHtml(params.name.trim() || "there");
  const safeTo = escapeHtml(params.to);

  return `<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <meta name="color-scheme" content="dark light">
    <title>Your StackPilot verification code</title>
  </head>
  <body bgcolor="#070b12" style="margin:0;padding:0;background-color:#070b12;">
    <!-- Inbox preview text, hidden inside the body -->
    <div style="display:none;font-size:1px;line-height:1px;max-height:0;max-width:0;opacity:0;overflow:hidden;color:#070b12;">
      Your code is ${params.otp}. It expires in 10 minutes.
    </div>

    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" bgcolor="#070b12" style="background-color:#070b12;">
      <tr>
        <td align="center" style="padding:40px 16px;">

          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"
                 style="width:100%;max-width:520px;background-color:#0d1320;border:1px solid #1e2b45;border-radius:16px;">
            <!-- Brand -->
            <tr>
              <td style="padding:28px 32px 0 32px;">
                <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                  <tr>
                    <td style="padding-right:10px;vertical-align:middle;">
                      <img src="${EMAIL_LOGO_URL}" width="70" height="70" alt="StackPilot"
                           style="display:block;width:28px;height:28px;border:0;outline:none;text-decoration:none;">
                    </td>
                    <td style="vertical-align:middle;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:30px;font-weight:700;letter-spacing:-0.2px;color:#ffffff;">
                      StackPilot
                    </td>
                  </tr>
                </table>
                <div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:12px;color:#64748b;padding-top:6px;">
                  Diagnose &middot; Understand &middot; Fix
                </div>
              </td>
            </tr>

            <tr>
              <td style="padding:22px 32px 0 32px;">
                <div style="height:1px;background-color:#16233a;line-height:1px;font-size:0;">&nbsp;</div>
              </td>
            </tr>

            <!-- Intro -->
            <tr>
              <td style="padding:26px 32px 0 32px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
                <h1 style="margin:0 0 10px 0;font-size:20px;line-height:26px;font-weight:600;letter-spacing:-0.3px;color:#ffffff;">
                  Verify your email
                </h1>
                <p style="margin:0;font-size:14px;line-height:22px;color:#94a3b8;">
                  Hi ${safeName}, use this code to finish signing in to StackPilot.
                </p>
              </td>
            </tr>

            <!-- Code -->
            <tr>
              <td style="padding:22px 32px 0 32px;">
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"
                       style="background-color:#080c14;border:1px solid #1e2b45;border-radius:12px;">
                  <tr>
                    <td align="center" style="padding:20px 16px;font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,'Courier New',monospace;font-size:32px;line-height:38px;font-weight:700;letter-spacing:10px;text-indent:10px;color:#60a5fa;">
                      ${params.otp}
                    </td>
                  </tr>
                </table>
              </td>
            </tr>

            <!-- Expiry -->
            <tr>
              <td align="center" style="padding:14px 32px 0 32px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:12px;color:#64748b;">
                Expires in <strong style="color:#cbd5e1;font-weight:600;">10 minutes</strong>
              </td>
            </tr>

            <!-- Note -->
            <tr>
              <td style="padding:24px 32px 0 32px;">
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"
                       style="background-color:#0a101c;border:1px solid #16233a;border-radius:10px;">
                  <tr>
                    <td style="padding:14px 16px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:12px;line-height:19px;color:#64748b;">
                      Didn't request this code? You can safely ignore this email - nobody can
                      sign in to your account without it.
                    </td>
                  </tr>
                </table>
              </td>
            </tr>

            <tr>
              <td style="padding:26px 32px 0 32px;">
                <div style="height:1px;background-color:#16233a;line-height:1px;font-size:0;">&nbsp;</div>
              </td>
            </tr>

            <!-- Footer -->
            <tr>
              <td style="padding:16px 32px 26px 32px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:11px;line-height:17px;color:#475569;">
                Sent to ${safeTo} because a verification code was requested for this address.
              </td>
            </tr>
          </table>

          <div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:11px;color:#334155;padding-top:18px;">
            &copy; ${new Date().getFullYear()} StackPilot
          </div>
        </td>
      </tr>
    </table>
  </body>
</html>`;
}

export async function sendOtpEmail(to: string, name: string, otp: string): Promise<void> {
  const transport = createTransport();
  const from = process.env.SMTP_FROM ?? "StackPilot <noreply@stackpilot.dev>";

  const html = renderOtpEmail({ name, otp, to });

  const info = await transport.sendMail({
    from,
    to,
    subject: `${otp} is your StackPilot verification code`,
    html,
    text: `Your StackPilot verification code is ${otp}. It expires in 10 minutes. If you didn't request it, you can safely ignore this email.`,
  });

  if (process.env.SMTP_LOG_ONLY === "true") {
    console.log(`\n[StackPilot OTP] To: ${to} | Code: ${otp}\n`);
  } else {
    console.log(`OTP email sent to ${to}: ${(info as SentMessageInfo).messageId}`);
  }
}
