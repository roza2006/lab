import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import AppLayout from '@/layouts/AppLayout.vue'
import { pinia } from '@/stores'
import { useAuthStore, type Role } from '@/stores/auth'

import { HOME_BY_ROLE } from './menus'

declare module 'vue-router' {
  interface RouteMeta {
    /** 浏览器标题 */
    title?: string
    /** 无需登录即可访问 */
    public?: boolean
    /** 允许访问的角色；为空表示登录即可 */
    roles?: Role[]
    /** 对应用例编号（见 docs/requirements/use-cases.md） */
    useCase?: string
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/',
    component: AppLayout,
    children: [
      {
        path: '',
        name: 'root',
        redirect: () => {
          const auth = useAuthStore(pinia)
          return auth.role ? HOME_BY_ROLE[auth.role] : '/login'
        },
      },

      // ---------- 患者端（7 页） ----------
      {
        path: '/patient/home',
        name: 'patient-home',
        component: () => import('@/views/patient/PatientHomeView.vue'),
        meta: { title: '患者首页', roles: ['PATIENT'], useCase: 'UC-P03' },
      },
      {
        path: '/patient/consultations/new',
        name: 'patient-consult-create',
        component: () => import('@/views/patient/PatientConsultCreateView.vue'),
        meta: { title: '发起图文问诊', roles: ['PATIENT'], useCase: 'UC-P03' },
      },
      {
        path: '/patient/consultations',
        name: 'patient-consult-list',
        component: () => import('@/views/patient/PatientConsultListView.vue'),
        meta: { title: '我的问诊', roles: ['PATIENT'], useCase: 'UC-P05' },
      },
      {
        path: '/patient/consultations/:id',
        name: 'patient-chat',
        component: () => import('@/views/patient/PatientChatView.vue'),
        meta: { title: '问诊会话', roles: ['PATIENT'], useCase: 'UC-P04' },
      },
      {
        path: '/patient/prescriptions',
        name: 'patient-prescription-list',
        component: () => import('@/views/patient/PatientPrescriptionListView.vue'),
        meta: { title: '我的处方', roles: ['PATIENT'], useCase: 'UC-P06' },
      },
      {
        path: '/patient/prescriptions/:id',
        name: 'patient-prescription-detail',
        component: () => import('@/views/patient/PatientPrescriptionDetailView.vue'),
        meta: { title: '处方详情', roles: ['PATIENT'], useCase: 'UC-P06' },
      },
      {
        path: '/patient/profile',
        name: 'patient-profile',
        component: () => import('@/views/patient/PatientProfileView.vue'),
        meta: { title: '个人中心', roles: ['PATIENT'], useCase: 'UC-P07' },
      },

      // ---------- 医生端（4 页） ----------
      {
        path: '/doctor/queue',
        name: 'doctor-queue',
        component: () => import('@/views/doctor/DoctorQueueView.vue'),
        meta: { title: '待接诊队列', roles: ['DOCTOR'], useCase: 'UC-D01' },
      },
      {
        path: '/doctor/consultations/:id',
        name: 'doctor-consultation',
        component: () => import('@/views/doctor/DoctorConsultationView.vue'),
        meta: { title: '接诊与病历', roles: ['DOCTOR'], useCase: 'UC-D02' },
      },
      {
        path: '/doctor/consultations/:id/prescription',
        name: 'doctor-prescription',
        component: () => import('@/views/doctor/DoctorPrescriptionView.vue'),
        meta: { title: '开方工作台', roles: ['DOCTOR'], useCase: 'UC-D04' },
      },
      {
        path: '/doctor/history',
        name: 'doctor-history',
        component: () => import('@/views/doctor/DoctorHistoryView.vue'),
        meta: { title: '历史问诊', roles: ['DOCTOR'], useCase: 'UC-D07' },
      },

      // ---------- 药师端（3 页） ----------
      {
        path: '/pharmacist/reviews',
        name: 'pharmacist-review-list',
        component: () => import('@/views/pharmacist/PharmacistReviewListView.vue'),
        meta: { title: '待审处方', roles: ['PHARMACIST'], useCase: 'UC-H01' },
      },
      {
        path: '/pharmacist/reviews/:id',
        name: 'pharmacist-review-detail',
        component: () => import('@/views/pharmacist/PharmacistReviewDetailView.vue'),
        meta: { title: '处方审核', roles: ['PHARMACIST'], useCase: 'UC-H02' },
      },
      {
        path: '/pharmacist/dispense',
        name: 'pharmacist-dispense',
        component: () => import('@/views/pharmacist/PharmacistDispenseView.vue'),
        meta: { title: '药房工作台', roles: ['PHARMACIST'], useCase: 'UC-H05' },
      },

      // ---------- 管理端（4 页） ----------
      {
        path: '/admin/users',
        name: 'admin-users',
        component: () => import('@/views/admin/AdminUsersView.vue'),
        meta: { title: '账号与角色', roles: ['ADMIN'], useCase: 'UC-A01' },
      },
      {
        path: '/admin/permissions',
        name: 'admin-permissions',
        component: () => import('@/views/admin/AdminPermissionsView.vue'),
        meta: { title: '权限策略', roles: ['ADMIN'], useCase: 'UC-A02' },
      },
      {
        path: '/admin/drugs',
        name: 'admin-drugs',
        component: () => import('@/views/admin/AdminDrugsView.vue'),
        meta: { title: '药品目录', roles: ['ADMIN'], useCase: 'UC-A03' },
      },
      {
        path: '/admin/audits',
        name: 'admin-audits',
        component: () => import('@/views/admin/AdminAuditsView.vue'),
        meta: { title: '审计与统计', roles: ['ADMIN'], useCase: 'UC-A04' },
      },

      // ---------- 公共 ----------
      {
        path: '/403',
        name: 'forbidden',
        component: () => import('@/views/ForbiddenView.vue'),
        meta: { title: '无权限' },
      },
      {
        path: '/:pathMatch(.*)*',
        name: 'not-found',
        component: () => import('@/views/NotFoundView.vue'),
        meta: { title: '页面不存在' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  const auth = useAuthStore(pinia)
  const appName = '智慧医疗在线问诊与处方流转平台'
  document.title = to.meta.title ? `${to.meta.title} · ${appName}` : appName

  if (to.meta.public) {
    if (to.name === 'login' && auth.isLoggedIn && auth.role) {
      return HOME_BY_ROLE[auth.role]
    }
    return true
  }

  if (!auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  const allowed = to.meta.roles
  if (allowed && (!auth.role || !allowed.includes(auth.role))) {
    return { name: 'forbidden' }
  }

  return true
})

export default router
