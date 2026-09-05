import { apiClient } from '@/api/client'
import type { components } from '@/api/generated/types'
type S=components['schemas']; export type MockInterviewSession=S['MockInterviewSession']; export type MockInterviewTurn=S['MockInterviewTurn']
const h=()=>({'Idempotency-Key':crypto.randomUUID()})
export const createMockInterview=async(projectId:string)=> (await apiClient.post<MockInterviewSession>('/mock-interviews',{projectId},{headers:h()})).data
export const getMockInterview=async(id:string)=> (await apiClient.get<MockInterviewSession>(`/mock-interviews/${id}`)).data
export const getMockInterviewTurns=async(id:string)=> (await apiClient.get<MockInterviewTurn[]>(`/mock-interviews/${id}/turns`)).data
export const answerMockInterview=async(id:string,version:number,content:string)=> (await apiClient.post<MockInterviewSession>(`/mock-interviews/${id}/answers`,{content},{headers:{...h(),'If-Match-Version':String(version)}})).data
export const evaluateMockInterviewAnswer=async(id:string,turnId:string,version:number)=> (await apiClient.post<MockInterviewSession>(`/mock-interviews/${id}/turns/${turnId}/evaluation`,undefined,{headers:{...h(),'If-Match-Version':String(version)}})).data
export const transitionMockInterview=async(id:string,version:number,targetStatus:'COMPLETED'|'CANCELED')=> (await apiClient.post<MockInterviewSession>(`/mock-interviews/${id}/transition`,{targetStatus},{headers:{...h(),'If-Match-Version':String(version)}})).data
