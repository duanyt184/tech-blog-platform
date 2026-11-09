import { ref, watch } from 'vue'
type Theme = 'light' | 'dark' | 'system'
const saved = localStorage.getItem('forum-theme')
const theme = ref<Theme>(
  saved === 'light' || saved === 'dark' ? saved : 'system',
)
const media = window.matchMedia('(prefers-color-scheme: dark)')
function apply() {
  document.documentElement.dataset.theme =
    theme.value === 'system' ? (media.matches ? 'dark' : 'light') : theme.value
  const icon = document.querySelector<HTMLLinkElement>('#site-favicon')
  if (icon)
    icon.href = `/favicon${document.documentElement.dataset.theme === 'dark' ? '-dark' : ''}.svg?v=mashang-3`
}
media.addEventListener('change', apply)
watch(
  theme,
  (value) => {
    localStorage.setItem('forum-theme', value)
    apply()
  },
  { immediate: true },
)
export function useTheme() {
  return {
    theme,
    toggle: () => {
      theme.value =
        document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark'
    },
  }
}
