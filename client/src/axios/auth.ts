import request from './request'

export type AuthUser = {
  id?: number
  username: string
  password?: string
  nickname?: string
  avatar?: string
  role?: string
}

export type AuthResponse = {
  code: number
  msg: string
  token?: string
  userInfo?: AuthUser
  data?: AuthUser
}

export function registerUser(payload: {
  username: string
  password: string
  nickname?: string
}) {
  return request.post<any, AuthResponse>('/user/register', payload)
}

export function loginUser(payload: { username: string; password: string }) {
  return request.post<any, AuthResponse>('/user/login', payload)
}
