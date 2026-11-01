import './assets/main.css'

// 按需引入后，函数式组件（ElMessage / ElMessageBox）的样式不会自动带上：
// 它们在 .vue 里是「显式 import」的，unplugin 的解析器只管模板里出现的 <el-xxx>
// 和自动导入的标识符，显式 import 会被跳过 —— 结果就是弹窗、提示条没样式。
// 这里手动补齐这两个样式入口（它们各自依赖的基础变量已由其它组件带入）。
import 'element-plus/theme-chalk/el-message.css'
import 'element-plus/theme-chalk/el-message-box.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(router)
// 不再全量注册 Element Plus：
// 组件由 unplugin-vue-components 在编译期按模板里出现的 <el-xxx> 自动注册。

app.mount('#app')
