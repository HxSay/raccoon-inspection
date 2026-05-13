import * as THREE from 'three'

/**
 * 边缘终端算力 / 存储 / 网络指标仿真（与真实 SNMP 无关，仅用于 UI 展示波动）。
 */

export interface EdgeTerminalMetrics {
  cpuPercent: number
  storagePercent: number
  networkMbps: number
  networkLabel: string
}

export function createEdgeMetricsSimulator(getOnline: () => boolean) {
  let t = 0
  let cpu = 28
  let storage = 44
  let net = 12

  const tick = (dt: number): EdgeTerminalMetrics => {
    t += dt
    const online = getOnline()
    const wobble = Math.sin(t * 1.7) * 4 + Math.sin(t * 3.1) * 2
    cpu = THREE.MathUtils.clamp(cpu + wobble * dt * 2.5 + (Math.random() - 0.5) * 6 * dt, 12, 91)
    storage = THREE.MathUtils.clamp(storage + (Math.random() - 0.45) * 0.08, 38, 78)
    if (online) {
      net = THREE.MathUtils.clamp(net + (Math.random() - 0.5) * 4 * dt + Math.sin(t * 2) * 0.8, 6, 48)
    } else {
      net = THREE.MathUtils.damp(net, 0, 2, dt)
    }
    return {
      cpuPercent: Math.round(cpu * 10) / 10,
      storagePercent: Math.round(storage * 10) / 10,
      networkMbps: Math.round(net * 10) / 10,
      networkLabel: online ? '链路正常' : '离线（遥测缓存中）'
    }
  }

  return { tick }
}
