/**
 * JWT helpers for StackPilot desktop ↔ web auth.
 *
 * The desktop app exchanges credentials for a signed JWT.
 * The JWT is sent as X-StackPilot-Auth on subsequent requests
 * and stored securely by the desktop.
 */
import jwt from "jsonwebtoken";

const SECRET = process.env.JWT_SECRET ?? "stackpilot-dev-secret-change-in-production";
const EXPIRES_IN = "30d"; // token valid for 30 days

export interface TokenPayload {
  userId: string;
  email: string;
  name: string;
}

export function signToken(payload: TokenPayload): string {
  return jwt.sign(payload, SECRET, { expiresIn: EXPIRES_IN });
}

export function verifyToken(token: string): TokenPayload | null {
  try {
    return jwt.verify(token, SECRET) as TokenPayload;
  } catch {
    return null;
  }
}
