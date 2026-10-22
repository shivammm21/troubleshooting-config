import { Hero } from "@/components/Hero";
import { ProblemSolution } from "@/components/ProblemSolution";
import { Features } from "@/components/Features";
import { ProductScreenshots } from "@/components/ProductScreenshots";
import { GitHubSection } from "@/components/GitHubSection";
import { FAQ } from "@/components/FAQ";

export default function Home() {
  return (
    <main className="flex flex-col min-h-screen bg-[#070b12] text-slate-100 selection:bg-[#00ffaa]/20 selection:text-[#00ffaa]">
      {/* 1. Hero Section with Live Mockup Dashboard */}
      <Hero />

      {/* 2. Problem & Solution */}
      <ProblemSolution />

      {/* 3. Comprehensive Features Grid */}
      <Features />

      {/* 4. Authentic Product Interface Showcase (Tool Tour) */}
      <ProductScreenshots />

      {/* 5. Open Source & GitHub Section */}
      <GitHubSection />

      {/* 6. Accessible Accordion FAQ */}
      <FAQ />
    </main>
  );
}
