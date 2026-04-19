<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

// 概览数据卡片数据
const overviewStats = ref([
  {
    title: '今日巡检任务',
    total: 24,
    completed: 18,
    abnormal: 3,
    icon: 'List',
    gradient: 'linear-gradient(135deg, #6384e8, #7a5cb4)'
  },
  {
    title: '异常识别率',
    rate: '96.8%',
    trend: '+1.2%',
    icon: 'CheckCircle',
    gradient: 'linear-gradient(135deg, #6384e8, #7a5cb4)'
  },
  {
    title: '在线机器人数量',
    online: 8,
    offline: 2,
    icon: 'Monitor',
    gradient: 'linear-gradient(135deg, #6384e8, #7a5cb4)'
  },
  {
    title: '待闭环工单',
    urgent: 2,
    normal: 5,
    icon: 'Ticket',
    gradient: 'linear-gradient(135deg, #6384e8, #7a5cb4)'
  }
])

// 核心架构数据
const architectureLayers = ref([
  {
    title: '云端核心层',
    processes: ['LLM任务理解', '结构化任务链', '异常复核', '知识图谱匹配'],
    description: '利用大语言模型理解自然语言指令，生成结构化巡检任务链，通过异常复核引擎和知识图谱匹配提高识别准确性。'
  },
  {
    title: '机器人边缘端',
    processes: ['SLAM导航', '多模态采集', 'YOLO识别', '断网续传'],
    description: '通过SLAM导航实现自主移动，多模态传感器采集数据，YOLO算法实时识别异常，支持断网续传确保数据不丢失。'
  },
  {
    title: '云端闭环层',
    processes: ['异常判定', '自动工单', '消息推送', '数据回流迭代'],
    description: '对边缘端上传的异常数据进行判定，自动生成工单并推送通知，跟踪处理结果，数据回流持续优化模型。'
  }
])

// 核心功能快捷入口数据
const coreFeatures = ref([
  {
    title: '自然语言任务调度',
    description: '一句话下发巡检任务，自动解析任务需求，生成结构化巡检任务链',
    icon: 'Message',
    gradient: 'linear-gradient(135deg, #6384e8, #7a5cb4)'
  },
  {
    title: '多模态智能识别',
    description: '融合视觉、声学、振动等多模态数据，实现设备异常的精准识别',
    icon: 'Camera',
    gradient: 'linear-gradient(135deg, #6384e8, #7a5cb4)'
  },
  {
    title: '断网续传监控',
    description: '在网络不稳定环境下，支持本地缓存数据，网络恢复后自动上传',
    icon: 'Wifi',
    gradient: 'linear-gradient(135deg, #6384e8, #7a5cb4)'
  },
  {
    title: '异常闭环管理',
    description: '自动生成异常工单，智能派工调度，跟踪处理结果，形成完整的异常处理闭环',
    icon: 'Ticket',
    gradient: 'linear-gradient(135deg, #6384e8, #7a5cb4)'
  }
])

// 技术优势数据
const technicalAdvantages = ref([
  {
    title: '低误报率',
    description: '基于深度学习和知识图谱的异常识别算法，大幅降低误报率，提高巡检准确性',
    icon: 'Check'
  },
  {
    title: '7×24小时无人值守',
    description: '机器人可全天候作业，不受时间和环境限制，大幅提高巡检覆盖率和效率',
    icon: 'Clock'
  },
  {
    title: '复杂工业环境适配',
    description: '具备抗干扰能力，适应高温、高湿、粉尘等复杂工业环境，确保稳定运行',
    icon: 'Industry'
  },
  {
    title: '模型持续迭代',
    description: '基于巡检数据持续优化模型，不断提高识别精度和适应性，保持技术领先优势',
    icon: 'Refresh'
  }
])

// 监听滚动事件，用于导航栏样式变化
const isScrolled = ref(false)
onMounted(() => {
  window.addEventListener('scroll', () => {
    isScrolled.value = window.scrollY > 50
  })
})
</script>

<template>
  <div class="dashboard">
    <!-- 概览数据卡片 -->
    <section class="overview-section">
      <h2 class="section-title">概览</h2>
      <div class="overview-container">
        <div v-for="(stat, index) in overviewStats" :key="index" class="overview-card">
          <div class="card-header" :style="{ background: stat.gradient }">
            <h3 class="card-title">{{ stat.title }}</h3>
            <el-icon :size="24" style="color: #FFFFFF">
              <component :is="stat.icon" />
            </el-icon>
          </div>
          <div class="card-content">
            <div v-if="stat.total" class="stat-details">
              <p class="stat-item"><span class="stat-label">总数：</span><span class="stat-value">{{ stat.total }}</span></p>
              <p class="stat-item"><span class="stat-label">完成：</span><span class="stat-value">{{ stat.completed }}</span></p>
              <p class="stat-item"><span class="stat-label">异常：</span><span class="stat-value">{{ stat.abnormal }}</span></p>
            </div>
            <div v-else-if="stat.rate" class="stat-details">
              <p class="stat-rate">{{ stat.rate }}</p>
              <p class="stat-trend" style="color: #67C23A">↑ {{ stat.trend }}</p>
            </div>
            <div v-else-if="stat.online" class="stat-details">
              <p class="stat-item"><span class="stat-label">在线：</span><span class="stat-value" style="color: #67C23A">{{ stat.online }}</span></p>
              <p class="stat-item"><span class="stat-label">离线：</span><span class="stat-value" style="color: #F56C6C">{{ stat.offline }}</span></p>
            </div>
            <div v-else-if="stat.urgent" class="stat-details">
              <p class="stat-item"><span class="stat-label">紧急：</span><span class="stat-value" style="color: #F56C6C">{{ stat.urgent }}</span></p>
              <p class="stat-item"><span class="stat-label">普通：</span><span class="stat-value">{{ stat.normal }}</span></p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 核心架构可视化 -->
    <section class="architecture-section">
      <h2 class="section-title">三层端云协同闭环架构</h2>
      <div class="architecture-container">
        <div v-for="(layer, index) in architectureLayers" :key="index" class="architecture-card">
          <h3 class="card-title">{{ layer.title }}</h3>
          <div class="card-processes">
            <div v-for="(process, processIndex) in layer.processes" :key="processIndex" class="process-item">
              <span class="process-arrow" v-if="processIndex > 0">→</span>
              <span class="process-name">{{ process }}</span>
            </div>
          </div>
          <div class="card-description">{{ layer.description }}</div>
        </div>
        <div class="architecture-arrow arrow-1">→</div>
        <div class="architecture-arrow arrow-2">→</div>
        <div class="architecture-arrow arrow-3">↩</div>
      </div>
    </section>

    <!-- 核心功能快捷入口 -->
    <section class="features-section">
      <h2 class="section-title">核心功能</h2>
      <div class="features-container">
        <div v-for="(feature, index) in coreFeatures" :key="index" class="feature-card">
          <div class="feature-icon" :style="{ background: feature.gradient }">
            <el-icon :size="24" style="color: #FFFFFF">
              <component :is="feature.icon" />
            </el-icon>
          </div>
          <h3 class="feature-title">{{ feature.title }}</h3>
          <p class="feature-description">{{ feature.description }}</p>
        </div>
      </div>
    </section>

    <!-- 技术优势 -->
    <section class="advantages-section">
      <h2 class="section-title">技术优势</h2>
      <div class="advantages-container">
        <div v-for="(advantage, index) in technicalAdvantages" :key="index" class="advantage-item">
          <div class="advantage-icon">
            <el-icon :size="20" style="color: #40a9ff">
              <component :is="advantage.icon" />
            </el-icon>
          </div>
          <div class="advantage-content">
            <h3 class="advantage-title">{{ advantage.title }}</h3>
            <p class="advantage-description">{{ advantage.description }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- 页脚 -->
    <footer class="dashboard-footer">
      <div class="footer-content">
        <div class="footer-section">
          <h3 class="footer-title">项目简介</h3>
          <p class="footer-description">浣熊智能巡检Agent是一款面向工业场景的AI巡检机器人系统，实现从自然语言任务下发到异常工单闭环的全流程自动化巡检，核心为三层端云协同闭环架构。</p>
        </div>
        <div class="footer-section">
          <h3 class="footer-title">技术栈</h3>
          <ul class="footer-tech-stack">
            <li>LLM</li>
            <li>YOLO</li>
            <li>SLAM</li>
            <li>边缘计算</li>
            <li>Vue 3</li>
            <li>TypeScript</li>
            <li>Element Plus</li>
          </ul>
        </div>
        <div class="footer-section">
          <h3 class="footer-title">联系方式</h3>
          <ul class="footer-contact">
            <li><i class="icon-phone"></i> 400-123-4567</li>
            <li><i class="icon-email"></i> contact@raccoon-inspection.com</li>
            <li><i class="icon-map"></i> 北京市海淀区中关村科技园区</li>
          </ul>
        </div>
      </div>
      <div class="footer-bottom">
        <p class="footer-copyright">© 2026 Raccoon Inspection. All rights reserved.</p>
      </div>
    </footer>
  </div>
</template>

<style scoped>
/* 全局样式 */
.dashboard {
  padding: 20px;
  min-height: 100vh;
  background-color: #F2F3F5;
}

.section-title {
  font-size: 24px;
  font-weight: 600;
  color: #1D2129;
  margin-bottom: 30px;
  padding-left: 10px;
  border-left: 4px solid #6384e8;
}

/* 概览数据卡片样式 */
.overview-section {
  margin-bottom: 40px;
}

.overview-container {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 20px;
}

.overview-card {
  background-color: #FFFFFF;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  transition: all 0.3s ease;
}

.overview-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 5px 20px rgba(0, 0, 0, 0.15);
}

.card-header {
  padding: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #FFFFFF;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
}

.card-content {
  padding: 20px;
}

.stat-details {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.stat-item {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
  margin: 0;
}

.stat-label {
  color: #4E5969;
}

.stat-value {
  font-weight: 600;
  color: #1D2129;
}

.stat-rate {
  font-size: 32px;
  font-weight: 700;
  color: #1D2129;
  margin: 0 0 10px 0;
}

.stat-trend {
  font-size: 14px;
  margin: 0;
}

/* 核心架构可视化样式 */
.architecture-section {
  margin-bottom: 40px;
}

.architecture-container {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 20px;
  padding: 20px;
  background-color: #FFFFFF;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

.architecture-card {
  flex: 1;
  min-width: 300px;
  padding: 30px;
  background-color: #FFFFFF;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
  position: relative;
}

.architecture-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 5px 20px rgba(0, 0, 0, 0.15);
  z-index: 10;
}

.architecture-card .card-title {
  font-size: 18px;
  font-weight: 600;
  color: #1D2129;
  margin-bottom: 20px;
}

.card-processes {
  display: flex;
  flex-direction: column;
  gap: 15px;
  margin-bottom: 20px;
}

.process-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: #4E5969;
}

.process-arrow {
  color: #40a9ff;
  font-weight: bold;
}

.card-description {
  font-size: 14px;
  color: #4E5969;
  line-height: 1.5;
}

.architecture-arrow {
  font-size: 32px;
  color: #6384e8;
  font-weight: bold;
  margin: 0 20px;
  transition: all 0.3s ease;
  animation: arrow-flow 2s infinite;
}

.architecture-card:hover + .architecture-arrow {
  color: #40a9ff;
  transform: scale(1.2);
}

.arrow-3 {
  position: absolute;
  bottom: 20px;
  right: 20px;
  transform: rotate(90deg);
}

@keyframes arrow-flow {
  0% {
    opacity: 0.5;
    transform: translateX(0);
  }
  50% {
    opacity: 1;
    transform: translateX(10px);
  }
  100% {
    opacity: 0.5;
    transform: translateX(0);
  }
}

/* 核心功能快捷入口样式 */
.features-section {
  margin-bottom: 40px;
}

.features-container {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 30px;
}

.feature-card {
  background-color: #FFFFFF;
  border-radius: 8px;
  padding: 30px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
  text-align: center;
  border: 1px solid #e8e8e8;
}

.feature-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 5px 20px rgba(0, 0, 0, 0.15);
  border-color: #6384e8;
}

.feature-icon {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px;
  transition: all 0.3s ease;
}

.feature-card:hover .feature-icon {
  transform: scale(1.1);
}

.feature-title {
  font-size: 18px;
  font-weight: 600;
  color: #1D2129;
  margin-bottom: 15px;
}

.feature-description {
  font-size: 14px;
  color: #4E5969;
  line-height: 1.5;
}

/* 技术优势样式 */
.advantages-section {
  margin-bottom: 40px;
}

.advantages-container {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 30px;
}

.advantage-item {
  display: flex;
  gap: 20px;
  background-color: #FFFFFF;
  border-radius: 8px;
  padding: 30px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
}

.advantage-item:hover {
  transform: translateY(-5px);
  box-shadow: 0 5px 20px rgba(0, 0, 0, 0.15);
}

.advantage-icon {
  width: 40px;
  height: 40px;
  background-color: rgba(64, 169, 255, 0.1);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.3s ease;
}

.advantage-item:hover .advantage-icon {
  background-color: rgba(99, 132, 232, 0.2);
  transform: scale(1.1);
}

.advantage-content {
  flex: 1;
}

.advantage-title {
  font-size: 16px;
  font-weight: 600;
  color: #1D2129;
  margin-bottom: 10px;
}

.advantage-description {
  font-size: 14px;
  color: #4E5969;
  line-height: 1.5;
}

/* 页脚样式 */
.dashboard-footer {
  background-color: #1D2129;
  color: #FFFFFF;
  padding: 60px 0 30px;
  border-radius: 8px;
  margin-top: 60px;
}

.footer-content {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 40px;
  margin-bottom: 40px;
  padding: 0 20px;
}

.footer-section .footer-title {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 20px;
  color: #FFFFFF;
}

.footer-section .footer-description {
  font-size: 14px;
  line-height: 1.5;
  color: #999;
}

.footer-tech-stack {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.footer-tech-stack li {
  background-color: rgba(255, 255, 255, 0.1);
  padding: 5px 10px;
  border-radius: 4px;
  font-size: 14px;
  color: #999;
  transition: all 0.3s ease;
}

.footer-tech-stack li:hover {
  background-color: rgba(99, 132, 232, 0.3);
  color: #FFFFFF;
}

.footer-contact {
  list-style: none;
  padding: 0;
  margin: 0;
}

.footer-contact li {
  margin-bottom: 10px;
  font-size: 14px;
  color: #999;
  display: flex;
  align-items: center;
  gap: 10px;
  transition: all 0.3s ease;
}

.footer-contact li:hover {
  color: #6384e8;
}

.footer-bottom {
  border-top: 1px solid #333;
  padding-top: 30px;
  text-align: center;
  font-size: 14px;
  color: #999;
}

/* 响应式布局 */
@media (max-width: 768px) {
  .dashboard {
    padding: 10px;
  }

  .section-title {
    font-size: 20px;
  }

  .overview-container,
  .features-container,
  .advantages-container {
    grid-template-columns: 1fr;
  }

  .architecture-container {
    flex-direction: column;
  }

  .architecture-arrow {
    transform: rotate(90deg);
    margin: 20px 0;
  }

  .arrow-3 {
    position: relative;
    bottom: 0;
    right: 0;
    transform: rotate(0);
  }

  .footer-content {
    grid-template-columns: 1fr;
    gap: 30px;
  }
}

/* 滚动渐入动画 */
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.overview-card,
.architecture-card,
.feature-card,
.advantage-item,
.dashboard-footer {
  animation: fadeIn 0.8s ease forwards;
}

.overview-card:nth-child(1) {
  animation-delay: 0.1s;
}

.overview-card:nth-child(2) {
  animation-delay: 0.2s;
}

.overview-card:nth-child(3) {
  animation-delay: 0.3s;
}

.overview-card:nth-child(4) {
  animation-delay: 0.4s;
}

.architecture-card:nth-child(1) {
  animation-delay: 0.1s;
}

.architecture-card:nth-child(3) {
  animation-delay: 0.2s;
}

.architecture-card:nth-child(5) {
  animation-delay: 0.3s;
}

.feature-card:nth-child(1) {
  animation-delay: 0.1s;
}

.feature-card:nth-child(2) {
  animation-delay: 0.2s;
}

.feature-card:nth-child(3) {
  animation-delay: 0.3s;
}

.feature-card:nth-child(4) {
  animation-delay: 0.4s;
}

.advantage-item:nth-child(1) {
  animation-delay: 0.1s;
}

.advantage-item:nth-child(2) {
  animation-delay: 0.2s;
}

.advantage-item:nth-child(3) {
  animation-delay: 0.3s;
}

.advantage-item:nth-child(4) {
  animation-delay: 0.4s;
}

.dashboard-footer {
  animation-delay: 0.5s;
}
</style>