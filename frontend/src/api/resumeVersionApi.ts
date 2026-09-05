import { apiClient } from '@/api/client'
import type { components } from '@/api/generated/types'
type S=components['schemas']; export type ResumeVersion=S['ResumeVersion']; export type ResumeVersionComparison=S['ResumeVersionComparison']
const headers=()=>({'Idempotency-Key':crypto.randomUUID()})
export const listResumeVersions=async()=> (await apiClient.get<ResumeVersion[]>('/resume-versions')).data
export const createResumeVersion=async(body:{name:string;content:string})=> (await apiClient.post<ResumeVersion>('/resume-versions',body,{headers:headers()})).data
export const compareResumeVersions=async(leftId:string,rightId:string)=> (await apiClient.get<ResumeVersionComparison>('/resume-versions/compare',{params:{leftId,rightId}})).data
