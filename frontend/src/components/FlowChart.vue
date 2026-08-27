<template>
  <!-- 流程图：节点状态 = 图标 + 颜色 + 文字（三通道编码） -->
  <div class="flow-chart">
    <template v-for="(node, i) in nodes" :key="node.key">
      <div class="flow-node" :class="nodeState(node)">
        <div class="node-box">
          <span>{{ stateIcon(node) }}</span>
          <span class="node-name">{{ node.name }}</span>
        </div>
        <div class="node-meta" v-if="node.sla_hours">SLA {{ node.sla_hours }}h</div>
        <div class="node-meta" v-else>—</div>
      </div>
      <span v-if="i < nodes.length - 1" class="flow-arrow">──▶</span>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  nodes: { type: Array, default: () => [] },
  currentKey: { type: String, default: null },
  completedKeys: { type: Array, default: () => [] }
})

function nodeState(node) {
  if (props.completedKeys.includes(node.key)) return 'completed'
  if (node.key === props.currentKey) return 'current'
  return 'pending'
}

function stateIcon(node) {
  const s = nodeState(node)
  return s === 'completed' ? '✅' : s === 'current' ? '🔄' : '⏳'
}
</script>
