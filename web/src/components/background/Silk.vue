<template>
  <div ref="containerRef" :class="className" :style="style" class="w-full h-full"></div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, watch, type CSSProperties, useTemplateRef } from 'vue'
import { Renderer, Program, Mesh, Plane, Camera } from 'ogl'

interface SilkProps {
  speed?: number
  scale?: number
  color?: string
  noiseIntensity?: number
  rotation?: number
  className?: string
  style?: CSSProperties
}

const props = withDefaults(defineProps<SilkProps>(), {
  speed: 5,
  scale: 1,
  color: '#7B7481',
  noiseIntensity: 1.5,
  rotation: 0,
  className: '',
  style: () => ({}),
})

const containerRef = useTemplateRef<HTMLDivElement>('containerRef')

const hexToNormalizedRGB = (hex: string): [number, number, number] => {
  const clean = hex.replace('#', '')
  const r = parseInt(clean.slice(0, 2), 16) / 255
  const g = parseInt(clean.slice(2, 4), 16) / 255
  const b = parseInt(clean.slice(4, 6), 16) / 255
  return [r, g, b]
}

const vertexShader = `
attribute vec2 uv;
attribute vec3 position;

uniform mat4 modelViewMatrix;
uniform mat4 projectionMatrix;

varying vec2 vUv;
varying vec3 vPosition;

void main() {
  vPosition = position;
  vUv = uv;
  gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
}
`

const fragmentShader = `
precision highp float;

varying vec2 vUv;
varying vec3 vPosition;

uniform float uTime;
uniform vec3 uColor;
uniform float uSpeed;
uniform float uScale;
uniform float uRotation;
uniform float uNoiseIntensity;

const float e = 2.71828182845904523536;

float noise(vec2 texCoord) {
  float G = e;
  vec2 r = (G * sin(G * texCoord));
  return fract(r.x * r.y * (1.0 + texCoord.x));
}

vec2 rotateUvs(vec2 uv, float angle) {
  float c = cos(angle);
  float s = sin(angle);
  mat2 rot = mat2(c, -s, s, c);
  return rot * uv;
}

void main() {
  float rnd = noise(gl_FragCoord.xy);
  vec2 uv = rotateUvs(vUv * uScale, uRotation);
  vec2 tex = uv * uScale;
  float tOffset = uSpeed * uTime;

  tex.y += 0.03 * sin(8.0 * tex.x - tOffset);

  float pattern = 0.6 +
                  0.4 * sin(5.0 * (tex.x + tex.y +
                                   cos(3.0 * tex.x + 5.0 * tex.y) +
                                   0.02 * tOffset) +
                           sin(20.0 * (tex.x + tex.y - 0.1 * tOffset)));

  vec4 col = vec4(uColor, 1.0) * vec4(pattern) - rnd / 15.0 * uNoiseIntensity;
  col.a = 1.0;
  gl_FragColor = col;
}
`

let renderer: Renderer | null = null
let mesh: Mesh | null = null
let program: Program | null = null
let camera: Camera | null = null
let animateId = 0

const initSilk = () => {
  const container = containerRef.value
  if (!container) return

  renderer = new Renderer({
    alpha: true,
    antialias: true,
  })

  const gl = renderer.gl
  gl.clearColor(0, 0, 0, 0)
  gl.canvas.style.backgroundColor = 'transparent'

  camera = new Camera(gl, { fov: 75 })
  camera.position.z = 1

  const resize = () => {
    if (!container || !camera) return

    let width = container.offsetWidth
    let height = container.offsetHeight

    let parent = container.parentElement
    while (parent && (!width || !height)) {
      if (parent.offsetWidth && parent.offsetHeight) {
        width = parent.offsetWidth
        height = parent.offsetHeight
        break
      }
      parent = parent.parentElement
    }

    if (!width || !height) {
      width = window.innerWidth
      height = window.innerHeight
    }

    width = Math.max(width, 300)
    height = Math.max(height, 300)

    renderer!.setSize(width, height)
    camera.perspective({ aspect: width / height })

    if (mesh) {
      const distance = camera.position.z
      const fov = camera.fov * (Math.PI / 180)
      const height2 = 2 * Math.tan(fov / 2) * distance
      const width2 = height2 * (width / height)

      mesh.scale.set(width2, height2, 1)
    }
  }

  window.addEventListener('resize', resize)

  const geometry = new Plane(gl, {
    width: 1,
    height: 1,
  })

  const colorRGB = hexToNormalizedRGB(props.color)

  program = new Program(gl, {
    vertex: vertexShader,
    fragment: fragmentShader,
    uniforms: {
      uSpeed: { value: props.speed },
      uScale: { value: props.scale },
      uNoiseIntensity: { value: props.noiseIntensity },
      uColor: { value: colorRGB },
      uRotation: { value: props.rotation },
      uTime: { value: 0 },
    },
  })

  mesh = new Mesh(gl, { geometry, program })
  container.appendChild(gl.canvas)

  gl.canvas.style.width = '100%'
  gl.canvas.style.height = '100%'
  gl.canvas.style.display = 'block'
  gl.canvas.style.position = 'absolute'
  gl.canvas.style.top = '0'
  gl.canvas.style.left = '0'
  gl.canvas.style.zIndex = '1'

  let lastTime = 0
  const update = (t: number) => {
    animateId = requestAnimationFrame(update)
    const deltaTime = (t - lastTime) / 1000
    lastTime = t

    if (program && mesh && camera) {
      program.uniforms.uTime.value += 0.1 * deltaTime
      program.uniforms.uSpeed.value = props.speed
      program.uniforms.uScale.value = props.scale
      program.uniforms.uNoiseIntensity.value = props.noiseIntensity
      program.uniforms.uColor.value = hexToNormalizedRGB(props.color)
      program.uniforms.uRotation.value = props.rotation
      renderer!.render({ scene: mesh, camera })
    }
  }
  animateId = requestAnimationFrame(update)

  resize()

  return () => {
    cancelAnimationFrame(animateId)
    window.removeEventListener('resize', resize)
    if (container && gl.canvas.parentNode === container) {
      container.removeChild(gl.canvas)
    }
    gl.getExtension('WEBGL_lose_context')?.loseContext()
  }
}

const cleanup = () => {
  if (animateId) {
    cancelAnimationFrame(animateId)
  }
  if (renderer) {
    const gl = renderer.gl
    const container = containerRef.value
    if (container && gl.canvas.parentNode === container) {
      container.removeChild(gl.canvas)
    }
    gl.getExtension('WEBGL_lose_context')?.loseContext()
  }
  renderer = null
  mesh = null
  camera = null
  program = null
}

onMounted(() => {
  initSilk()
})

onUnmounted(() => {
  cleanup()
})

watch(
  () => [props.speed, props.scale, props.color, props.noiseIntensity, props.rotation],
  () => {},
)
</script>

<style scoped>
div {
  width: 100% !important;
  height: 100% !important;
  min-height: 100% !important;
  display: block !important;
}

:deep(canvas) {
  width: 100% !important;
  height: 100% !important;
  min-height: 100% !important;
  display: block !important;
  object-fit: cover !important;
}
</style>
