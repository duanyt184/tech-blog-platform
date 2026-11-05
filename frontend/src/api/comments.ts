import { http } from './http'
import type { ArticleComment, CommentReport, MyComments } from '../types/comment'

export async function listComments(articleId: number | string) {
  return (await http.get<ArticleComment[]>(`/articles/${articleId}/comments`)).data
}

export async function addComment(articleId: number | string, content: string, parentId?: number | null) {
  return (await http.post<ArticleComment>(`/articles/${articleId}/comments`, { content, parentId: parentId ?? null })).data
}

export async function getMyComments() {
  return (await http.get<MyComments>('/my/comments')).data
}

export async function deleteComment(commentId: number) {
  await http.delete(`/comments/${commentId}`)
}

export async function reportComment(commentId: number, reason: string) {
  await http.post(`/comments/${commentId}/reports`, { reason })
}

export async function listReports() {
  return (await http.get<CommentReport[]>('/editor/reports')).data
}

export async function resolveReport(reportId: number, action: 'DELETE' | 'DISMISS') {
  await http.post(`/editor/reports/${reportId}/resolve`, { action })
}
