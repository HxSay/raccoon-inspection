<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

defineProps<{
  collapse: boolean
}>()

const route = useRoute()
const router = useRouter()

interface MenuLeaf {
  path: string
  title: string
  icon?: string
}

interface MenuSubGroup {
  type: 'submenu'
  index: string
  title: string
  icon: string
  children: MenuLeaf[]
}

interface MenuSingle {
  type: 'item'
  path: string
  title: string
  icon: string
}

type MenuNode = MenuSingle | MenuSubGroup

/** 侧栏分组：与图中「系统管理 / 巡检管理 / 智能巡检」一致 */
const menuTree: MenuNode[] = [
  { type: 'item', path: '/dashboard', title: '首页', icon: 'Odometer' },
  { type: 'item', path: '/sim/drone', title: '仿真模拟', icon: 'VideoCamera' },
  {
    type: 'submenu',
    index: 'system',
    title: '系统管理',
    icon: 'Setting',
    children: [
      { path: '/user', title: '用户管理', icon: 'User' },
      { path: '/role', title: '角色管理', icon: 'UserFilled' },
      { path: '/dict/type', title: '字典类型管理', icon: 'Document' },
      { path: '/dict/data', title: '字典数据管理', icon: 'Collection' }
    ]
  },
  { type: 'item', path: '/cmms/device', title: '设备管理', icon: 'Monitor' },
  {
    type: 'submenu',
    index: 'inspection',
    title: '巡检管理',
    icon: 'DocumentChecked',
    children: [
      { path: '/cmms/inspection', title: '巡检计划与任务', icon: 'DocumentChecked' },
      { path: '/cmms/inspection/work-order-audit', title: '工单审核', icon: 'Checked' },
      { path: '/cmms/maintenance', title: '设备维护工单', icon: 'Tools' }
    ]
  },
  {
    type: 'submenu',
    index: 'smart-inspection',
    title: '智能巡检',
    icon: 'Cpu',
    children: [
      { path: '/drone/route-plan', title: '无人机路径规划', icon: 'Position' },
      { path: '/drone/field-device', title: '现场设备管理', icon: 'Place' },
      { path: '/drone/robot-manage', title: '巡检机器人管理', icon: 'Cpu' },
      { path: '/drone/inspection-data', title: '巡检数据', icon: 'DataLine' },
      { path: '/drone/close-loop', title: '任务闭环管理', icon: 'CircleCheck' },
      { path: '/drone/fault-grade', title: '故障分级处理', icon: 'Warning' }
    ]
  },
  {
    type: 'submenu',
    index: 'rag',
    title: '智能知识库',
    icon: 'Reading',
    children: [
      { path: '/rag/document', title: '巡检知识库管理', icon: 'Files' },
      { path: '/rag/chat', title: '巡检RAG问答', icon: 'ChatLineRound' },
      { path: '/rag/storage', title: '知识库存储查看', icon: 'DataAnalysis' }
    ]
  },
  {
    type: 'submenu',
    index: 'test-cases',
    title: '测试用例',
    icon: 'Memo',
    children: [
      { path: '/test/minio', title: 'MinIO 测试', icon: 'Upload' },
      { path: '/ai/ollama-test', title: 'Ollama 测试', icon: 'ChatDotRound' },
      { path: '/ai/neo4j-test', title: 'Neo4j 图库', icon: 'Share' },
      { path: '/ai/milvus/test', title: 'Milvus 测试', icon: 'Coin' }
    ]
  }
]

const mergeRouteMeta = (node: MenuLeaf): MenuLeaf => {
  const matched = router.getRoutes().find((r) => r.path === node.path)
  if (!matched?.meta?.title) return node
  return {
    path: node.path,
    title: (matched.meta.title as string) || node.title,
    icon: (matched.meta.icon as string) || node.icon
  }
}

const menuNodes = computed((): MenuNode[] =>
  menuTree.map((node) => {
    if (node.type === 'item') {
      const merged = mergeRouteMeta({ path: node.path, title: node.title, icon: node.icon })
      return { type: 'item', path: merged.path, title: merged.title, icon: merged.icon || node.icon }
    }
    return { ...node, children: node.children.map(mergeRouteMeta) }
  })
)

const activeMenu = computed(() => route.path)

const defaultOpeneds = computed(() => {
  const path = route.path
  const opened: string[] = []
  for (const node of menuTree) {
    if (node.type !== 'submenu') continue
    const hit = node.children.some((c) => path === c.path || path.startsWith(`${c.path}/`))
    if (hit) opened.push(node.index)
  }
  return opened
})
</script>

<template>
  <div class="sidebar">
    <div class="logo">
      <img src="@/assets/logo.svg" alt="logo" class="logo-img" />
      <span v-if="!collapse" class="logo-text">Raccoon</span>
    </div>
    <el-menu
      :key="activeMenu"
      :default-active="activeMenu"
      :default-openeds="defaultOpeneds"
      :collapse="collapse"
      :collapse-transition="false"
      background-color="#304156"
      text-color="#bfcbd9"
      active-text-color="#409EFF"
      router
    >
      <template v-for="node in menuNodes" :key="node.type === 'item' ? node.path : node.index">
        <el-menu-item v-if="node.type === 'item'" :index="node.path">
          <el-icon v-if="node.icon">
            <component :is="node.icon" />
          </el-icon>
          <template #title>{{ node.title }}</template>
        </el-menu-item>

        <el-sub-menu v-else :index="node.index">
          <template #title>
            <el-icon>
              <component :is="node.icon" />
            </el-icon>
            <span>{{ node.title }}</span>
          </template>
          <el-menu-item v-for="child in node.children" :key="child.path" :index="child.path">
            <el-icon v-if="child.icon">
              <component :is="child.icon" />
            </el-icon>
            <template #title>{{ child.title }}</template>
          </el-menu-item>
        </el-sub-menu>
      </template>
    </el-menu>
  </div>
</template>

<style scoped>
.sidebar {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #2b3a4a;
}

.logo-img {
  width: 32px;
  height: 32px;
}

.logo-text {
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  margin-left: 8px;
}

.el-menu {
  flex: 1;
  border-right: none;
}
</style>
