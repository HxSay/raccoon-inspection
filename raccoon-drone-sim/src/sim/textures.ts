import * as THREE from 'three'

/**
 * 程序化生成贴图（CanvasTexture），避免引入外部图片资源，同时满足写实 PBR 所需的基础纹理。
 * 后续可替换为摄影测量/素材库的高精度贴图，接口保持为 THREE.Texture 即可。
 */

function makeCanvas(w: number, h: number, draw: (ctx: CanvasRenderingContext2D, w: number, h: number) => void): HTMLCanvasElement {
  const c = document.createElement('canvas')
  c.width = w
  c.height = h
  const ctx = c.getContext('2d')!
  draw(ctx, w, h)
  return c
}

/** 平滑值噪声（无逐像素 random，避免「砂纸」颗粒感） */
function smoothNoise2D(x: number, y: number): number {
  const xi = Math.floor(x)
  const yi = Math.floor(y)
  const xf = x - xi
  const yf = y - yi
  const u = xf * xf * (3 - 2 * xf)
  const v = yf * yf * (3 - 2 * yf)
  const h = (a: number, b: number) => {
    const t = Math.sin(a * 127.1 + b * 311.7) * 43758.5453
    return t - Math.floor(t)
  }
  const n00 = h(xi, yi)
  const n10 = h(xi + 1, yi)
  const n01 = h(xi, yi + 1)
  const n11 = h(xi + 1, yi + 1)
  const nx0 = n00 * (1 - u) + n10 * u
  const nx1 = n01 * (1 - u) + n11 * u
  return nx0 * (1 - v) + nx1 * v
}

/** 草地：柔和草甸色 + 低频土色斑块（适合大面积地面） */
export function createGrassTexture(): THREE.CanvasTexture {
  const c = makeCanvas(512, 512, (ctx, w, h) => {
    const img = ctx.createImageData(w, h)
    for (let y = 0; y < h; y++) {
      for (let x = 0; x < w; x++) {
        const i = (y * w + x) * 4
        const nx = x / w
        const ny = y / h
        const n1 = smoothNoise2D(nx * 6, ny * 6)
        const n2 = smoothNoise2D(nx * 14 + 2.1, ny * 12 + 1.7)
        const n3 = smoothNoise2D(nx * 28, ny * 26)
        const blend = n1 * 0.55 + n2 * 0.3 + n3 * 0.15
        const dirtPatch = smoothNoise2D(nx * 3.2, ny * 3.2)
        const towardDirt = THREE.MathUtils.smoothstep(dirtPatch, 0.35, 0.72)
        const gBase = 72 + blend * 28
        const rBase = 38 + blend * 22 + towardDirt * 18
        const bBase = 32 + blend * 18 + towardDirt * 8
        const g = THREE.MathUtils.clamp(gBase - towardDirt * 12, 45, 118)
        const r = THREE.MathUtils.clamp(rBase, 32, 110)
        const b = THREE.MathUtils.clamp(bBase, 28, 95)
        img.data[i] = r
        img.data[i + 1] = g
        img.data[i + 2] = b
        img.data[i + 3] = 255
      }
    }
    ctx.putImageData(img, 0, 0)
  })
  const t = new THREE.CanvasTexture(c)
  t.wrapS = t.wrapT = THREE.RepeatWrapping
  t.repeat.set(18, 18)
  t.anisotropy = 8
  t.colorSpace = THREE.SRGBColorSpace
  return t
}

/**
 * 地面粗糙度贴图：整体偏「很糙」（高 roughness），避免暗部被当成光滑产生白条高光。
 */
export function createTerrainRoughnessTexture(): THREE.CanvasTexture {
  const c = makeCanvas(256, 256, (ctx, w, h) => {
    const img = ctx.createImageData(w, h)
    for (let y = 0; y < h; y++) {
      for (let x = 0; x < w; x++) {
        const i = (y * w + x) * 4
        const n = smoothNoise2D(x / 48, y / 48) * 0.06 + smoothNoise2D(x / 18, y / 18) * 0.04
        const v = Math.floor(255 * (0.88 + n))
        img.data[i] = v
        img.data[i + 1] = v
        img.data[i + 2] = v
        img.data[i + 3] = 255
      }
    }
    ctx.putImageData(img, 0, 0)
  })
  const t = new THREE.CanvasTexture(c)
  t.wrapS = t.wrapT = THREE.RepeatWrapping
  t.repeat.set(22, 22)
  t.anisotropy = 4
  return t
}

/** 泥土 / 裸露地表（仅作调色参考；主地面已用草地贴图） */
export function createDirtTexture(): THREE.CanvasTexture {
  const c = makeCanvas(256, 256, (ctx, w, h) => {
    const img = ctx.createImageData(w, h)
    for (let y = 0; y < h; y++) {
      for (let x = 0; x < w; x++) {
        const i = (y * w + x) * 4
        const n = smoothNoise2D(x / 32, y / 32)
        const v = 88 + n * 22
        img.data[i] = v + 8
        img.data[i + 1] = v - 2
        img.data[i + 2] = v - 18
        img.data[i + 3] = 255
      }
    }
    ctx.putImageData(img, 0, 0)
  })
  const t = new THREE.CanvasTexture(c)
  t.wrapS = t.wrapT = THREE.RepeatWrapping
  t.repeat.set(16, 16)
  t.anisotropy = 4
  t.colorSpace = THREE.SRGBColorSpace
  return t
}

/** 热镀锌钢：灰蓝金属 + 细微划痕 */
export function createGalvanizedMetalTexture(): THREE.CanvasTexture {
  const c = makeCanvas(256, 256, (ctx, w, h) => {
    const g = ctx.createLinearGradient(0, 0, w, h)
    g.addColorStop(0, '#8a939e')
    g.addColorStop(1, '#6f7780')
    ctx.fillStyle = g
    ctx.fillRect(0, 0, w, h)
    for (let i = 0; i < 4000; i++) {
      ctx.strokeStyle = `rgba(255,255,255,${0.02 + Math.random() * 0.04})`
      ctx.beginPath()
      ctx.moveTo(Math.random() * w, Math.random() * h)
      ctx.lineTo(Math.random() * w, Math.random() * h)
      ctx.stroke()
    }
  })
  const t = new THREE.CanvasTexture(c)
  t.wrapS = t.wrapT = THREE.RepeatWrapping
  t.repeat.set(3, 3)
  t.anisotropy = 8
  t.colorSpace = THREE.SRGBColorSpace
  return t
}

/** 混凝土基础 */
export function createConcreteTexture(): THREE.CanvasTexture {
  const c = makeCanvas(256, 256, (ctx, w, h) => {
    ctx.fillStyle = '#9a9a96'
    ctx.fillRect(0, 0, w, h)
    for (let i = 0; i < 8000; i++) {
      const x = Math.random() * w
      const y = Math.random() * h
      ctx.fillStyle = `rgba(0,0,0,${0.02 + Math.random() * 0.04})`
      ctx.fillRect(x, y, 1.2, 1.2)
    }
  })
  const t = new THREE.CanvasTexture(c)
  t.wrapS = t.wrapT = THREE.RepeatWrapping
  t.repeat.set(2, 2)
  t.anisotropy = 4
  t.colorSpace = THREE.SRGBColorSpace
  return t
}

/** 碳纤维机身 */
export function createCarbonTexture(): THREE.CanvasTexture {
  const c = makeCanvas(256, 256, (ctx, w, h) => {
    ctx.fillStyle = '#1a1d22'
    ctx.fillRect(0, 0, w, h)
    for (let y = 0; y < h; y += 2) {
      ctx.strokeStyle = `rgba(255,255,255,${0.03 + (y % 6) * 0.008})`
      ctx.beginPath()
      ctx.moveTo(0, y)
      ctx.lineTo(w, y + 6)
      ctx.stroke()
    }
  })
  const t = new THREE.CanvasTexture(c)
  t.wrapS = t.wrapT = THREE.RepeatWrapping
  t.repeat.set(4, 4)
  t.anisotropy = 8
  t.colorSpace = THREE.SRGBColorSpace
  return t
}

export function disposeTexture(t: THREE.Texture | null | undefined) {
  t?.dispose()
}
