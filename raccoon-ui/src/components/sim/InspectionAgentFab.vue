<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import { dispatchTaskGenerate } from '@/api/droneDispatch'
import { nlpTaskParse, type NlpTaskParseResponse } from '@/api/droneNlp'
import { planningEndToEnd } from '@/api/planningE2e'
import {
  AGENT_ACTIVE_DISPATCH_TASK_KEY,
  AGENT_ACTIVE_WORK_ORDER_KEY,
  completeAgentSimulation
} from '@/api/agentPlanning'
import type { SimulationMissionReport } from '@/api/agentPlanningTypes'
import { faultIngest } from '@/api/faultHandle'
import { closeLoopIngest, type CapturedPoint, type CloseLoopResult } from '@/api/closeLoop'
import { fieldSceneDeviceByMap } from '@/api/fieldSceneDevice'
import type { UavRouteDispatchPayload } from '@/api/drone'

function countPhotoWaypoints(d: UavRouteDispatchPayload | null | undefined): number {
  return d?.photoWaypoints?.length ?? 0
}

/** 口语「所有/全部杆塔」：应覆盖仿真场景 5 基杆塔 */
function isInspectAllTowersIntent(text: string): boolean {
  const t = text.replace(/\s+/g, '')
  return /(?:所有|全部|全体)(?:的)?(?:杆塔|塔杆|塔)/.test(t)
    || t.includes('所有杆塔') || t.includes('全部杆塔')
    || t.includes('巡检所有') || t.includes('巡检全部')
}
import ChatBubble from '@/components/rag/ChatBubble.vue'
import {
  broadcastDispatchToSim,
  formatTaskSummary,
  inspectionTaskToDispatch,
  MSG_INSPECTION_ANOMALY,
  MSG_INSPECTION_DISPATCH_ACK,
  MSG_INSPECTION_MISSION_COMPLETE,
  MSG_INSPECTION_MISSION_ERROR,
  MSG_INSPECTION_STATUS,
  type InspectionAnomalyPayload,
  postDispatchToSimIframe
} from '@/utils/inspectionBridge'

const props = defineProps<{
  /** 仿真 iframe，用于 postMessage 下发航线 */
  simIframe?: HTMLIFrameElement | null
}>()

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: number
  loading?: boolean
  error?: string
}

const panelOpen = ref(false)
const inputText = ref('')
const sending = ref(false)
const messages = ref<ChatMessage[]>([])
const chatBodyRef = ref<HTMLElement | null>(null)
const missionRunning = ref(false)
/** 生成 CMMS 工单并提交移动端审核（不下发仿真） */
const submitAuditMode = ref(false)

/** 当前在飞任务的计划快照，任务完成后用于闭环完整性核对 */
interface ActiveTaskContext {
  taskId: string
  mapId?: number
  plannedDeviceIds: number[]
  plannedWaypointCount: number
  rescheduleCount: number
  parentTaskId?: string
}
let activeTaskContext: ActiveTaskContext | null = null
/** 已完成闭环的任务，避免重复触发 */
const closeLoopDoneTasks = new Set<string>()

function setActiveTaskContext(
  payload: UavRouteDispatchPayload | null | undefined,
  taskId: string,
  rescheduleCount = 0,
  parentTaskId?: string
) {
  const plannedDeviceIds = Array.from(
    new Set([
      ...(payload?.deviceVisitOrder ?? []),
      ...((payload?.photoWaypoints ?? []).flatMap((w) => w.deviceIds ?? []))
    ])
  )
  activeTaskContext = {
    taskId,
    mapId: payload?.mapId,
    plannedDeviceIds,
    plannedWaypointCount: payload?.photoWaypoints?.length ?? 0,
    rescheduleCount,
    parentTaskId
  }
}

const genId = () =>
  typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : `m-${Date.now()}-${Math.random().toString(16).slice(2)}`

const scrollToBottom = async () => {
  await nextTick()
  if (chatBodyRef.value) {
    chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
  }
}

const togglePanel = () => {
  panelOpen.value = !panelOpen.value
}

const clearChat = () => {
  messages.value = []
  missionRunning.value = false
  faultIngestedKeys.clear()
  faultDedupeUntil.clear()
  closeLoopDoneTasks.clear()
  activeTaskContext = null
}

function bindActiveWorkOrder(workOrderId?: number | null, dispatchTaskId?: string | null) {
  if (workOrderId != null) {
    sessionStorage.setItem(AGENT_ACTIVE_WORK_ORDER_KEY, String(workOrderId))
  }
  if (dispatchTaskId) {
    sessionStorage.setItem(AGENT_ACTIVE_DISPATCH_TASK_KEY, dispatchTaskId)
  }
}

function prepareExternalMission(
  payload: UavRouteDispatchPayload,
  options?: {
    workOrderId?: number
    dispatchTaskId?: string
  }
) {
  bindActiveWorkOrder(options?.workOrderId, options?.dispatchTaskId)
  const taskId =
    options?.dispatchTaskId ||
    (payload?.taskId != null ? String(payload.taskId) : '') ||
    `SIM-${Date.now()}`
  closeLoopDoneTasks.delete(taskId)
  setActiveTaskContext(payload, taskId, 0)
  missionRunning.value = true
}

let towerDeviceIdCache: Map<number, number> | null = null
/** 5 分钟内同设备/杆塔火情不重复上报与复巡（与后端去重一致） */
const FAULT_DEDUPE_MS = 5 * 60 * 1000
const faultDedupeUntil = new Map<string, number>()
/** 本架次已处理过的键，避免任务结束再次 ingest */
const faultIngestedKeys = new Set<string>()

function isFaultDeduped(key: string): boolean {
  const until = faultDedupeUntil.get(key)
  if (until != null && Date.now() < until) return true
  if (until != null) faultDedupeUntil.delete(key)
  return false
}

function markFaultDeduped(key: string) {
  faultDedupeUntil.set(key, Date.now() + FAULT_DEDUPE_MS)
}

function faultEventKey(ev: {
  faultType?: string
  towerIndex?: number
  photoId?: string
}): string {
  return `${ev.faultType ?? 'FIRE'}-${ev.towerIndex ?? ev.photoId ?? ''}`
}

function isFireRelatedLabel(label?: string): boolean {
  if (!label) return false
  const t = label.toUpperCase()
  return label.includes('火') || t.includes('FIRE') || label.includes('烟火') || label.includes('明火')
}

function dispatchReinspectRoute(
  routePayload: UavRouteDispatchPayload | undefined,
  dedupeKey: string,
  reinspectTaskId?: string,
  deviceCount?: number
): boolean {
  if (!routePayload?.photoWaypoints?.length && !routePayload?.waypoints?.length) {
    return false
  }
  const routeKey = `reinspect-route-${dedupeKey}`
  if (isFaultDeduped(routeKey)) return false
  const opts = {
    autoStart: true,
    userInput: `故障扩范围应急复巡（${deviceCount ?? '?'} 个设备）`,
    dispatchTaskId: reinspectTaskId
  }
  let posted = false
  if (props.simIframe?.contentWindow) {
    posted = postDispatchToSimIframe(props.simIframe, routePayload, opts)
  } else {
    broadcastDispatchToSim(routePayload, opts)
    posted = true
  }
  if (posted) markFaultDeduped(routeKey)
  return posted
}

async function resolveTowerDeviceId(towerIndex: number, mapId = 1): Promise<number | undefined> {
  try {
    if (!towerDeviceIdCache) {
      const res: any = await fieldSceneDeviceByMap(mapId)
      const list = res.data ?? []
      towerDeviceIdCache = new Map()
      for (const d of list) {
        const name = (d.deviceName || '').trim()
        const m = name.match(/杆塔\s*(\d+)/)
        if (m) towerDeviceIdCache.set(Number(m[1]), d.id)
      }
    }
    return towerDeviceIdCache.get(towerIndex)
  } catch {
    return towerIndex
  }
}

async function ingestFaultEvent(
  ev: InspectionAnomalyPayload & { photoId?: string },
  opts?: { urgent?: boolean; skipIfIngested?: boolean }
): Promise<boolean> {
  const key = faultEventKey(ev)
  if (isFaultDeduped(key)) return false
  if (opts?.skipIfIngested !== false && faultIngestedKeys.has(key)) return false
  markFaultDeduped(key)
  faultIngestedKeys.add(key)

  const mapId = 1
  const towerIndex = ev.towerIndex
  const deviceId =
    towerIndex != null ? await resolveTowerDeviceId(towerIndex, mapId) : undefined
  const label = ev.aiLabel
  const pct = ev.confidence != null ? `${(ev.confidence * 100).toFixed(1)}%` : '—'

  if (opts?.urgent) {
    ElNotification({
      title: '紧急：巡检发现火情',
      message: `杆塔${towerIndex ?? '?'} · ${label ?? '火焰/烟火'}（置信度 ${pct}），已通知调度并触发扩范围复巡。`,
      type: 'error',
      duration: 0,
      position: 'top-right'
    })
    pushAssistant(
      `【紧急】航点 #${ev.waypointIndex ?? '?'} 检出 ${label ?? '火情'}（${pct}），正在上报管理人员并插单复巡…`
    )
  }

  try {
    const fr: any = await faultIngest({
      deviceId,
      mapId,
      faultType: ev.faultType || 'FIRE',
      confidence: ev.confidence ?? 0.9,
      description: ev.description || `仿真巡检检出火情：${label ?? '火焰/烟火'}`
    })
    const body = fr?.data as {
      status?: string
      level?: string
      eventId?: string
      message?: string
      dispatch?: {
        success?: boolean
        reinspectTaskId?: string
        routePayload?: UavRouteDispatchPayload
        expandedDeviceCount?: number
      }
      plan?: { expandScope?: boolean; action?: string }
      expandedScope?: { relatedDeviceIds?: number[] }
    } | undefined
    if (body?.status === 'MERGED') {
      if (opts?.urgent) {
        pushAssistant(body?.message || '5分钟内同设备同类型火情已处理，不重复触发复巡。')
      }
      return false
    }
    const level = body?.level
    const ok = body?.dispatch?.success
    const devN =
      body?.dispatch?.expandedDeviceCount ??
      (body?.expandedScope?.relatedDeviceIds?.length ?? 0) + 1
    const expand = body?.plan?.expandScope ? `已扩范围（${devN} 个设备）` : ''
    const lines = [
      `故障分级：${level ?? '—'}（事件 ${body?.eventId?.slice(0, 8) ?? '—'}…）${expand ? `，${expand}` : ''}`,
      ok
        ? `已插单应急复巡 ${body?.dispatch?.reinspectTaskId ?? ''}（动作 ${body?.plan?.action ?? 'EMERGENCY_SWEEP'}）`
        : body?.message || '复巡插单未完全成功，可在「故障分级处理」页查看审计'
    ]
    if (ok && body?.dispatch?.routePayload) {
      const flew = dispatchReinspectRoute(
        body.dispatch.routePayload,
        key,
        body.dispatch.reinspectTaskId,
        body.dispatch.expandedDeviceCount
      )
      lines.push(
        flew
          ? '已向仿真无人机自动下发扩范围复巡航线并起飞。'
          : '航线已生成；请打开仿真页或刷新后，在故障分级页点击「下发仿真复巡」。'
      )
      if (flew) {
        missionRunning.value = true
        ElMessage.warning('检测到火情：已中断当前任务并启动扩范围应急复巡')
      }
    } else if (ok) {
      lines.push('（未返回仿真航线，请确认 drone 服务已重启）')
    }
    pushAssistant(lines.join('\n'), !ok)
    if (!ok) {
      faultDedupeUntil.delete(key)
      faultIngestedKeys.delete(key)
    }
    return true
  } catch (e: unknown) {
    faultDedupeUntil.delete(key)
    faultIngestedKeys.delete(key)
    const msg = e instanceof Error ? e.message : String(e)
    pushAssistant(`故障分级上报失败：${msg}（请确认 drone 服务 8091 已启动）`, true)
    if (opts?.urgent) {
      ElMessage.error(`火情上报失败：${msg}`)
    }
    return false
  }
}

async function triggerFaultGradingFromReport(missionReport?: SimulationMissionReport) {
  const events = missionReport?.anomalyEvents ?? []
  const photoWp = new Map(
    (missionReport?.photos ?? []).map((p) => [p.id, p.waypointIndex] as const)
  )
  const fromAi =
    missionReport?.aiResults
      ?.filter((a) => a.hasDefect && isFireRelatedLabel(a.label))
      .map((a) => {
        const wp = a.photoId ? photoWp.get(a.photoId) : undefined
        return {
          faultType: 'FIRE' as const,
          confidence: a.confidence,
          description: `AI 检出：${a.label}`,
          photoId: a.photoId,
          waypointIndex: wp,
          aiLabel: a.label
        }
      }) ?? []
  const merged: Array<InspectionAnomalyPayload & { photoId?: string }> = [...events, ...fromAi]
  if (!merged.length) return
  for (const ev of merged) {
    await ingestFaultEvent(ev, { urgent: false, skipIfIngested: true })
  }
}

async function handleInspectionAnomaly(anomaly: InspectionAnomalyPayload) {
  await ingestFaultEvent(anomaly, { urgent: true, skipIfIngested: true })
}

function closeStatusLabel(s?: string): string {
  switch (s) {
    case 'COMPLETED':
      return '已闭环'
    case 'PARTIAL':
      return '部分完成·已补检'
    case 'MANUAL_REQUIRED':
      return '需人工介入'
    case 'COLLECTING':
      return '结果回收中'
    default:
      return s ?? '—'
  }
}

/** 由仿真上报聚合实际完成的点位（按航点合并拍照与多模态采样） */
function buildCapturedPoints(report?: SimulationMissionReport): {
  captured: CapturedPoint[]
  finishedWaypoints: number
} {
  const byWp = new Map<number, CapturedPoint>()
  const aiByPhoto = new Map<string, { hasDefect?: boolean; label?: string }>()
  for (const a of report?.aiResults ?? []) {
    if (a.photoId) aiByPhoto.set(a.photoId, { hasDefect: a.hasDefect, label: a.label })
  }
  const ensure = (wp: number): CapturedPoint => {
    let c = byWp.get(wp)
    if (!c) {
      c = { waypointIndex: wp, dataTypes: [] }
      byWp.set(wp, c)
    }
    return c
  }
  for (const p of report?.photos ?? []) {
    const c = ensure(p.waypointIndex ?? 0)
    if (!c.dataTypes!.includes('VISIBLE')) c.dataTypes!.push('VISIBLE')
    if (p.id) {
      const ai = aiByPhoto.get(p.id)
      if (ai?.hasDefect) {
        c.hasDefect = true
        c.faultLabel = ai.label
      }
    }
  }
  for (const s of report?.multimodalSamples ?? []) {
    const c = ensure(s.waypointIndex ?? 0)
    const t = (s.modalityType ?? '').toUpperCase()
    if (t && !c.dataTypes!.includes(t)) c.dataTypes!.push(t)
  }
  return { captured: Array.from(byWp.values()), finishedWaypoints: byWp.size }
}

/** 任务完成 → 触发任务规划 Agent 闭环：完整性核对、报告生成、未完成项自动补检 */
async function triggerCloseLoop(report?: SimulationMissionReport) {
  const ctx =
    activeTaskContext ??
    (() => {
      const taskId =
        sessionStorage.getItem(AGENT_ACTIVE_DISPATCH_TASK_KEY) ||
        `SIM-${Date.now()}`
      const waypointIndexes = new Set<number>()
      for (const p of report?.photos ?? []) {
        if (p.waypointIndex != null) waypointIndexes.add(p.waypointIndex)
      }
      for (const s of report?.multimodalSamples ?? []) {
        if (s.waypointIndex != null) waypointIndexes.add(s.waypointIndex)
      }
      const plannedWaypointCount =
        waypointIndexes.size || report?.photoCount || report?.photos?.length || report?.multimodalSamples?.length || 0
      return {
        taskId,
        plannedDeviceIds: [],
        plannedWaypointCount,
        rescheduleCount: 0
      } satisfies ActiveTaskContext
    })()
  if (closeLoopDoneTasks.has(ctx.taskId)) return
  closeLoopDoneTasks.add(ctx.taskId)

  const { captured, finishedWaypoints } = buildCapturedPoints(report)
  const woRaw = sessionStorage.getItem(AGENT_ACTIVE_WORK_ORDER_KEY)
  const workOrderId = woRaw && Number.isFinite(Number(woRaw)) ? Number(woRaw) : undefined

  try {
    const res: any = await closeLoopIngest({
      taskId: ctx.taskId,
      mapId: ctx.mapId,
      workOrderId,
      plannedDeviceIds: ctx.plannedDeviceIds,
      plannedWaypointCount: ctx.plannedWaypointCount || finishedWaypoints,
      requiredDataTypes: ['VISIBLE'],
      finishedWaypointCount: finishedWaypoints,
      capturedPoints: captured,
      flightDistanceM: report?.distanceM,
      durationSec: report?.durationSec,
      telemetrySent: report?.telemetrySent,
      multimodalUploaded: report?.multimodalUploaded,
      rescheduleCount: ctx.rescheduleCount,
      parentTaskId: ctx.parentTaskId
    })
    const body = res?.data as CloseLoopResult | undefined
    if (!body) return
    const r = body.report
    const lines = [
      `【任务闭环】${closeStatusLabel(body.closeStatus)}，完成率 ${((body.completionRate ?? 0) * 100).toFixed(0)}%。`,
      r
        ? `计划航点 ${r.plannedWaypointCount} · 完成 ${r.finishedWaypointCount} · 漏检设备 ${r.missedDeviceCount} · 数据缺口 ${r.dataGapCount} · AI异常 ${r.defectCount}`
        : '',
      r?.executiveSummary ?? body.message ?? ''
    ].filter(Boolean)
    pushAssistant(lines.join('\n'), body.closeStatus === 'MANUAL_REQUIRED')

    if (body.rescheduled && body.reinspectRoutePayload) {
      const missed = r?.missedDeviceCount ?? body.verify?.missedDeviceIds?.length ?? 0
      const childTaskId = body.reinspectTaskId ?? `${ctx.taskId}-R${ctx.rescheduleCount + 1}`
      setActiveTaskContext(body.reinspectRoutePayload, childTaskId, ctx.rescheduleCount + 1, ctx.taskId)
      if (missionRunning.value) {
        // 已有应急复巡在飞（如火情），仅记录补检任务，避免航线冲突
        pushAssistant(`已生成补检任务 ${childTaskId}（${missed} 个未完成设备），将在当前任务结束后执行。`)
        return
      }
      const flew = dispatchReinspectRoute(
        body.reinspectRoutePayload,
        `closeloop-${ctx.taskId}`,
        body.reinspectTaskId,
        missed
      )
      if (flew) {
        missionRunning.value = true
        ElMessage.warning('存在漏检/数据缺口：已自动下发补检航线并起飞')
        pushAssistant('已向仿真无人机下发补检航线并起飞，补检完成后将再次核对闭环。')
      } else {
        pushAssistant('补检航线已生成；请打开仿真页或刷新后重新下发补检。')
      }
    }
  } catch (e: unknown) {
    closeLoopDoneTasks.delete(ctx.taskId)
    const msg = e instanceof Error ? e.message : String(e)
    pushAssistant(`任务闭环核对失败：${msg}（请确认 drone 服务已重启并已建表 task_close_loop_audit）`, true)
  }
}

async function syncWorkOrderAfterSimulation(missionReport?: SimulationMissionReport) {
  const woRaw = sessionStorage.getItem(AGENT_ACTIVE_WORK_ORDER_KEY)
  const dispatchTaskId = sessionStorage.getItem(AGENT_ACTIVE_DISPATCH_TASK_KEY) || undefined
  const workOrderId = woRaw ? Number(woRaw) : undefined
  if (!workOrderId && !dispatchTaskId) return
  try {
    await completeAgentSimulation({
      workOrderId: Number.isFinite(workOrderId) ? workOrderId : undefined,
      dispatchTaskId,
      remark: '仿真巡检完成自动回填',
      missionReport
    })
    pushAssistant('CMMS 巡检工单已更新为「已完成」，可在巡检管理 → 巡检工单中查看。')
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    pushAssistant(`工单状态回写失败：${msg}`, true)
  }
}

function onSimMessage(ev: MessageEvent) {
  const data = ev.data as { type?: string; status?: string; summary?: Record<string, unknown>; message?: string }
  if (!data?.type) return

  if (data.type === MSG_INSPECTION_DISPATCH_ACK) {
    missionRunning.value = true
    const n = (data as { waypointCount?: number }).waypointCount
    pushAssistant(`航线已加载（${n ?? '?'} 个航点），无人机正在起飞巡检…`)
    return
  }

  if (data.type === MSG_INSPECTION_ANOMALY) {
    const anomaly = (data as { anomaly?: InspectionAnomalyPayload }).anomaly
    if (anomaly) void handleInspectionAnomaly(anomaly)
    return
  }

  if (data.type === MSG_INSPECTION_STATUS && data.status) {
    if (
      data.status.includes('自主巡检') ||
      data.status.includes('拍照') ||
      data.status.includes('返航') ||
      data.status.includes('上报') ||
      data.status.includes('火情') ||
      data.status.includes('【紧急】')
    ) {
      pushAssistant(`[状态] ${data.status}`, false)
    }
    return
  }

  if (data.type === MSG_INSPECTION_MISSION_COMPLETE && data.summary) {
    missionRunning.value = false
    faultIngestedKeys.clear()
    const payload = data as { summary?: Record<string, unknown>; missionReport?: SimulationMissionReport }
    const s = payload.summary as {
      durationSec?: number
      distanceM?: number
      photoCount?: number
      telemetrySent?: number
      multimodalUploaded?: boolean
      multimodalError?: string
    }
    const uploadLine = s.multimodalUploaded
      ? '多模态巡检结果已上报云端。'
      : s.multimodalError
        ? `多模态上报：${s.multimodalError}`
        : '多模态上报未执行。'
    const mmCount = payload.missionReport?.multimodalSamples?.length ?? 0
    pushAssistant(
      [
        '巡检任务已完成。',
        `飞行 ${s.durationSec?.toFixed(0) ?? '—'} s，航程 ${s.distanceM?.toFixed(1) ?? '—'} m。`,
        `拍照 ${s.photoCount ?? 0} 次，多模态采样 ${mmCount} 条，遥测 ${s.telemetrySent ?? 0} 条。`,
        uploadLine
      ].join('\n')
    )
    void syncWorkOrderAfterSimulation(payload.missionReport)
    void triggerCloseLoop(payload.missionReport)
    void triggerFaultGradingFromReport(payload.missionReport)
    return
  }

  if (data.type === MSG_INSPECTION_MISSION_ERROR) {
    missionRunning.value = false
    pushAssistant(`任务异常：${data.message ?? '未知错误'}`, true)
  }
}

function pushAssistant(content: string, isError = false) {
  messages.value.push({
    id: genId(),
    role: 'assistant',
    content,
    timestamp: Date.now(),
    error: isError ? content : undefined
  })
  void scrollToBottom()
}

const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text) {
    ElMessage.warning('请输入巡检指令')
    return
  }
  if (sending.value) return
  if (missionRunning.value) {
    ElMessage.warning('无人机巡检进行中，请等待任务结束')
    return
  }

  messages.value.push({
    id: genId(),
    role: 'user',
    content: text,
    timestamp: Date.now()
  })
  inputText.value = ''

  const assistantMsg: ChatMessage = {
    id: genId(),
    role: 'assistant',
    content: '',
    timestamp: Date.now(),
    loading: true
  }
  messages.value.push(assistantMsg)
  await scrollToBottom()

  sending.value = true
  try {
    if (submitAuditMode.value) {
      const e2eRes: any = await planningEndToEnd({
        userInput: text,
        enableSimulation: true
      })
      const e2e = e2eRes.data
      if (e2e?.needFollowUp) {
        assistantMsg.content = e2e.followUpQuestion ?? '请补充巡检区域与设备信息。'
        return
      }
      if (!e2e?.success) {
        assistantMsg.content = e2e?.message ?? '端到端规划失败'
        assistantMsg.error = assistantMsg.content
        return
      }
      bindActiveWorkOrder(e2e.workOrderId, e2e.dispatchTaskId)
      assistantMsg.content = [
        '已生成待审核巡检工单：',
        `工单号 ${e2e.orderNo ?? '—'}`,
        `调度任务 ${e2e.dispatchTaskId ?? '—'}`,
        `分配终端 ${e2e.assignedTerminalName ?? e2e.assignedTerminalId ?? '—'}`,
        e2e.assignReason ? `分配理由：${e2e.assignReason}` : '',
        e2e.auditDeadline ? `审核截止：${e2e.auditDeadline}` : '',
        '请管理员在「巡检管理 → 工单审核」页面通过或驳回。'
      ]
        .filter(Boolean)
        .join('\n')
      ElMessage.success('工单已提交审核')
      return
    }

    let dispatchPayload: UavRouteDispatchPayload | null = null
    let summaryLines: string[] = []
    let parseSource = ''
    const simQuickPath = !!props.simIframe

    // 仿真 Agent：跳过调度中枢（含 LLM+拍卖，易超过 30s），直接规则 NLP 快速下发
    if (!simQuickPath) {
      try {
        const hubRes: any = await dispatchTaskGenerate({
          userInput: text,
          enableSimulation: true,
          autoDispatch: true
        })
        const hub = hubRes.data
        if (hub?.assigned && hub?.workOrder?.payload) {
          dispatchPayload = hub.workOrder.payload
          parseSource = '调度中枢'
          summaryLines = [
            `任务：${hub.taskId ?? '—'}`,
            `分派终端：${hub.assignedTerminalName ?? hub.assignedTerminalId ?? '—'}`,
            hub.bidPrice != null ? `竞拍价：${hub.bidPrice.toFixed(3)}` : '',
            hub.priority ? `优先级：${hub.priority}` : '',
            hub.pathPlan?.distanceM != null
              ? `航程：${hub.pathPlan.distanceM.toFixed(1)} m / ${hub.pathPlan.durationSec ?? '—'} s`
              : ''
          ].filter(Boolean) as string[]
        } else if (hub?.message && hub.message !== 'OK') {
          summaryLines.push(`调度提示：${hub.message}`)
        }
      } catch (hubErr: unknown) {
        const msg = hubErr instanceof Error ? hubErr.message : String(hubErr)
        summaryLines.push(`调度中枢暂不可用（${msg}），将使用 NLP 解析…`)
      }
    }

    const allTowers = isInspectAllTowersIntent(text)
    const hubPhotos = countPhotoWaypoints(dispatchPayload)
    const needNlp =
      simQuickPath ||
      !dispatchPayload ||
      hubPhotos < 1 ||
      allTowers

    let data: NlpTaskParseResponse | undefined
    if (needNlp) {
      const res: any = await nlpTaskParse(text, {
        ruleOnly: simQuickPath,
        timeoutMs: simQuickPath ? 15000 : 65000
      })
      data = res.data as NlpTaskParseResponse
    }

    if (data?.needFollowUp) {
      assistantMsg.content = data.followUpQuestion ?? '请补充巡检区域与设备信息。'
      return
    }

    if (!data?.task && !dispatchPayload) {
      assistantMsg.content = '解析失败：未生成巡检任务，请换种说法重试。'
      assistantMsg.error = assistantMsg.content
      return
    }

    const nlpDispatch = data?.task ? inspectionTaskToDispatch(data.task) : null

    const nlpPhotos = countPhotoWaypoints(nlpDispatch)
    const preferNlp =
      nlpDispatch &&
      (!dispatchPayload ||
        nlpPhotos > hubPhotos ||
        (allTowers && nlpPhotos >= 5 && hubPhotos < 5))

    if (preferNlp && nlpDispatch) {
      if (dispatchPayload && nlpPhotos > hubPhotos) {
        summaryLines.push(`（调度路径较短，已改用 NLP 全量杆塔航线，${nlpPhotos} 个拍照点）`)
      } else if (allTowers && hubPhotos < 5) {
        summaryLines.push(`（全量杆塔巡检，已采用 ${nlpPhotos} 个拍照点航线）`)
      }
      dispatchPayload = nlpDispatch
    } else if (!dispatchPayload && nlpDispatch) {
      dispatchPayload = nlpDispatch
      const summary = formatTaskSummary(data.task!, data.slots)
      parseSource = data.parseSource === 'RULE' ? '规则解析' : 'LLM 解析'
      summaryLines = [`已理解巡检意图（${parseSource}）：`, summary]
    }

    // 机队决策：架数与理由由后端（LLM 决策 + 真实可用机数裁剪）给出
    const recommendedFleet = data?.slots?.recommendedDrones
    const fleetReason = data?.slots?.fleetReason
    if (recommendedFleet && recommendedFleet >= 1) {
      summaryLines.push(`AI 决策出动 ${recommendedFleet} 架无人机`)
      if (fleetReason) summaryLines.push(`理由：${fleetReason}`)
    }

    const photoN = countPhotoWaypoints(dispatchPayload)
    assistantMsg.content =
      (summaryLines.length ? summaryLines.join('\n') : '任务已生成') +
      `\n共 ${photoN} 个拍照点，将按编队分派至各无人机。` +
      '\n\n正在向仿真无人机下发航线…'

    if (!props.simIframe) {
      assistantMsg.content += '\n\n（仿真 iframe 未就绪，无法启动飞行）'
      ElMessage.warning('仿真页面未加载完成')
      return
    }

    const posted = postDispatchToSimIframe(props.simIframe, dispatchPayload!, {
      autoStart: true,
      userInput: text,
      recommendedFleet: recommendedFleet && recommendedFleet >= 1 ? recommendedFleet : undefined,
      workOrderId: sessionStorage.getItem(AGENT_ACTIVE_WORK_ORDER_KEY)
        ? Number(sessionStorage.getItem(AGENT_ACTIVE_WORK_ORDER_KEY))
        : undefined,
      dispatchTaskId: sessionStorage.getItem(AGENT_ACTIVE_DISPATCH_TASK_KEY) || undefined
    })
    if (!posted) {
      assistantMsg.content += '\n\n下发失败：无法访问仿真窗口。'
      ElMessage.error('无法向仿真页下发指令')
    } else {
      const tid =
        sessionStorage.getItem(AGENT_ACTIVE_DISPATCH_TASK_KEY) ||
        (dispatchPayload?.taskId != null ? String(dispatchPayload.taskId) : '') ||
        `SIM-${Date.now()}`
      closeLoopDoneTasks.delete(tid)
      setActiveTaskContext(dispatchPayload, tid, 0)
    }
  } catch (e: any) {
    assistantMsg.error = e?.message ?? '任务解析失败'
    assistantMsg.content = `解析失败：${assistantMsg.error}`
    ElMessage.error(assistantMsg.error)
  } finally {
    assistantMsg.loading = false
    sending.value = false
    await scrollToBottom()
  }
}

const onKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

onMounted(() => {
  window.addEventListener('message', onSimMessage)
})

onBeforeUnmount(() => {
  window.removeEventListener('message', onSimMessage)
})

watch(panelOpen, (open) => {
  if (open) scrollToBottom()
})

defineExpose({
  prepareExternalMission
})
</script>

<template>
  <div class="agent-fab-root">
    <transition name="agent-panel">
      <div v-show="panelOpen" class="agent-panel">
        <div class="agent-panel__header">
          <div class="agent-panel__title">
            <el-icon class="agent-panel__icon"><MagicStick /></el-icon>
            <span>智能巡检 Agent</span>
          </div>
          <div class="agent-panel__actions">
            <el-button link type="info" @click="clearChat">清空</el-button>
            <el-button link type="info" @click="panelOpen = false">
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
        </div>

        <div class="agent-panel__hint">
          用自然语言下达巡检任务，例如：
          <em>明天上午对输电线路场景的杆塔1和杆塔2做例行巡检</em>
        </div>

        <div ref="chatBodyRef" class="agent-panel__body">
          <div v-if="!messages.length" class="agent-panel__empty">
            <p>我是巡检任务 Agent，可：</p>
            <ul>
              <li>解析自然语言中的区域、设备、优先级</li>
              <li>自动生成航线并指挥仿真无人机起飞</li>
              <li>任务结束后上报遥测与多模态巡检结果</li>
            </ul>
          </div>
          <template v-else>
            <ChatBubble
              v-for="m in messages"
              :key="m.id"
              :role="m.role"
              :content="m.content"
              :timestamp="m.timestamp"
              :loading="m.loading"
            />
          </template>
        </div>

        <div class="agent-panel__footer">
          <el-checkbox v-model="submitAuditMode" :disabled="sending || missionRunning" class="audit-mode">
            生成工单并提交审核（正式下发需审核通过）
          </el-checkbox>
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="2"
            placeholder="输入巡检指令，如：紧急复巡变电站场景的主变压器和断路器"
            :disabled="sending || missionRunning"
            resize="none"
            @keydown="onKeydown"
          />
          <el-button
            type="primary"
            class="agent-panel__send"
            :loading="sending"
            :disabled="missionRunning"
            @click="sendMessage"
          >
            {{ missionRunning ? '巡检中…' : '下达指令' }}
          </el-button>
        </div>
      </div>
    </transition>

    <button
      type="button"
      class="agent-fab"
      :class="{ 'agent-fab--active': panelOpen }"
      title="智能巡检 Agent"
      @click="togglePanel"
    >
      <el-icon :size="26"><ChatDotRound /></el-icon>
      <span v-if="!panelOpen" class="agent-fab__label">AI 助手</span>
    </button>
  </div>
</template>

<style scoped>
.agent-fab-root {
  position: absolute;
  right: 20px;
  bottom: 24px;
  z-index: 30;
  pointer-events: none;
}

.agent-fab-root > * {
  pointer-events: auto;
}

.agent-fab {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 18px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, #409eff 0%, #337ecc 100%);
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  box-shadow: 0 6px 20px rgba(64, 158, 255, 0.45);
  transition: transform 0.2s, box-shadow 0.2s;
}

.agent-fab:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(64, 158, 255, 0.55);
}

.agent-fab--active {
  background: linear-gradient(135deg, #606266 0%, #303133 100%);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.35);
}

.agent-fab__label {
  white-space: nowrap;
}

.agent-panel {
  position: absolute;
  right: 0;
  bottom: 64px;
  width: 400px;
  max-width: calc(100vw - 40px);
  height: 520px;
  max-height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
  border-radius: 12px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.28);
  border: 1px solid #e4e7ed;
}

.agent-panel__header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  background: linear-gradient(135deg, #304156 0%, #1f2d3d 100%);
  color: #fff;
}

.agent-panel__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
}

.agent-panel__icon {
  font-size: 18px;
  color: #79bbff;
}

.agent-panel__actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.agent-panel__actions .el-button {
  color: #c0c4cc;
}

.agent-panel__actions .el-button:hover {
  color: #fff;
}

.agent-panel__hint {
  flex-shrink: 0;
  padding: 8px 12px;
  font-size: 12px;
  color: #606266;
  line-height: 1.5;
  background: #ecf5ff;
  border-bottom: 1px solid #d9ecff;
}

.agent-panel__hint em {
  font-style: normal;
  color: #409eff;
}

.agent-panel__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px;
  background: #f5f7fa;
}

.agent-panel__empty {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}

.agent-panel__empty p {
  margin: 0 0 8px;
  font-weight: 500;
}

.agent-panel__empty ul {
  margin: 0;
  padding-left: 18px;
}

.audit-mode {
  margin-bottom: 8px;
  width: 100%;
}

.agent-panel__footer {
  flex-shrink: 0;
  padding: 10px 12px;
  border-top: 1px solid #ebeef5;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.agent-panel__send {
  align-self: flex-end;
}

.agent-panel-enter-active,
.agent-panel-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.agent-panel-enter-from,
.agent-panel-leave-to {
  opacity: 0;
  transform: translateY(12px) scale(0.96);
}
</style>
