"use client";

import { useEffect } from "react";
import Lenis from "lenis";

/** Height of the fixed header, so an anchored section clears it. */
const HEADER_OFFSET = 88;

/**
 * Inertial wheel scrolling for the marketing site. Touch screens keep their
 * native momentum, and Lenis turns the smoothing off by itself when the
 * visitor prefers reduced motion (`respectReducedMotion` defaults to true).
 */
export function SmoothScroll() {
  useEffect(() => {
    const lenis = new Lenis({
      // Slightly softer than the 0.1 default: the page glides after a wheel tick.
      lerp: 0.085,
      wheelMultiplier: 1,
      smoothWheel: true,
      autoRaf: true,
    });

    // Anchor links glide to the section instead of jumping, and the fixed
    // header no longer covers the heading of the targeted section.
    const handleClick = (event: MouseEvent) => {
      if (event.defaultPrevented || event.button !== 0) return;
      if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;

      const link = (event.target as Element | null)?.closest<HTMLAnchorElement>("a[href]");
      if (!link || (link.target && link.target !== "_self")) return;

      const url = new URL(link.href, window.location.href);
      if (url.origin !== window.location.origin) return;
      if (url.pathname !== window.location.pathname) return;

      // "Back to top" links use a bare "#".
      if (!url.hash) {
        if (!link.getAttribute("href")?.startsWith("#")) return;
        event.preventDefault();
        lenis.scrollTo(0, { duration: 1.1 });
        return;
      }

      const target = document.getElementById(decodeURIComponent(url.hash.slice(1)));
      if (!target) return;

      event.preventDefault();
      lenis.scrollTo(target, { offset: -HEADER_OFFSET, duration: 1.1 });
      window.history.pushState(null, "", url.hash);
    };

    document.addEventListener("click", handleClick);

    return () => {
      document.removeEventListener("click", handleClick);
      lenis.destroy();
    };
  }, []);

  return null;
}
