import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './styles/global.css'
import 'highlight.js/styles/github.css'
import 'katex/dist/katex.min.css'
import './styles/markdown.css'

createApp(App).use(router).mount('#app')

