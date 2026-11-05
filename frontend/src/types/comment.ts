export interface ArticleComment {
  id: number
  articleId: number
  authorId: number
  articleTitle?: string
  author: string
  content: string
  createdAt: string
  parentId?: number | null
}

export interface MyComments {
  written: ArticleComment[]
  received: ArticleComment[]
}

export interface CommentReport {
  id: number
  commentId: number
  articleId: number
  articleTitle: string
  commentAuthor: string
  commentContent: string
  reporter: string
  reason: string
  status: 'PENDING' | 'DISMISSED' | 'DELETED'
  createdAt: string
}
