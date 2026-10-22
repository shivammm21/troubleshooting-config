"use client";

import React, { useEffect, useRef } from "react";

export function MouseSpotlight() {
  const spotlightRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let mouseX = -1000;
    let mouseY = -1000;
    let currentX = -1000;
    let currentY = -1000;
    let animationFrameId: number;

    const handleMouseMove = (e: MouseEvent) => {
      mouseX = e.clientX;
      mouseY = e.clientY;
    };

    const handleTouchMove = (e: TouchEvent) => {
      if (e.touches.length > 0) {
        mouseX = e.touches[0].clientX;
        mouseY = e.touches[0].clientY;
      }
    };

    const render = () => {
      // Smooth linear interpolation for gentle fluid trailing
      currentX += (mouseX - currentX) * 0.12;
      currentY += (mouseY - currentY) * 0.12;

      if (spotlightRef.current) {
        spotlightRef.current.style.transform = `translate3d(${currentX - 350}px, ${currentY - 350}px, 0)`;
      }

      animationFrameId = requestAnimationFrame(render);
    };

    window.addEventListener("mousemove", handleMouseMove, { passive: true });
    window.addEventListener("touchmove", handleTouchMove, { passive: true });
    animationFrameId = requestAnimationFrame(render);

    return () => {
      window.removeEventListener("mousemove", handleMouseMove);
      window.removeEventListener("touchmove", handleTouchMove);
      cancelAnimationFrame(animationFrameId);
    };
  }, []);

  return (
    <div className="fixed inset-0 pointer-events-none z-20 overflow-hidden">
      {/* Primary Neon Mint / Cyan interactive glow sphere */}
      <div
        ref={spotlightRef}
        className="w-[700px] h-[700px] rounded-full pointer-events-none transition-opacity duration-300 opacity-60"
        style={{
          background:
            "radial-gradient(circle, rgba(0, 255, 170, 0.13) 0%, rgba(56, 189, 248, 0.08) 35%, transparent 70%)",
          filter: "blur(40px)",
          willChange: "transform",
        }}
      />
    </div>
  );
}
