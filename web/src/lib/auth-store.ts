/**
 * Simple file-based user store.
 * Stores users in web/data/users.json — no database needed.
 * Works entirely offline.
 */
import fs from "fs";
import path from "path";

const DATA_FILE = path.join(process.cwd(), "data", "users.json");

export interface StoredUser {
  id: string;
  name: string;
  email: string;
  passwordHash: string | null; // null for Google-only accounts
  googleId: string | null;
  verified: boolean;
  createdAt: string;
}

export interface OtpEntry {
  email: string;
  otp: string;
  expiresAt: number; // unix ms
  pendingUser?: {
    id: string;
    name: string;
    passwordHash: string;
  };
}

interface DataFile {
  users: StoredUser[];
  otps: OtpEntry[];
}

function read(): DataFile {
  try {
    const dir = path.dirname(DATA_FILE);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    if (!fs.existsSync(DATA_FILE)) return { users: [], otps: [] };
    return JSON.parse(fs.readFileSync(DATA_FILE, "utf-8")) as DataFile;
  } catch {
    return { users: [], otps: [] };
  }
}

function write(data: DataFile) {
  const dir = path.dirname(DATA_FILE);
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(DATA_FILE, JSON.stringify(data, null, 2), "utf-8");
}

export function findUserByEmail(email: string): StoredUser | undefined {
  return read().users.find((u) => u.email.toLowerCase() === email.toLowerCase());
}

export function findUserByGoogleId(googleId: string): StoredUser | undefined {
  return read().users.find((u) => u.googleId === googleId);
}

export function createUser(user: StoredUser): void {
  const data = read();
  data.users.push(user);
  write(data);
}

export function markVerified(email: string): void {
  const data = read();
  const user = data.users.find((u) => u.email.toLowerCase() === email.toLowerCase());
  if (user) user.verified = true;
  write(data);
}

export function saveOtp(entry: OtpEntry): void {
  const data = read();
  // Remove any existing OTP for this email
  data.otps = data.otps.filter((o) => o.email.toLowerCase() !== entry.email.toLowerCase());
  data.otps.push(entry);
  write(data);
}

export function consumeOtp(email: string, otp: string): OtpEntry | null {
  const data = read();
  const idx = data.otps.findIndex(
    (o) =>
      o.email.toLowerCase() === email.toLowerCase() &&
      o.otp === otp &&
      o.expiresAt > Date.now()
  );
  if (idx === -1) return null;
  const entry = data.otps[idx];
  data.otps.splice(idx, 1);
  write(data);
  return entry;
}
