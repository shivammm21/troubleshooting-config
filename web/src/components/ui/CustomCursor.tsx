"use client";

import React, { useEffect, useRef, useState } from "react";

// Hovering these grows the ring, since the native pointer cursor is hidden.
const INTERACTIVE_SELECTOR =
  'a, button, [role="button"], input, textarea, select, summary, label, [tabindex]:not([tabindex="-1"])';

type Ripple = { id: number; x: number; y: number };

/**
 * Circular cursor: a mint ring trails the pointer while a dot sits exactly on
 * it, and every click leaves a red ripple behind. Only activated for precise
 * pointers and never when the visitor prefers reduced motion, so touch devices
 * and keyboard users keep the native behaviour.
 */
export function CustomCursor() {
  const ringRef = useRef<HTMLDivElement>(null);
  const dotRef = useRef<HTMLDivElement>(null);
  const hoveringRef = useRef(false);
  const rippleIdRef = useRef(0);
  const timersRef = useRef<number[]>([]);

  const [enabled, setEnabled] = useState(false);
  const [hovering, setHovering] = useState(false);
  const [pressed, setPressed] = useState(false);
  const [ripples, setRipples] = useState<Ripple[]>([]);

  useEffect(() => {
    const finePointer = window.matchMedia("(pointer: fine)");
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");

    const sync = () => setEnabled(finePointer.matches && !reducedMotion.matches);

    sync();
    finePointer.addEventListener("change", sync);
    reducedMotion.addEventListener("change", sync);

    return () => {
      finePointer.removeEventListener("change", sync);
      reducedMotion.removeEventListener("change", sync);
    };
  }, []);

  useEffect(() => {
    if (!enabled) return;

    const root = document.documentElement;
    root.classList.add("custom-cursor");

    const timers = timersRef.current;
    let targetX = window.innerWidth / 2;
    let targetY = window.innerHeight / 2;
    let ringX = targetX;
    let ringY = targetY;
    let visible = false;
    let frame = 0;

    const fade = (opacity: string) => {
      if (ringRef.current) ringRef.current.style.opacity = opacity;
      if (dotRef.current) dotRef.current.style.opacity = opacity;
    };

    const show = () => {
      if (visible) return;
      visible = true;
      // Jump the ring straight to the pointer instead of sliding in from
      // wherever it was last parked.
      ringX = targetX;
      ringY = targetY;
      fade("1");
    };

    const hide = () => {
      if (!visible) return;
      visible = false;
      fade("0");
    };

    const handlePointerMove = (event: PointerEvent) => {
      if (event.pointerType === "touch") return;
      targetX = event.clientX;
      targetY = event.clientY;
      show();
    };

    const handlePointerOver = (event: PointerEvent) => {
      const node = event.target;
      const over =
        node instanceof Element && node.closest(INTERACTIVE_SELECTOR) !== null;
      if (over === hoveringRef.current) return;
      hoveringRef.current = over;
      setHovering(over);
    };

    const handlePointerOut = (event: PointerEvent) => {
      // relatedTarget is null when the pointer leaves the window entirely.
      if (event.relatedTarget === null) hide();
    };

    const handlePointerDown = (event: PointerEvent) => {
      if (event.pointerType === "touch") return;
      targetX = event.clientX;
      targetY = event.clientY;
      show();

      setPressed(true);
      timers.push(window.setTimeout(() => setPressed(false), 180));

      const id = ++rippleIdRef.current;
      setRipples((current) => [
        ...current.slice(-4),
        { id, x: event.clientX, y: event.clientY },
      ]);
      timers.push(
        window.setTimeout(() => {
          setRipples((current) => current.filter((ripple) => ripple.id !== id));
        }, 700),
      );
    };

    const render = () => {
      // Ease the ring towards the pointer; the dot stays locked to it.
      ringX += (targetX - ringX) * 0.2;
      ringY += (targetY - ringY) * 0.2;

      if (ringRef.current) {
        ringRef.current.style.transform = `translate3d(${ringX}px, ${ringY}px, 0)`;
      }
      if (dotRef.current) {
        dotRef.current.style.transform = `translate3d(${targetX}px, ${targetY}px, 0)`;
      }

      frame = requestAnimationFrame(render);
    };

    window.addEventListener("pointermove", handlePointerMove, { passive: true });
    window.addEventListener("pointerover", handlePointerOver, { passive: true });
    window.addEventListener("pointerout", handlePointerOut, { passive: true });
    window.addEventListener("pointerdown", handlePointerDown, { passive: true });
    window.addEventListener("blur", hide);
    frame = requestAnimationFrame(render);

    return () => {
      root.classList.remove("custom-cursor");
      window.removeEventListener("pointermove", handlePointerMove);
      window.removeEventListener("pointerover", handlePointerOver);
      window.removeEventListener("pointerout", handlePointerOut);
      window.removeEventListener("pointerdown", handlePointerDown);
      window.removeEventListener("blur", hide);
      cancelAnimationFrame(frame);
      timers.forEach((timer) => window.clearTimeout(timer));
      timers.length = 0;
    };
  }, [enabled]);

  if (!enabled) return null;

  const ringState = pressed
    ? "scale-90 border-[#ff3b30] bg-[#ff3b30]/15 opacity-90 shadow-[0_0_22px_rgba(255,59,48,0.55)]"
    : hovering
      ? "scale-150 border-[#00ffaa] bg-[#00ffaa]/10 opacity-80 shadow-[0_0_20px_rgba(0,255,170,0.45)]"
      : "scale-100 border-[#00ffaa]/60 bg-transparent opacity-70";

  return (
    <>
      {/* Trailing ring */}
      <div
        ref={ringRef}
        aria-hidden="true"
        className="pointer-events-none fixed left-0 top-0 z-[9999] opacity-0 transition-opacity duration-200"
        style={{ willChange: "transform, opacity" }}
      >
        <div
          className={`h-9 w-9 -translate-x-1/2 -translate-y-1/2 rounded-full border transition-[scale,border-color,background-color,box-shadow,opacity] duration-200 ease-out ${ringState}`}
        />
      </div>

      {/* Dot locked to the pointer */}
      <div
        ref={dotRef}
        aria-hidden="true"
        className="pointer-events-none fixed left-0 top-0 z-[9999] opacity-0 transition-opacity duration-200"
        style={{ willChange: "transform, opacity" }}
      >
        <div
          className={`h-1.5 w-1.5 -translate-x-1/2 -translate-y-1/2 rounded-full transition-colors duration-150 ${
            pressed ? "bg-[#ff3b30]" : "bg-[#00ffaa]"
          }`}
        />
      </div>

      {/* Red ripple on click */}
      {ripples.map((ripple) => (
        <span
          key={ripple.id}
          aria-hidden="true"
          className="cursor-ripple"
          style={{ left: ripple.x, top: ripple.y }}
        />
      ))}
    </>
  );
}
