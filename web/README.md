# Stack Pilot — Official Product Website

**Find the Fault. Fix the Stack.**

A developer-focused infrastructure troubleshooting website for **Stack Pilot**, built with Next.js (App Router), TypeScript, Tailwind CSS, Framer Motion, and Lucide React.

---

## 🚀 Features Included

- **Sticky Navigation Bar**: Branded logomark, quick navigation anchors, GitHub link, and highlighted Download CTA.
- **Hero Section**: Two-column layout with announcement badge, crisp value proposition, primary/secondary CTAs, quick-install terminal snippet, and an **interactive product dashboard mockup**.
- **Interactive Mockup Dashboard**: Real-time simulated Kafka broker issue, Docker container status, Redis cache telemetry, root-cause trace logs, and an **"Apply Patch"** interaction that dynamically demonstrates recovery verification.
- **Problem & Solution Section**: Highlights five common developer infrastructure pain points contrasted with Stack Pilot's deterministic troubleshooting workflow.
- **Features Grid**: 8 responsive cards covering failure simulation, root-cause analysis, Docker troubleshooting, Kafka diagnostics, Redis diagnostics, guided remediation, recovery verification, and DX-first workflows.
- **Connected 4-Step Process**: Clean visual workflow (Select Scenario → Reproduce Failure → Investigate Root Cause → Apply & Verify Fix).
- **Architecture & Technology Section**: Decoupled architecture topology showcasing Docker, Kafka, Redis, Toxiproxy, React, Spring Boot, SQLite, and YAML configuration.
- **Diagnostic Terminal Preview**: Realistic sample logs (`[INFO]`, `[WARN]`, `[ANALYSIS]`, `[ACTION]`) clearly labeled as illustrative preview data.
- **Multi-Platform Download Section**: Linux, macOS, and Windows selectors with package sizes, architecture details, release notes links, SHA-256 checksums, and terminal package manager commands (curl, Homebrew).
- **GitHub & Open Source Section**: View on GitHub, documentation, contributing guide, and issue tracker links.
- **Accessible Accordion FAQ**: All 8 questions answered with realistic, configurable details.
- **Footer**: Brand description, quick navigation links, status indicator, and copyright notice.

---

## 🛠️ Local Development

### Prerequisites
- Node.js LTS (v20+ recommended, v24 LTS verified)
- npm or yarn

### Setup Instructions

1. Install dependencies:
   ```bash
   npm install
   ```

2. Start the development server:
   ```bash
   npm run dev
   ```

3. Open your browser and navigate to:
   ```
   http://localhost:3000
   ```

4. Build for production:
   ```bash
   npm run build
   ```

5. Preview the production build:
   ```bash
   npm run start
   ```

---

## ⚙️ Centralized Configuration

All release versions, download URLs, repository links, documentation paths, and install scripts are centrally managed in:
[`src/config/site.ts`](src/config/site.ts)

When new binary releases are built or GitHub repository details change, update `SITE_CONFIG` in `src/config/site.ts`:

```typescript
export const SITE_CONFIG = {
  name: "Stack Pilot",
  tagline: "Find the Fault. Fix the Stack.",
  release: {
    version: "v0.4.2-beta",
    releaseDate: "October 2026",
    notesUrl: "https://github.com/shivammm21/troubleshooting-config/releases",
    checksumSha256: "...",
    platforms: [
      {
        os: "linux",
        downloadUrl: "https://github.com/.../stackpilot-linux-x64.tar.gz",
        isReady: true,
      },
      ...
    ]
  }
};
```

---

## 🚢 Deployment Instructions

### 1. Deploying to Vercel (Recommended)
1. Push your repository to GitHub.
2. Sign in to [Vercel](https://vercel.com) and click **"New Project"**.
3. Import your repository and select the `web` root directory (if in a monorepo).
4. Framework Preset will automatically detect **Next.js**.
5. Click **Deploy**.

### 2. Deploying to Netlify
1. Connect your repository on [Netlify](https://netlify.com).
2. Set build command to `npm run build`.
3. Set publish directory to `.next`.
4. Install the `@netlify/plugin-nextjs` plugin or deploy using Netlify CLI:
   ```bash
   netlify deploy --build
   ```

### 3. Deploying to Cloudflare Pages
1. Go to Cloudflare Dashboard > Workers & Pages > Create application > Pages.
2. Connect Git repository.
3. Select **Next.js** build preset.
4. Set build command: `npx @cloudflare/next-on-pages` or standard `npm run build`.
