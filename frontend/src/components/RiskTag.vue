<template>
  <!-- 风险状态标签：状态色必须配图标+文字，绝不单靠颜色传达 -->
  <span class="risk-tag" :class="status.toLowerCase()">
    <span>{{ icon }}</span>
    <span>{{ label }}</span>
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: { type: String, default: 'NORMAL' }
})

const map = {
  NORMAL: { icon: '●', label: '正常' },
  WARNING: { icon: '▲', label: '预警' },
  BLOCKED: { icon: '⛔', label: '卡顿' }
}
const icon = computed(() => (map[props.status] || map.NORMAL).icon)
const label = computed(() => (map[props.status] || map.NORMAL).label)
</script>
