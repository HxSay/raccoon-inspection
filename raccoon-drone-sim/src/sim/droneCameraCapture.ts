import * as THREE from 'three'

const CAP_W = 720
const CAP_H = 480

let sharedRenderer: THREE.WebGLRenderer | null = null

function getCaptureRenderer(): THREE.WebGLRenderer {
  if (!sharedRenderer) {
    sharedRenderer = new THREE.WebGLRenderer({
      antialias: true,
      alpha: false,
      powerPreference: 'high-performance'
    })
    sharedRenderer.setPixelRatio(1)
    sharedRenderer.setSize(CAP_W, CAP_H, false)
    sharedRenderer.shadowMap.enabled = true
    sharedRenderer.shadowMap.type = THREE.PCFSoftShadowMap
    sharedRenderer.toneMapping = THREE.ACESFilmicToneMapping
    sharedRenderer.toneMappingExposure = 0.92
    sharedRenderer.outputColorSpace = THREE.SRGBColorSpace
    sharedRenderer.domElement.style.cssText = 'position:fixed;left:-9999px;top:0;width:1px;height:1px;opacity:0;pointer-events:none'
    document.body.appendChild(sharedRenderer.domElement)
  }
  return sharedRenderer
}

/** 释放离屏截图用 WebGLRenderer（页面卸载时调用） */
export function disposeInspectionCaptureRenderer(): void {
  if (sharedRenderer) {
    try {
      document.body.removeChild(sharedRenderer.domElement)
    } catch {
      /* 已从 DOM 移除 */
    }
    sharedRenderer.dispose()
    sharedRenderer = null
  }
}

/**
 * 从巡检相机挂点（+Z 为光轴）对当前场景做一次离屏渲染，返回 JPEG data URL。
 * 与主画布共用同一场景图，不干扰主 WebGLRenderer。
 */
export function captureSceneFromInspectionRig(
  scene: THREE.Scene,
  viewRig: THREE.Object3D,
  options?: { hideRoots?: THREE.Object3D[] }
): string | null {
  try {
    scene.updateMatrixWorld(true)

    const hidden: { o: THREE.Object3D; v: boolean }[] = []
    for (const o of options?.hideRoots ?? []) {
      hidden.push({ o, v: o.visible })
      o.visible = false
    }

    const eye = new THREE.Vector3()
    const dir = new THREE.Vector3()
    viewRig.getWorldPosition(eye)
    viewRig.getWorldDirection(dir)
    const target = eye.clone().addScaledVector(dir, 600)

    const capCam = new THREE.PerspectiveCamera(50, CAP_W / CAP_H, 0.6, 9000)
    capCam.position.copy(eye)
    capCam.up.set(0, 1, 0)
    capCam.lookAt(target)
    capCam.updateMatrixWorld(true)

    const r = getCaptureRenderer()
    r.setClearColor(0x87b8e8, 1)
    r.render(scene, capCam)
    const dataUrl = r.domElement.toDataURL('image/jpeg', 0.86)

    for (const { o, v } of hidden) {
      o.visible = v
    }
    return dataUrl
  } catch (e) {
    console.warn('[droneCameraCapture]', e)
    return null
  }
}
