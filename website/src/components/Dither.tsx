import { useEffect, useRef } from 'react'

const vertexShader = `#version 300 es
void main() {
  vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
  gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
`

const fragmentShader = `#version 300 es
precision highp float;

uniform vec2 resolution;
uniform float time;
uniform float waveSpeed;
uniform float waveFrequency;
uniform float waveAmplitude;
uniform vec3 waveColor;
uniform vec3 backgroundColor;
uniform vec2 mousePos;
uniform int enableMouseInteraction;
uniform float mouseRadius;
uniform float colorNum;
uniform float pixelSize;

out vec4 outColor;

vec4 mod289(vec4 x) { return x - floor(x * (1.0/289.0)) * 289.0; }
vec4 permute(vec4 x) { return mod289(((x * 34.0) + 1.0) * x); }
vec4 taylorInvSqrt(vec4 r) { return 1.79284291400159 - 0.85373472095314 * r; }
vec2 fade(vec2 t) { return t*t*t*(t*(t*6.0-15.0)+10.0); }

float cnoise(vec2 P) {
  vec4 Pi = floor(P.xyxy) + vec4(0.0,0.0,1.0,1.0);
  vec4 Pf = fract(P.xyxy) - vec4(0.0,0.0,1.0,1.0);
  Pi = mod289(Pi);
  vec4 ix = Pi.xzxz;
  vec4 iy = Pi.yyww;
  vec4 fx = Pf.xzxz;
  vec4 fy = Pf.yyww;
  vec4 i = permute(permute(ix) + iy);
  vec4 gx = fract(i * (1.0/41.0)) * 2.0 - 1.0;
  vec4 gy = abs(gx) - 0.5;
  vec4 tx = floor(gx + 0.5);
  gx = gx - tx;
  vec2 g00 = vec2(gx.x, gy.x);
  vec2 g10 = vec2(gx.y, gy.y);
  vec2 g01 = vec2(gx.z, gy.z);
  vec2 g11 = vec2(gx.w, gy.w);
  vec4 norm = taylorInvSqrt(vec4(dot(g00,g00), dot(g01,g01), dot(g10,g10), dot(g11,g11)));
  g00 *= norm.x; g01 *= norm.y; g10 *= norm.z; g11 *= norm.w;
  float n00 = dot(g00, vec2(fx.x, fy.x));
  float n10 = dot(g10, vec2(fx.y, fy.y));
  float n01 = dot(g01, vec2(fx.z, fy.z));
  float n11 = dot(g11, vec2(fx.w, fy.w));
  vec2 fade_xy = fade(Pf.xy);
  vec2 n_x = mix(vec2(n00, n01), vec2(n10, n11), fade_xy.x);
  return 2.3 * mix(n_x.x, n_x.y, fade_xy.y);
}

const int OCTAVES = 4;
float fbm(vec2 p) {
  float value = 0.0;
  float amp = 1.0;
  float freq = waveFrequency;
  for (int i = 0; i < OCTAVES; i++) {
    value += amp * abs(cnoise(p));
    p *= freq;
    amp *= waveAmplitude;
  }
  return value;
}

float pattern(vec2 p) {
  vec2 p2 = p - time * waveSpeed;
  return fbm(p + fbm(p2));
}

const float bayerMatrix8x8[64] = float[64](
  0.0/64.0, 48.0/64.0, 12.0/64.0, 60.0/64.0,  3.0/64.0, 51.0/64.0, 15.0/64.0, 63.0/64.0,
  32.0/64.0,16.0/64.0, 44.0/64.0, 28.0/64.0, 35.0/64.0,19.0/64.0, 47.0/64.0, 31.0/64.0,
  8.0/64.0, 56.0/64.0,  4.0/64.0, 52.0/64.0, 11.0/64.0,59.0/64.0,  7.0/64.0, 55.0/64.0,
  40.0/64.0,24.0/64.0, 36.0/64.0, 20.0/64.0, 43.0/64.0,27.0/64.0, 39.0/64.0, 23.0/64.0,
  2.0/64.0, 50.0/64.0, 14.0/64.0, 62.0/64.0,  1.0/64.0,49.0/64.0, 13.0/64.0, 61.0/64.0,
  34.0/64.0,18.0/64.0, 46.0/64.0, 30.0/64.0, 33.0/64.0,17.0/64.0, 45.0/64.0, 29.0/64.0,
  10.0/64.0,58.0/64.0,  6.0/64.0, 54.0/64.0,  9.0/64.0,57.0/64.0,  5.0/64.0, 53.0/64.0,
  42.0/64.0,26.0/64.0, 38.0/64.0, 22.0/64.0, 41.0/64.0,25.0/64.0, 37.0/64.0, 21.0/64.0
);

vec3 dither(vec2 uv, vec3 color) {
  vec2 scaledCoord = floor(uv * resolution / pixelSize);
  int x = int(mod(scaledCoord.x, 8.0));
  int y = int(mod(scaledCoord.y, 8.0));
  float threshold = bayerMatrix8x8[y * 8 + x] - 0.25;
  float step = 1.0 / (colorNum - 1.0);
  color += threshold * step;
  float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
  float bias = mix(0.2, 0.0, smoothstep(0.45, 0.8, luminance));
  color = clamp(color - bias, 0.0, 1.0);
  return floor(color * (colorNum - 1.0) + 0.5) / (colorNum - 1.0);
}

void main() {
  vec2 uv = gl_FragCoord.xy / resolution;
  // 先在像素网格上对齐采样点，等效于原实现中后处理 pass 的 uvPixel 采样
  vec2 uvPixel = (pixelSize / resolution) * floor(uv * resolution / pixelSize);
  vec2 p = uvPixel - 0.5;
  p.x *= resolution.x / resolution.y;
  float f = pattern(p);
  if (enableMouseInteraction == 1) {
    vec2 mouseNDC = (mousePos / resolution - 0.5) * vec2(1.0, -1.0);
    mouseNDC.x *= resolution.x / resolution.y;
    float dist = length(p - mouseNDC);
    float effect = 1.0 - smoothstep(0.0, mouseRadius, dist);
    f -= 0.5 * effect;
  }
  vec3 col = mix(backgroundColor, waveColor, clamp(f, 0.0, 1.0));
  outColor = vec4(dither(uv, col), 1.0);
}
`

interface DitherProps {
  waveSpeed?: number
  waveFrequency?: number
  waveAmplitude?: number
  waveColor?: [number, number, number]
  backgroundColor?: [number, number, number]
  colorNum?: number
  pixelSize?: number
  disableAnimation?: boolean
  enableMouseInteraction?: boolean
  mouseRadius?: number
}

export default function Dither(props: DitherProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const propsRef = useRef(props)
  propsRef.current = props

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const gl = canvas.getContext('webgl2', { antialias: false, alpha: false })
    if (!gl) return

    const compile = (type: number, source: string) => {
      const shader = gl.createShader(type)!
      gl.shaderSource(shader, source)
      gl.compileShader(shader)
      return shader
    }
    const program = gl.createProgram()!
    gl.attachShader(program, compile(gl.VERTEX_SHADER, vertexShader))
    gl.attachShader(program, compile(gl.FRAGMENT_SHADER, fragmentShader))
    gl.linkProgram(program)
    gl.useProgram(program)

    const u = (name: string) => gl.getUniformLocation(program, name)
    const loc = {
      resolution: u('resolution'),
      time: u('time'),
      waveSpeed: u('waveSpeed'),
      waveFrequency: u('waveFrequency'),
      waveAmplitude: u('waveAmplitude'),
      waveColor: u('waveColor'),
      backgroundColor: u('backgroundColor'),
      mousePos: u('mousePos'),
      enableMouseInteraction: u('enableMouseInteraction'),
      mouseRadius: u('mouseRadius'),
      colorNum: u('colorNum'),
      pixelSize: u('pixelSize'),
    }

    const vao = gl.createVertexArray()
    gl.bindVertexArray(vao)

    // 输出被量化到 pixelSize 网格上：1 个渲染像素对应屏幕上 pixelSize² 的块，
    // 再经 CSS pixelated 放大，与全分辨率渲染逐像素一致
    let renderScale = 0.5
    let animated = false
    const start = performance.now()
    const FRAME_MS = 1000 / 30
    let raf = 0
    let last = -Infinity

    const mouse = { x: 0, y: 0 }
    const onPointerMove = (e: PointerEvent) => {
      const rect = canvas.getBoundingClientRect()
      mouse.x = (e.clientX - rect.left) * renderScale
      mouse.y = (e.clientY - rect.top) * renderScale
    }
    window.addEventListener('pointermove', onPointerMove)

    const draw = () => {
      const p = propsRef.current
      gl.uniform1f(loc.waveSpeed, p.waveSpeed ?? 0.05)
      gl.uniform1f(loc.waveFrequency, p.waveFrequency ?? 3)
      gl.uniform1f(loc.waveAmplitude, p.waveAmplitude ?? 0.3)
      gl.uniform3f(loc.waveColor, ...(p.waveColor ?? [0.5, 0.5, 0.5]))
      gl.uniform3f(loc.backgroundColor, ...(p.backgroundColor ?? [0, 0, 0]))
      gl.uniform2f(loc.mousePos, mouse.x, mouse.y)
      gl.uniform1i(loc.enableMouseInteraction, p.enableMouseInteraction ? 1 : 0)
      gl.uniform1f(loc.mouseRadius, p.mouseRadius ?? 1)
      gl.uniform1f(loc.colorNum, p.colorNum ?? 4)
      gl.drawArrays(gl.TRIANGLES, 0, 3)
    }

    const resize = () => {
      renderScale = 1 / (propsRef.current.pixelSize ?? 2)
      const w = Math.max(1, Math.floor(canvas.clientWidth * renderScale))
      const h = Math.max(1, Math.floor(canvas.clientHeight * renderScale))
      if (canvas.width !== w || canvas.height !== h) {
        canvas.width = w
        canvas.height = h
        gl.viewport(0, 0, w, h)
      }
      gl.uniform2f(loc.resolution, canvas.width, canvas.height)
      gl.uniform1f(loc.pixelSize, 1)
      if (!animated) draw()
    }
    const observer = new ResizeObserver(resize)
    observer.observe(canvas)

    const still = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (still || props.disableAnimation) {
      gl.uniform1f(loc.time, 0)
      resize()
    } else {
      animated = true
      resize()
      const frame = () => {
        raf = requestAnimationFrame(frame)
        const now = performance.now()
        if (now - last < FRAME_MS) return
        last = now
        gl.uniform1f(loc.time, (now - start) / 1000)
        draw()
      }
      raf = requestAnimationFrame(frame)
    }

    // 注意：不能 loseContext()——React StrictMode 双挂载会复用同一 canvas 节点，
    // 第二次 getContext 拿到的会是同一个已丢失的 context
    return () => {
      cancelAnimationFrame(raf)
      observer.disconnect()
      window.removeEventListener('pointermove', onPointerMove)
    }
  }, [])

  return (
    <canvas
      ref={canvasRef}
      className="block h-full w-full"
      style={{ imageRendering: 'pixelated' }}
    />
  )
}
