export type ArticleStatus = 'DRAFT' | 'PENDING' | 'REJECTED' | 'PUBLISHED'
export type ContentFormat = 'PLAIN_TEXT' | 'MARKDOWN'
export interface ReviewEvent {
  id: number
  actor: string
  action: 'SUBMITTED' | 'WITHDRAWN' | 'PUBLISHED' | 'REJECTED'
  comment: string
  articleVersion: number
  createdAt: string
}
export interface Article {
  id: number
  authorId: number
  author: string
  title: string
  summary: string
  content?: string
  contentFormat: ContentFormat
  category: string
  tags: string[]
  status: ArticleStatus
  version: number
  viewCount: number
  createdAt: string
  updatedAt: string
  publishedAt: string | null
  bookmarked?: boolean
  reviews?: ReviewEvent[]
}
export interface ArticleWrite {
  title: string
  summary: string
  content: string
  contentFormat: ContentFormat
  category: string
  tags: string[]
  version?: number
}
export interface PageResult<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
export type ArticleScope = 'public' | 'mine' | 'editor' | 'bookmarks'
