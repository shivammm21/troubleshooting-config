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
    version: "v0.4.2-beta",
    releaseDate: "October 2026",
    channel: "Public Beta",
    isAvailable: true,
    notesUrl: "https://github.com/shivammm21/troubleshooting-config/releases",
    checksumSha256: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    platforms: [
      {
        os: "linux",
        label: "Linux x64",
        arch: "x86_64",
        format: ".tar.gz / AppImage",
        size: "42.8 MB",
        downloadUrl: "https://github.com/shivammm21/troubleshooting-config/releases/download/v0.4.2-beta/stackpilot-linux-x64.tar.gz",
        isReady: true,
      },
      {
        os: "macos",
        label: "macOS Apple Silicon & Intel",
        arch: "Universal (arm64 / x86_64)",
        format: ".dmg / binary",
        size: "48.1 MB",
        downloadUrl: "https://github.com/shivammm21/troubleshooting-config/releases/download/v0.4.2-beta/stackpilot-darwin-universal.dmg",
        isReady: true,
      },
      {
        os: "windows",
        label: "Windows 11 / 10",
        arch: "x86_64",
        format: ".msi / .zip",
        size: "44.6 MB",
        downloadUrl: "https://github.com/shivammm21/troubleshooting-config/releases/download/v0.4.2-beta/stackpilot-win-x64.zip",
        isReady: true,
      },
    ],
  } as ReleaseConfig,
  quickInstall: {
    curl: "curl -fsSL https://raw.githubusercontent.com/shivammm21/troubleshooting-config/main/install.sh | bash",
    brew: "brew tap stackpilot/tap && brew install stackpilot",
    powershell: "irm https://raw.githubusercontent.com/shivammm21/troubleshooting-config/main/install.ps1 | iex",
  },
};
