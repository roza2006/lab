/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 后端接口前缀；默认走 Vite 代理的 /api/v1 */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
