import { reactive } from 'vue'
import { http } from '../api/http'
import type { ArticleStatus } from '../types/article'
import { ApiError } from '../api/http'
export interface Category {
  id: number
  name: string
  description: string
  color: string
  parentId: number | null
}
export const categories = reactive<Category[]>([])
export async function loadCategories() {
  const { data } = await http.get<Category[]>('/community/categories')
  const order = [
    '数学基础',
    '数学进阶',
    '编程入门',
    '数据结构与算法',
    '软件工程',
    '计算机系统基础',
    '操作系统',
    '计算机网络',
    '编译原理',
    'Web开发',
    '机器学习',
    '深度学习',
    '数据科学',
  ]
  data.sort(
    (a, b) =>
      (order.indexOf(a.name) < 0 ? 100 : order.indexOf(a.name)) -
      (order.indexOf(b.name) < 0 ? 100 : order.indexOf(b.name)),
  )
  categories.splice(0, categories.length, ...data)
}
export const categoryColor = (name: string) =>
  categories.find((item) => item.name === name)?.color || '#777'
export const statusLabels: Record<ArticleStatus, string> = {
  DRAFT: '草稿',
  PENDING: '待审核',
  REJECTED: '已退回',
  PUBLISHED: '已发布',
}
export const formatCount = (count: number) =>
  count >= 1000 ? `${(count / 1000).toFixed(1)}k` : String(count)
export const formatDate = (value: string | null | undefined) =>
  value ? value.slice(0, 10).replace(/-/g, ' / ') : '—'
export const formatTime = (value: string) =>
  value.replace('T', ' ').slice(0, 16)
export const errorMessage = (error: unknown) =>
  error instanceof ApiError ? error.firstFieldMessage : '操作失败，请稍后重试'

export const relativeTime = (value: string) => {
  const elapsed = Math.max(0, Date.now() - new Date(value).getTime())
  if (elapsed < 60_000) return 'now'
  if (elapsed < 3600_000) return `${Math.floor(elapsed / 60_000)}m`
  if (elapsed < 86400_000) return `${Math.floor(elapsed / 3600_000)}h`
  if (elapsed < 30 * 86400_000) return `${Math.floor(elapsed / 86400_000)}d`
  return new Date(value).toLocaleDateString('en-US', {
    month: 'short',
    year: '2-digit',
  })
}
