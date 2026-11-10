<script setup lang="ts">
withDefaults(
  defineProps<{
    /** 页面标题 */
    title: string
    /** 一句话说明本页目标 */
    summary: string
    /** 对应用例编号，如 UC-D04 */
    useCase?: string
    /** 页面内计划包含的区域 */
    blocks?: string[]
  }>(),
  {
    useCase: '',
    blocks: () => ['列表区'],
  },
)
</script>

<template>
  <section class="page">
    <header class="page__head">
      <h2 class="page__title">{{ title }}</h2>
      <el-tag v-if="useCase" type="info" effect="plain">{{ useCase }}</el-tag>
    </header>

    <p class="page__summary">{{ summary }}</p>

    <el-alert
      type="info"
      show-icon
      :closable="false"
      title="R2 仅建立路由与页面骨架，业务交互在 R3–R5 按用例接入。"
    />

    <div class="page__blocks">
      <el-card v-for="block in blocks" :key="block" shadow="never" class="page__card">
        <template #header>
          <span class="page__card-title">{{ block }}</span>
        </template>
        <el-skeleton :rows="3" animated />
      </el-card>
    </div>

    <slot />
  </section>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.page__head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page__title {
  margin: 0;
  font-size: 20px;
  color: var(--mp-color-primary);
}

.page__summary {
  margin: 0;
  color: var(--mp-text-secondary);
  line-height: 1.7;
}

.page__blocks {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
}

.page__card {
  min-height: 168px;
}

.page__card-title {
  font-weight: 600;
  color: var(--mp-text-regular);
}
</style>
