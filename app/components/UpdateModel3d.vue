<script setup lang="ts">
import type * as Three from 'three'
import { voxelModelFromImage, type VoxelModel } from '~/utils/voxelArt'

// Das Bild eines Updates als echtes 3D-Modell: Die Pixel-Art-Szene (banner.png) wird zur Laufzeit in Voxel zerlegt
// (utils/voxelArt.ts) und mit three.js gezeichnet – ein InstancedMesh aus Quadern, weiches Licht von links oben plus
// Grundlicht. Das Modell schwingt sanft hin und her und lässt sich mit Maus, Finger oder Pfeiltasten drehen.
// - „Animationen reduzieren“: kein Schwingen, kein Funkeln – gezeichnet wird nur nach einer Eingabe. Ebenso, solange
//   Minecraft startet oder läuft (die Rechenzeit gehört dem Spiel), und bei WebGL ohne Grafikkarte (Software-Renderer
//   wie SwiftShader oder llvmpipe würden sonst den Launcher ausbremsen).
// - Außerhalb des Bildes, im Hintergrund oder geschlossen: keine Bilder; beim Schließen wird alles freigegeben.
// - Kein WebGL, Kontext verloren oder Bild nicht zerlegbar → `fallback` (der Banner zeigt das flache Bild).
// three.js wird erst hier geladen (eigener Chunk), nicht mit dem Launcher.
const props = defineProps<{ src: string }>()
const emit = defineEmits<{ ready: []; fallback: []; activate: [] }>()

const host = useTemplateRef<HTMLDivElement>('host')
const canvas = useTemplateRef<HTMLCanvasElement>('canvas')
const { reduced } = useMotion()
const games = useGamesStore()
const ready = ref(false)
/** WebGL läuft ohne Grafikkarte (Software-Renderer) – dann nur auf Wunsch zeichnen. */
const software = ref(false)
/** Ruhig: kein Schwingen und Funkeln, nur Bilder nach einer Eingabe. */
const resting = computed(() => reduced.value || software.value || games.runningCount > 0)
const dragging = ref(false)

/** Ruhige Haltung (auch das Standbild bei „Animationen reduzieren“): leicht von links oben. */
const REST_YAW = 0.38
const REST_PITCH = 0.16
/** Sanftes Schwingen: ±0,42 rad in 12 s. */
const SWING = 0.42
const SWING_MS = 12_000
/** Nach dem Loslassen so lange warten, bevor das Modell wieder von selbst schwingt. */
const RESUME_MS = 2500
/** Höchstens ~30 Bilder je Sekunde, solange es von selbst schwingt (beim Ziehen sofort). */
const FRAME_MS = 1000 / 32
const PITCH_MIN = -0.25
const PITCH_MAX = 0.55
const FOV = 26

// Fertige Modelle je Bild (z. B. Dialog erneut geöffnet) – klein, höchstens ein paar Versionen.
const cache = new Map<string, VoxelModel | null>()

async function loadModel(src: string): Promise<VoxelModel | null> {
  if (cache.has(src)) return cache.get(src)!
  const img = new Image()
  img.decoding = 'async'
  img.src = src
  await img.decode()
  const w = img.naturalWidth
  const h = img.naturalHeight
  if (!w || !h || w > 2048 || h > 2048) return null
  const scratch = document.createElement('canvas')
  scratch.width = w
  scratch.height = h
  const ctx = scratch.getContext('2d', { willReadFrequently: true })
  if (!ctx) return null
  ctx.imageSmoothingEnabled = false
  ctx.drawImage(img, 0, 0)
  const model = voxelModelFromImage(ctx.getImageData(0, 0, w, h))
  if (cache.size >= 6) cache.delete(cache.keys().next().value!)
  cache.set(src, model)
  return model
}

interface Stage {
  three: typeof Three
  renderer: Three.WebGLRenderer
  scene: Three.Scene
  camera: Three.PerspectiveCamera
  pivot: Three.Group
  mesh: Three.InstancedMesh
  geometry: Three.BoxGeometry
  material: Three.MeshLambertMaterial
  model: VoxelModel
  /** Funkeln: Quader-Index, Lage, Mitte seiner Gruppe, Phase. */
  sparks: { index: number; x: number; y: number; z: number; depth: number; cx: number; cy: number; phase: number }[]
  /** Wiederverwendet je Bild (keine neuen Objekte im Takt). */
  matrix: Three.Matrix4
  /** Funkeln gerade verkleinert (beim Ruhen wieder auf volle Größe). */
  twinkled: boolean
}

let stage: Stage | null = null
let disposed = false
let frame = 0
let lastFrame = 0
let onScreen = true
let yaw = REST_YAW
let pitch = REST_PITCH
/** Bis wann die Hand des Spielers gilt (danach schwingt es wieder von selbst). */
let handUntil = 0
let startedAt = 0
let drag: { id: number; x: number; y: number; moved: number; at: number } | null = null

function animating() {
  return !!stage && !resting.value && onScreen && !document.hidden && !disposed
}

/** Kamera so weit weg, dass das Modell in jeder Drehung ganz zu sehen ist. */
function fit() {
  const s = stage
  const el = host.value
  if (!s || !el) return
  const width = Math.max(1, el.clientWidth)
  const height = Math.max(1, el.clientHeight)
  s.renderer.setSize(width, height, false)
  const m = s.model
  const w = m.maxX - m.minX + 1
  const h = m.maxY - m.minY + 1
  const d = m.depth
  const aspect = width / height
  const tan = Math.tan(((FOV / 2) * Math.PI) / 180)
  // Halbe Ausdehnung über alle Drehungen (Gieren um y, Nicken begrenzt): Zylinder bzw. Rechteck mit der Tiefe.
  const around = Math.hypot(w / 2, d / 2)
  const tall = Math.hypot(h / 2, d / 2)
  const distance = around + Math.max(tall / tan, around / (tan * aspect)) * 1.04
  s.camera.aspect = aspect
  s.camera.position.set(0, 0, distance)
  s.camera.near = Math.max(0.1, distance - around * 2)
  s.camera.far = distance + around * 2
  s.camera.lookAt(0, 0, 0)
  s.camera.updateProjectionMatrix()
}

/** Kürzester Weg von `from` zu `to` (Winkel), damit es nach einer ganzen Drehung nicht zurückkurbelt. */
function towards(from: number, to: number, share: number) {
  const diff = Math.atan2(Math.sin(to - from), Math.cos(to - from))
  return from + diff * share
}

function render(now: number) {
  const s = stage
  if (!s) return
  const auto = !resting.value && !drag && now >= handUntil
  if (auto) {
    const target = REST_YAW * 0.25 + SWING * Math.sin(((now - startedAt) / SWING_MS) * Math.PI * 2)
    // Nach dem Ziehen gleitet es zurück in die Schwingung.
    yaw = towards(yaw, target, 0.05)
    pitch += (REST_PITCH - pitch) * 0.05
  }
  s.pivot.rotation.set(pitch, yaw, 0)
  if (s.sparks.length && (!resting.value || s.twinkled)) {
    const matrix = s.matrix
    s.twinkled = !resting.value
    for (const spark of s.sparks) {
      const scale = resting.value ? 1 : 0.55 + 0.45 * Math.abs(Math.sin(now / 900 + spark.phase))
      matrix.makeScale(scale, scale, spark.depth * scale)
      matrix.setPosition(spark.cx + (spark.x - spark.cx) * scale, spark.cy + (spark.y - spark.cy) * scale, spark.z)
      s.mesh.setMatrixAt(spark.index, matrix)
    }
    s.mesh.instanceMatrix.needsUpdate = true
  }
  s.renderer.render(s.scene, s.camera)
  if (!ready.value) {
    ready.value = true
    emit('ready')
  }
}

function loop(now: number) {
  frame = 0
  if (!animating()) return
  if (now - lastFrame >= FRAME_MS - 2) {
    lastFrame = now
    render(now)
  }
  frame = requestAnimationFrame(loop)
}

function wake() {
  if (frame === 0 && animating()) frame = requestAnimationFrame(loop)
}

/** Ein Bild auf Wunsch (Ziehen, Tasten, Größe) – auch bei „Animationen reduzieren“. */
function redraw() {
  render(performance.now())
}

function build(three: typeof Three, model: VoxelModel, target: HTMLCanvasElement): Stage {
  const renderer = new three.WebGLRenderer({ canvas: target, alpha: true, antialias: true, powerPreference: 'low-power' })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
  renderer.setClearColor(0x000000, 0)
  const scene = new three.Scene()
  const camera = new three.PerspectiveCamera(FOV, 1, 1, 1000)
  // Weiches Licht von links oben (wie in der Pixel-Art) und kräftiges Grundlicht: Vorderseiten zeigen die Farben
  // des Bilds, Oberseiten etwas heller, die rechte Seite im Schatten (Lambert ohne Altlicht-Skalierung: ÷ π).
  scene.add(new three.AmbientLight(0xffffff, 2))
  const sun = new three.DirectionalLight(0xffffff, 2.1)
  sun.position.set(-0.6, 1, 0.75)
  scene.add(sun)
  const geometry = new three.BoxGeometry(1, 1, 1)
  const material = new three.MeshLambertMaterial({ color: 0xffffff })
  const mesh = new three.InstancedMesh(geometry, material, model.boxes.length)
  const matrix = new three.Matrix4()
  const color = new three.Color()
  const cx = (model.minX + model.maxX + 1) / 2
  const cy = (model.minY + model.maxY + 1) / 2
  const sparks: Stage['sparks'] = []
  const sparkCenter = new Map<number, { x: number; y: number; n: number }>()
  model.boxes.forEach((box, i) => {
    const x = box.x + 0.5 - cx
    const y = cy - (box.y + 0.5)
    const z = (box.z0 + box.z1) / 2
    const depth = box.z1 - box.z0
    matrix.makeScale(1, 1, depth)
    matrix.setPosition(x, y, z)
    mesh.setMatrixAt(i, matrix)
    color.setRGB(((box.color >> 16) & 255) / 255, ((box.color >> 8) & 255) / 255, (box.color & 255) / 255, three.SRGBColorSpace)
    mesh.setColorAt(i, color)
    if (box.kind === 'spark') {
      sparks.push({ index: i, x, y, z, depth, cx: 0, cy: 0, phase: (box.group * 2.399) % (Math.PI * 2) })
      const c = sparkCenter.get(box.group) ?? { x: 0, y: 0, n: 0 }
      c.x += x
      c.y += y
      c.n++
      sparkCenter.set(box.group, c)
    }
  })
  sparks.forEach((spark) => {
    const group = model.boxes[spark.index]!.group
    const c = sparkCenter.get(group)!
    spark.cx = c.x / c.n
    spark.cy = c.y / c.n
  })
  mesh.instanceMatrix.needsUpdate = true
  if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true
  const pivot = new three.Group()
  pivot.add(mesh)
  scene.add(pivot)
  return { three, renderer, scene, camera, pivot, mesh, geometry, material, model, sparks, matrix: new three.Matrix4(), twinkled: false }
}

/** Zeichnet WebGL ohne Grafikkarte? (Name des Renderers; ohne Angabe: nein.) */
function softwareRenderer(renderer: Three.WebGLRenderer): boolean {
  try {
    const gl = renderer.getContext()
    const info = gl.getExtension('WEBGL_debug_renderer_info')
    const name = String(gl.getParameter(info ? info.UNMASKED_RENDERER_WEBGL : gl.RENDERER) ?? '')
    return /swiftshader|llvmpipe|softpipe|software|basic render/i.test(name)
  } catch {
    return false
  }
}

function dispose() {
  cancelAnimationFrame(frame)
  frame = 0
  const s = stage
  stage = null
  if (!s) return
  canvas.value?.removeEventListener('webglcontextlost', onContextLost)
  s.mesh.dispose()
  s.geometry.dispose()
  s.material.dispose()
  s.renderer.dispose()
  // GPU-Speicher sofort freigeben (sonst erst, wenn der Browser den Kontext einsammelt).
  s.renderer.forceContextLoss()
}

function onContextLost(e: Event) {
  e.preventDefault()
  dispose()
  emit('fallback')
}

// --- Eingaben -----------------------------------------------------------------------------------------------------

function onPointerDown(e: PointerEvent) {
  if (!stage || (e.pointerType === 'mouse' && e.button !== 0)) return
  drag = { id: e.pointerId, x: e.clientX, y: e.clientY, moved: 0, at: performance.now() }
  dragging.value = true
  ;(e.currentTarget as HTMLElement).setPointerCapture?.(e.pointerId)
}

function onPointerMove(e: PointerEvent) {
  if (!drag || e.pointerId !== drag.id) return
  const dx = e.clientX - drag.x
  const dy = e.clientY - drag.y
  drag.x = e.clientX
  drag.y = e.clientY
  drag.moved += Math.abs(dx) + Math.abs(dy)
  yaw += dx * 0.012
  // Finger: nur drehen (senkrecht bleibt Scrollen der Seite), Maus: auch nicken.
  if (e.pointerType === 'mouse' || e.pointerType === 'pen') pitch = Math.min(PITCH_MAX, Math.max(PITCH_MIN, pitch + dy * 0.008))
  redraw()
}

function onPointerUp(e: PointerEvent) {
  if (!drag || e.pointerId !== drag.id) return
  const click = drag.moved < 6 && performance.now() - drag.at < 500
  drag = null
  dragging.value = false
  handUntil = performance.now() + RESUME_MS
  // Kurzer Klick ohne Ziehen: wie ein Klick auf das Bild (z. B. Beitrag öffnen).
  if (click && e.type === 'pointerup') emit('activate')
  wake()
}

function onKey(e: KeyboardEvent) {
  if (!stage) return
  const step = 0.2
  if (e.key === 'ArrowLeft') yaw -= step
  else if (e.key === 'ArrowRight') yaw += step
  else if (e.key === 'ArrowUp') pitch = Math.max(PITCH_MIN, pitch - step / 2)
  else if (e.key === 'ArrowDown') pitch = Math.min(PITCH_MAX, pitch + step / 2)
  else if (e.key === 'Home') {
    yaw = REST_YAW
    pitch = REST_PITCH
  } else return
  e.preventDefault()
  handUntil = performance.now() + RESUME_MS
  redraw()
  wake()
}

// --- Lebenszyklus ---------------------------------------------------------------------------------------------------

let resizeObserver: ResizeObserver | undefined
let viewObserver: IntersectionObserver | undefined

function onVisibility() {
  if (!document.hidden && stage) redraw()
  wake()
}

async function start() {
  const target = canvas.value
  if (!target) return
  try {
    const [three, model] = await Promise.all([import('three'), loadModel(props.src)])
    if (disposed) return
    if (!model) {
      emit('fallback')
      return
    }
    // WebGLRenderer wirft ohne WebGL („Error creating WebGL context“).
    stage = build(three, model, target)
    software.value = softwareRenderer(stage.renderer)
  } catch {
    if (!disposed) emit('fallback')
    return
  }
  target.addEventListener('webglcontextlost', onContextLost)
  startedAt = performance.now()
  // Bei „Animationen reduzieren“ in ruhiger Haltung, sonst mitten in der Schwingung beginnen.
  yaw = REST_YAW
  pitch = REST_PITCH
  fit()
  redraw()
  wake()
}

watch(resting, () => {
  if (stage) redraw()
  wake()
})

onMounted(() => {
  resizeObserver = new ResizeObserver(() => {
    if (!stage) return
    fit()
    redraw()
  })
  if (host.value) resizeObserver.observe(host.value)
  viewObserver = new IntersectionObserver((entries) => {
    onScreen = entries[entries.length - 1]?.isIntersecting ?? true
    wake()
  })
  if (host.value) viewObserver.observe(host.value)
  document.addEventListener('visibilitychange', onVisibility)
  void start()
})

onBeforeUnmount(() => {
  disposed = true
  resizeObserver?.disconnect()
  viewObserver?.disconnect()
  document.removeEventListener('visibilitychange', onVisibility)
  dispose()
})
</script>

<template>
  <div
    ref="host"
    class="model3d"
    :class="{ 'is-ready': ready, 'is-dragging': dragging }"
    role="img"
    :aria-label="t('updateModel.label')"
    :tabindex="ready ? 0 : -1"
    @pointerdown="onPointerDown"
    @pointermove="onPointerMove"
    @pointerup="onPointerUp"
    @pointercancel="onPointerUp"
    @keydown="onKey"
  >
    <canvas ref="canvas" class="model3d-canvas" />
    <span v-if="ready" class="model3d-hint" aria-hidden="true">
      <svg viewBox="0 0 24 24" class="size-3" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M3 12a9 9 0 0 1 15.5-6.2M21 12a9 9 0 0 1-15.5 6.2M18.5 2.5v3.3h-3.3M5.5 21.5v-3.3h3.3" />
      </svg>
      {{ t('updateModel.hint') }}
    </span>
  </div>
</template>

<style scoped>
.model3d {
  cursor: grab;
  touch-action: pan-y;
  outline: none;
  border-radius: 1rem;
}
.model3d.is-dragging {
  cursor: grabbing;
}
.model3d:focus-visible {
  box-shadow: 0 0 0 2px var(--color-primary-400);
}
.model3d-canvas {
  display: block;
  width: 100%;
  height: 100%;
  opacity: 0;
  transition: opacity 0.35s ease;
}
.is-ready .model3d-canvas {
  opacity: 1;
}
/* Hinweis „Ziehen zum Drehen“: nur beim Zeigen darauf oder mit Tastatur-Fokus. */
.model3d-hint {
  position: absolute;
  right: 0.25rem;
  bottom: 0.25rem;
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
  font-size: 0.65rem;
  font-weight: 600;
  color: #e9eef9;
  background: rgb(12 16 14 / 0.72);
  box-shadow: inset 0 0 0 1px rgb(255 255 255 / 0.12);
  opacity: 0;
  translate: 0 4px;
  transition: opacity 0.2s ease, translate 0.2s ease;
  pointer-events: none;
}
.model3d:hover .model3d-hint,
.model3d:focus-visible .model3d-hint {
  opacity: 1;
  translate: 0 0;
}
:root[data-reduced-motion] .model3d-canvas,
:root[data-reduced-motion] .model3d-hint {
  transition: none;
}
</style>
