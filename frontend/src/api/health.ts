import { http } from './http'
import type { HealthResponse } from '../types/health'

export async function getHealth(): Promise<HealthResponse> {
  const { data } = await http.get<HealthResponse>('/health')
  return data
}
