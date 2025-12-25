import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'

import App from './App.vue'
import router from './router'
import PrimeVue from 'primevue/config'
import Aura from '@primeuix/themes/aura'
import ToastService from 'primevue/toastservice'
import Tooltip from 'primevue/tooltip'
const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(PrimeVue, {
  theme: {
    preset: Aura,
  },
})
app.use(ToastService)
app.directive('tooltip', Tooltip)
app.mount('#app')
