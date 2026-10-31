/** 后端 articles.status 的取值，与 backend 的 ArticleStatus 枚举保持一致 */
export type ArticleStatus = 'DRAFT' | 'PENDING' | 'REJECTED' | 'PUBLISHED'

/** 文章作者的最小信息（后端不会返回 email / passwordHash） */
export interface ArticleAuthor {
  id: number
  username: string
}

/** 文章接口返回的结构（发布成功返回、后续列表/详情复用） */
export interface Article {
  id: number
  title: string
  content: string
  status: ArticleStatus
  viewCount: number
  author: ArticleAuthor
  createdAt: string
  updatedAt: string
  publishedAt: string | null
}

/** 发布文章的请求体 */
export interface CreateArticlePayload {
  title: string
  content: string
}
