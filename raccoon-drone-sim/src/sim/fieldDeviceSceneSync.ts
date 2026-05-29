import type { SceneEditor3D } from '@/editor/SceneEditor3D'
import { fetchFieldDevicesByMap, type FieldSceneDeviceRecord } from '@/api/fieldSceneDevice'

/** 管理平台 ↔ 仿真：跨页通知（现场设备增删改） */
export const FIELD_DEVICE_CHANNEL = 'raccoon-field-device'

export type FieldDeviceChannelMessage =
  | { type: 'UPSERT'; device: FieldSceneDeviceRecord }
  | { type: 'DELETE'; sceneObjectId: string; mapId?: number }
  | { type: 'RELOAD'; mapId?: number }

export function postFieldDeviceChannel(msg: FieldDeviceChannelMessage): void {
  try {
    new BroadcastChannel(FIELD_DEVICE_CHANNEL).postMessage(msg)
  } catch {
    /* 部分环境无 BroadcastChannel */
  }
}

/**
 * 将管理平台现场设备（含坐标）反映到 3D 编辑器物体层。
 * 内置杆塔（builtin-tower-*）已由场景几何生成，不再重复放置。
 */
export async function applyCloudFieldDevicesToEditor(
  editor: SceneEditor3D | null | undefined,
  tab: 'patrol' | 'substation' | 'thermal'
): Promise<number> {
  if (!editor) return 0
  const list = await fetchFieldDevicesByMap(tab)
  let n = 0
  for (const d of list) {
    if (d.status != null && d.status !== 1) continue
    if (editor.upsertFromCloud(d)) n++
  }
  return n
}

export function applySingleCloudDevice(
  editor: SceneEditor3D | null | undefined,
  device: FieldSceneDeviceRecord
): boolean {
  return editor?.upsertFromCloud(device) ?? false
}

export function removeCloudDeviceFromEditor(
  editor: SceneEditor3D | null | undefined,
  sceneObjectId: string
): void {
  editor?.removeByEditorId(sceneObjectId)
}
