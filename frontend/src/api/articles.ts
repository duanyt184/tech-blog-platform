import { http } from './http'
import type { Article, ArticleScope, ArticleWrite, PageResult } from '../types/article'
const paths: Record<ArticleScope, string> = {
  public: '/articles', mine: '/my/articles', editor: '/editor/articles', bookmarks: '/my/bookmarks',
}
export async function listArticles(scope: ArticleScope, params: Record<string, unknown>, signal?: AbortSignal) {
  return (await http.get<PageResult<Article>>(paths[scope], { params, signal })).data
}
export async function getArticle(id: string | number, scope: 'public' | 'mine' | 'editor' = 'public') {
  return (await http.get<Article>(`${paths[scope]}/${id}`)).data
}
export async function saveArticle(payload: ArticleWrite, id?: number) {
  return (await (id ? http.put<Article>(`/my/articles/${id}`, payload) : http.post<Article>('/my/articles', payload))).data
}
export async function submitArticle(article: Article) {
  return (await http.post<Article>(`/my/articles/${article.id}/submit`, { version: article.version })).data
}
export async function withdrawArticle(article: Article) {
  return (await http.post<Article>(`/my/articles/${article.id}/withdraw`, { version: article.version })).data
}
export async function deleteArticle(article: Article) {
  await http.delete(`/my/articles/${article.id}`, { params: { version: article.version } })
}
export async function reviewArticle(article: Article, decision: 'APPROVE' | 'REJECT', comment: string) {
  return (await http.post<Article>(`/editor/articles/${article.id}/review`, { version: article.version, decision, comment })).data
}
export async function setBookmark(id: number, saved: boolean) {
  if (saved) await http.put(`/my/bookmarks/${id}`)
  else await http.delete(`/my/bookmarks/${id}`)
}
