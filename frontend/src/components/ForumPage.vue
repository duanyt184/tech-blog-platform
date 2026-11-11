<script setup lang="ts">
import { computed, provide, reactive } from 'vue'
import { routeLocationKey, type RouteLocationNormalizedLoaded } from 'vue-router'
const props = defineProps<{ route: RouteLocationNormalizedLoaded }>()
// Keep the page's route context intact while a composer is layered above it.
provide(routeLocationKey, reactive(Object.fromEntries(
  Object.keys(props.route).map(key => [key, computed(() => props.route[key as keyof RouteLocationNormalizedLoaded])]),
)) as unknown as RouteLocationNormalizedLoaded)
</script>
<template><RouterView :route="route" /></template>
