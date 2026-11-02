<script setup>
// 通用图表容器：所有 ECharts 图都通过它渲染。
//
// 为什么要有这个组件（而不是每个页面自己 new echarts.init）：
//   1. dispose 容易漏 —— 漏一次就是一次内存泄漏，切十次页面就有十个实例挂在 DOM 上
//   2. 窗口/侧边栏尺寸变化时图表不会自适应，会糊成一团
//   3. 数据变少时 setOption 默认是"合并"，旧数据会残留，必须传 notMerge = true
// 这三件事每个页面都要做对，抽成一个组件只做对一次。
import { ref, onMounted, onBeforeUnmount, watch, nextTick, computed } from 'vue'

// 【关键】按需引入，绝不能 import * as echarts from 'echarts'
// 全量引入约 1MB，会把之前"按需引入 Element Plus"省下的体积全吃回去。
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  TitleComponent,
  LegendComponent,
  DatasetComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  BarChart,
  LineChart,
  PieChart,
  GridComponent,
  TooltipComponent,
  TitleComponent,
  LegendComponent,
  DatasetComponent,
  CanvasRenderer,
])

const props = defineProps({
  // 完整的 ECharts option 对象，由父组件构造
  option: { type: Object, required: true },
  // 容器高度，默认 320px
  height: { type: String, default: '320px' },
  // 是否显示加载态
  loading: { type: Boolean, default: false },
})

const el = ref(null)
let chart = null
let resizeObserver = null

// 暗色主题的通用样式：ECharts 默认配色是给白底设计的，
// 直接用在深色页面上文字会看不清（深灰字 + 深灰底）。
const darkDefaults = {
  backgroundColor: 'transparent',
  textStyle: { color: '#c8c8cc' },
}

function render() {
  if (!chart) return
  chart.setOption({ ...darkDefaults, ...props.option }, true) // true = notMerge，避免旧数据残留
}

onMounted(async () => {
  await nextTick()
  chart = echarts.init(el.value, null, { renderer: 'canvas' })
  render()

  // ResizeObserver 比 window.resize 更准：侧边栏折叠不会触发 window resize，
  // 但会改变容器宽度。用 ResizeObserver 才能跟上。
  resizeObserver = new ResizeObserver(() => chart && chart.resize())
  resizeObserver.observe(el.value)
})

// 数据变化时重绘。deep 是为了 option 内部数组变化也能触发。
watch(() => props.option, render, { deep: true })

onBeforeUnmount(() => {
  // 必须清理：不 dispose 就是内存泄漏，不断开 observer 就是回调泄漏
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  if (chart) {
    chart.dispose()
    chart = null
  }
})

// 没有数据时显示占位，而不是画一个空白坐标系（那看起来像加载失败）
const empty = computed(() => {
  const series = props.option?.series
  if (!series) return true
  const list = Array.isArray(series) ? series : [series]
  return list.every((s) => !s.data || s.data.length === 0)
})
</script>

<template>
  <div v-loading="loading" class="chart-wrap">
    <div ref="el" :style="{ width: '100%', height }"></div>
    <div v-if="!loading && empty" class="chart-empty">暂无数据</div>
  </div>
</template>

<style scoped>
.chart-wrap {
  position: relative;
  width: 100%;
}

.chart-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ch-text-muted, #8e8e93);
  font-size: 13px;
  pointer-events: none;
}
</style>
