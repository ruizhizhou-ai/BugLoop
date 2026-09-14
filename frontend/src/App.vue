<!-- 本文件是前端根组件，项目骨架阶段用于验证 Vue、路由、Pinia 和 Element Plus 已正确接入。 -->
<script setup lang="ts">
import { ElCard, ElTag } from 'element-plus'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/tag/style/css'
</script>

<template>
  <main class="framework-page">
    <el-card class="framework-card" shadow="never">
      <template #header>
        <div class="framework-card__header">
          <h1>BugLoop</h1>
          <el-tag type="success">项目骨架已就绪</el-tag>
        </div>
      </template>
      <p>Vue 3、Vue Router、Pinia 和 Element Plus 已完成基础接入。</p>
      <p class="framework-card__hint">下一阶段将按照 Spec 实现登录和用户基础能力。</p>
    </el-card>
  </main>
</template>

<style scoped>
.framework-page {
  display: grid;
  min-height: 100vh;
  padding: 24px;
  place-items: center;
}

.framework-card {
  width: min(100%, 640px);
}

.framework-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.framework-card__header h1 {
  margin: 0;
}

.framework-card__hint {
  margin-bottom: 0;
  color: #606266;
}
</style>
