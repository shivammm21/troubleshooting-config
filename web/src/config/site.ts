export interface ReleaseConfig {
  version: string;
  releaseDate: string;
  channel: string;
  isAvailable: boolean;
  notesUrl: string;
  checksumSha256?: string;
  platforms: {
    os: 'linux' | 'macos' | 'windows';
    label: string;
    arch: string;
    format: string;
    size: string;
    downloadUrl: string;
    isReady: boolean;
  }[];
}

// Stable alias published by .github/workflows/release.yml. GitHub redirects it to
// the StackPilot-Setup.exe attached to the newest published release, so the
// website never has to change when a new version ships.
export const WINDOWS_SETUP_URL =
  "https://github.com/shivammm21/troubleshooting-config/releases/latest/download/StackPilot-Setup.exe";

// Checksum file published next to the installer.
export const WINDOWS_CHECKSUM_URL = `${WINDOWS_SETUP_URL}.sha256`;

// Linux Debian package published by the same release workflow.
export const LINUX_DEB_URL =
  "https://github.com/shivammm21/troubleshooting-config/releases/latest/download/StackPilot.deb";
export const LINUX_DEB_CHECKSUM_URL = `${LINUX_DEB_URL}.sha256`;

// Terminal equivalents of the Windows download button.
export const WINDOWS_CURL_COMMAND = `curl.exe -L -o StackPilot-Setup.exe "${WINDOWS_SETUP_URL}"`;
export const WINDOWS_POWERSHELL_COMMAND = `Invoke-WebRequest -Uri "${WINDOWS_SETUP_URL}" -OutFile StackPilot-Setup.exe`;

export const SITE_CONFIG = {
  name: "Stack Pilot",
  tagline: "Find the Fault. Fix the Stack.",
  description:
    "Reproduce infrastructure failures, investigate root causes, and validate fixes in a controlled environment. Built for developers and DevOps engineers.",
  github: {
    repoUrl: "https://github.com/shivammm21/troubleshooting-config",
    docsUrl: "https://github.com/shivammm21/troubleshooting-config#readme",
    issuesUrl: "https://github.com/shivammm21/troubleshooting-config/issues",
    contributingUrl: "https://github.com/shivammm21/troubleshooting-config/blob/main/CONTRIBUTING.md",
  },
  release: {
    version: "v1.0.0",
    releaseDate: "October 2026",
    channel: "Public Beta",
    isAvailable: true,
    notesUrl: "https://github.com/shivammm21/troubleshooting-config/releases",
    platforms: [
      {
        os: "linux",
        label: "Linux x64",
        arch: "x86_64",
        format: ".deb package",
        size: "≈150 MB",
        downloadUrl: LINUX_DEB_URL,
        isReady: true,
      },
      {
        os: "macos",
        label: "macOS Apple Silicon & Intel",
        arch: "Universal (arm64 / x86_64)",
        format: ".dmg / binary",
        size: "48.1 MB",
        downloadUrl: "https://github.com/shivammm21/troubleshooting-config/releases",
        isReady: false,
      },
      {
        os: "windows",
        label: "Windows 11 / 10",
        arch: "x86_64",
        format: ".exe installer",
        size: "173.5 MB",
        downloadUrl: WINDOWS_SETUP_URL,
        isReady: true,
      },
    ],
  } as ReleaseConfig,
  // Terminal one-liners that fetch the same installer as the Windows button.
  quickInstall: {
    curl: WINDOWS_CURL_COMMAND,
    powershell: WINDOWS_POWERSHELL_COMMAND,
  },
};
