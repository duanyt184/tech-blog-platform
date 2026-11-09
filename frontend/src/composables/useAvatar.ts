import { reactive } from 'vue'
export const avatarRevisions = reactive<Record<number, string>>({})
export function refreshAvatar(id: number, revision = String(Date.now())) {
  avatarRevisions[id] = revision
}
