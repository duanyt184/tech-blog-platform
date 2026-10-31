import type { ArticleStatus } from '../types/article'
import { ApiError } from '../api/http'
export const categories = [
  { name: '前端开发', color: '#4e8d77' }, { name: '后端开发', color: '#6684c3' },
  { name: '工程实践', color: '#c79752' }, { name: '数据库', color: '#9b7bb5' },
  { name: '学习笔记', color: '#ce827b' },
]
export const categoryColor = (name: string) => categories.find(item => item.name === name)?.color || '#777'
export const statusLabels: Record<ArticleStatus, string> = { DRAFT: '草稿', PENDING: '待审核', REJECTED: '已退回', PUBLISHED: '已发布' }
export const formatCount = (count: number) => count >= 1000 ? `${(count / 1000).toFixed(1)}k` : String(count)
export const formatDate = (value: string | null | undefined) => value ? value.slice(0, 10).replace(/-/g, ' / ') : '—'
export const formatTime = (value: string) => value.replace('T', ' ').slice(0, 16)
export const errorMessage = (error: unknown) => error instanceof ApiError ? error.firstFieldMessage : '操作失败，请稍后重试'
