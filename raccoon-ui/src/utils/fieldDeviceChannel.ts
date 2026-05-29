/** 与 raccoon-drone-sim 共用的现场设备跨页同步通道 */
export const FIELD_DEVICE_CHANNEL = 'raccoon-field-device'

export type FieldDeviceChannelMessage =
  | { type: 'UPSERT'; device: Record<string, unknown> }
  | { type: 'DELETE'; sceneObjectId: string; mapId?: number }
  | { type: 'RELOAD'; mapId?: number }

export function postFieldDeviceChannel(msg: FieldDeviceChannelMessage): void {
  try {
    new BroadcastChannel(FIELD_DEVICE_CHANNEL).postMessage(msg)
  } catch {
    /* ignore */
  }
}

/**
 * 订阅现场设备同步通道（仿真侧增删改移动会广播）。返回取消订阅函数。
 */
export function subscribeFieldDeviceChannel(
  handler: (msg: FieldDeviceChannelMessage) => void
): () => void {
  let ch: BroadcastChannel | null = null
  try {
    ch = new BroadcastChannel(FIELD_DEVICE_CHANNEL)
    ch.onmessage = (ev: MessageEvent<FieldDeviceChannelMessage>) => handler(ev.data)
  } catch {
    ch = null
  }
  return () => {
    ch?.close()
    ch = null
  }
}
