import type { Role } from '@/stores/auth'

export interface MenuItem {
  path: string
  title: string
}

/** 各角色登录后的默认落地页 */
export const HOME_BY_ROLE: Record<Role, string> = {
  PATIENT: '/patient/home',
  DOCTOR: '/doctor/queue',
  PHARMACIST: '/pharmacist/reviews',
  ADMIN: '/admin/users',
}

/** 左侧导航；详情页（会话、处方、审核）不进入菜单，由列表页跳转 */
export const MENUS: Record<Role, MenuItem[]> = {
  PATIENT: [
    { path: '/patient/home', title: '首页' },
    { path: '/patient/consultations/new', title: '发起问诊' },
    { path: '/patient/consultations', title: '我的问诊' },
    { path: '/patient/prescriptions', title: '我的处方' },
    { path: '/patient/profile', title: '个人中心' },
  ],
  DOCTOR: [
    { path: '/doctor/queue', title: '待接诊队列' },
    { path: '/doctor/history', title: '历史问诊' },
  ],
  PHARMACIST: [
    { path: '/pharmacist/reviews', title: '待审处方' },
    { path: '/pharmacist/dispense', title: '药房工作台' },
  ],
  ADMIN: [
    { path: '/admin/users', title: '账号与角色' },
    { path: '/admin/permissions', title: '权限策略' },
    { path: '/admin/drugs', title: '药品目录' },
    { path: '/admin/audits', title: '审计与统计' },
  ],
}
