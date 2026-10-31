<script setup lang="ts">
withDefaults(defineProps<{ name: string; size?: number }>(), { size: 20 })
const paths: Record<string, string> = {
  menu: 'M4 6h16M4 12h16M4 18h16',
  search: 'm21 21-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0',
  home: 'm3 10 9-7 9 7v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1ZM9 21v-8h6v8',
  book: 'M4 3h6a3 3 0 0 1 3 3v15a5 5 0 0 0-4-2H3V4a1 1 0 0 1 1-1ZM13 6a3 3 0 0 1 3-3h5v16h-4a5 5 0 0 0-4 2',
  edit: 'm15 4 5 5M4 16 16 4a3.5 3.5 0 0 1 5 5L9 21H3ZM3 21l6-1',
  file: 'M14 2H5v20h14V7ZM14 2v6h5M8 13h8M8 17h6',
  bookmark: 'M5 3h14v18l-7-4-7 4Z',
  arrow: 'M19 12H5m6-6-6 6 6 6',
  chevron: 'm9 5 7 7-7 7',
  clock: 'M12 8v5l3 2M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0',
  eye: 'M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12ZM15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0',
  heart: 'M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z',
  pin: 'm16 3 5 5-4 1-4 5v3l-3-3-6 6m6-6-3-3h3l5-4Z',
  close: 'm6 6 12 12M6 18 18 6',
  check: 'm5 12 4 4L19 6',
  code: 'm8 6-6 6 6 6m8-12 6 6-6 6m-3-15-2 18',
  sun: 'M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1 1m12 12 1 1M5 19l1-1M18 6l1-1M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
}
</script>
<template>
  <svg :width="size" :height="size" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path :d="paths[name] || paths.file" />
  </svg>
</template>
