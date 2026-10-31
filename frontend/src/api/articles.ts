import { http } from './http'
import type { Article, CreateArticlePayload } from '../types/article'

/** 发布一篇纯文本文章；成功后返回完整文章（含 id、status、作者等） */
export async function createArticle(payload: CreateArticlePayload): Promise<Article> {
  const { data } = await http.post<Article>('/articles', payload)
  return data
}
