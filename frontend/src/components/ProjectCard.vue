<template>
  <router-link class="project-card" :to="`/projects/${project.id}`">
    <div class="pc-top">
      <div>
        <div class="pc-title">{{ project.name }}</div>
        <div class="pc-sub">
          <span v-if="project.customerName">客户：{{ project.customerName }} · </span>
          <span>流程：{{ project.templateName || '未绑定' }}</span>
        </div>
      </div>
      <RiskTag :status="project.riskStatus" />
    </div>

    <div class="fp-progress">
      <div class="track">
        <div class="bar" :style="{ width: (project.progress * 100).toFixed(0) + '%' }"></div>
      </div>
      <span class="pct">{{ Math.round(project.progress * 100) }}%</span>
    </div>

    <div class="pc-activity" v-if="project.latestActivity">
      💬 {{ project.latestActivity }}
    </div>
    <div class="pc-activity muted" v-else>暂无动态，等待消息同步或导入</div>

    <div class="small muted flex-between">
      <span>当前节点：{{ project.currentNodeKey || '待启动' }}</span>
      <span class="mono">更新于 {{ fmtTime(project.updatedAt) }}</span>
    </div>
  </router-link>
</template>

<script setup>
import RiskTag from './RiskTag.vue'

defineProps({ project: { type: Object, required: true } })

function fmtTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(5, 16)
}
</script>
