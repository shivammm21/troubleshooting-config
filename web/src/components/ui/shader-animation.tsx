"use client"

import { useEffect, useRef } from "react"
import * as THREE from "three"

export function ShaderAnimation({
  className,
  style,
}: {
  className?: string
  style?: React.CSSProperties
}) {
  const containerRef = useRef<HTMLDivElement>(null)
  const sceneRef = useRef<{
    camera: THREE.Camera
    scene: THREE.Scene
    renderer: THREE.WebGLRenderer
    uniforms: {
      time: { value: number }
      resolution: { value: THREE.Vector2 }
      mouse: { value: THREE.Vector2 }
    }
    animationId: number
    targetMouse: THREE.Vector2
  } | null>(null)

  useEffect(() => {
    if (!containerRef.current) return

    const container = containerRef.current

    // Vertex shader
    const vertexShader = `
      void main() {
        gl_Position = vec4( position, 1.0 );
      }
    `

    // Interactive Fragment shader with Brand Palette (Neon Mint, Cyan, Emerald, Electric Indigo)
    const fragmentShader = `
      #define TWO_PI 6.2831853072
      #define PI 3.14159265359

      precision highp float;
      uniform vec2 resolution;
      uniform float time;
      uniform vec2 mouse;

      void main(void) {
        vec2 uv = (gl_FragCoord.xy * 2.0 - resolution.xy) / min(resolution.x, resolution.y);
        
        // Mouse warp & interaction
        vec2 m = mouse * 1.5;
        float dMouse = length(uv - m);
        float mousePush = smoothstep(0.8, 0.0, dMouse);
        
        // Distort UV with mouse displacement
        uv += (uv - m) * mousePush * 0.28;

        float t = time * 0.045;
        float lineWidth = 0.0024 + mousePush * 0.0018;

        vec3 color = vec3(0.0);
        for(int j = 0; j < 3; j++){
          for(int i = 0; i < 5; i++){
            float wave = abs(fract(t - 0.012 * float(j) + float(i) * 0.012) * 5.0 - length(uv) + mod(uv.x + uv.y + mouse.x * 0.2, 0.22));
            color[j] += (lineWidth * float(i * i)) / (wave + 0.0001);
          }
        }

        // Color grading to brand: Mint (#00ffaa), Cyan (#38bdf8), and deep Emerald/Indigo
        vec3 mint = vec3(0.0, 1.0, 0.66);     // #00ffaa
        vec3 cyan = vec3(0.22, 0.74, 0.97);    // #38bdf8
        vec3 deepBlue = vec3(0.1, 0.2, 0.55);

        vec3 finalColor = color.r * mint * 1.1 + color.g * cyan * 0.95 + color.b * deepBlue * 0.8;
        
        // Extra glow near cursor
        finalColor += mint * mousePush * 0.25;

        gl_FragColor = vec4(finalColor, 1.0);
      }
    `

    // Initialize Three.js scene
    const camera = new THREE.Camera()
    camera.position.z = 1

    const scene = new THREE.Scene()
    const geometry = new THREE.PlaneGeometry(2, 2)

    const uniforms = {
      time: { value: 1.0 },
      resolution: { value: new THREE.Vector2() },
      mouse: { value: new THREE.Vector2(0, 0) },
    }

    const material = new THREE.ShaderMaterial({
      uniforms: uniforms,
      vertexShader: vertexShader,
      fragmentShader: fragmentShader,
    })

    const mesh = new THREE.Mesh(geometry, material)
    scene.add(mesh)

    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
    // 1.5x keeps the gradient sharp without paying for a full 2x buffer.
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.5))

    container.appendChild(renderer.domElement)

    // Handle window resize
    const onWindowResize = () => {
      if (!container) return
      const width = container.clientWidth
      const height = container.clientHeight
      renderer.setSize(width, height)
      uniforms.resolution.value.x = renderer.domElement.width
      uniforms.resolution.value.y = renderer.domElement.height
    }

    onWindowResize()
    window.addEventListener("resize", onWindowResize, false)

    // Mouse movement listener (listens globally on window for fluid tracking across the whole hero)
    const targetMouse = new THREE.Vector2(0, 0)

    const onPointerMove = (e: MouseEvent | TouchEvent) => {
      let clientX = 0
      let clientY = 0

      if ("touches" in e && e.touches.length > 0) {
        clientX = e.touches[0].clientX
        clientY = e.touches[0].clientY
      } else if ("clientX" in e) {
        clientX = (e as MouseEvent).clientX
        clientY = (e as MouseEvent).clientY
      }

      const rect = container.getBoundingClientRect()
      // Normalize to -1.0 to 1.0 relative to container center
      const x = ((clientX - rect.left) / rect.width) * 2 - 1
      const y = -(((clientY - rect.top) / rect.height) * 2 - 1)

      targetMouse.set(x, y)
    }

    window.addEventListener("mousemove", onPointerMove, { passive: true })
    window.addEventListener("touchmove", onPointerMove, { passive: true })

    // Animation loop with smooth mouse interpolation (lerp)
    let animationId = 0
    let running = false

    const frame = () => {
      animationId = requestAnimationFrame(frame)
      uniforms.time.value += 0.05

      // Smooth lerp to mouse position
      uniforms.mouse.value.lerp(targetMouse, 0.08)

      renderer.render(scene, camera)

      if (sceneRef.current) {
        sceneRef.current.animationId = animationId
      }
    }

    const start = () => {
      if (running) return
      running = true
      animationId = requestAnimationFrame(frame)
      if (sceneRef.current) sceneRef.current.animationId = animationId
    }

    const stop = () => {
      running = false
      cancelAnimationFrame(animationId)
    }

    // This full-screen shader is the most expensive thing on the page. Keep it
    // idle while the hero is scrolled out of view or the tab is in the
    // background, so scrolling the rest of the page keeps a full frame rate.
    let inView = true
    const sync = () => {
      if (inView && !document.hidden) start()
      else stop()
    }

    const observer = new IntersectionObserver(
      ([entry]) => {
        inView = entry.isIntersecting
        sync()
      },
      { rootMargin: "120px" }
    )
    observer.observe(container)

    document.addEventListener("visibilitychange", sync)

    // Store scene references for cleanup
    sceneRef.current = {
      camera,
      scene,
      renderer,
      uniforms,
      animationId: 0,
      targetMouse,
    }

    start()

    // Cleanup function
    return () => {
      window.removeEventListener("resize", onWindowResize)
      window.removeEventListener("mousemove", onPointerMove)
      window.removeEventListener("touchmove", onPointerMove)
      document.removeEventListener("visibilitychange", sync)
      observer.disconnect()

      if (sceneRef.current) {
        cancelAnimationFrame(sceneRef.current.animationId)

        if (container && sceneRef.current.renderer.domElement && container.contains(sceneRef.current.renderer.domElement)) {
          container.removeChild(sceneRef.current.renderer.domElement)
        }

        sceneRef.current.renderer.dispose()
        geometry.dispose()
        material.dispose()
      }
    }
  }, [])

  return (
    <div
      ref={containerRef}
      className={className || "w-full h-full"}
      style={{
        overflow: "hidden",
        ...style,
      }}
    />
  )
}
