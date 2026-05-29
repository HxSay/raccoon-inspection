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
