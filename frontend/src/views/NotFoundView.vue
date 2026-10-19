<template>
  <el-result icon="warning" title="页面不存在" sub-title="地址可能输错了，或这个链接已经失效">
    <template #extra>
      <RouterLink to="/">
        <el-button type="primary">回到首页</el-button>
      </RouterLink>
    </template>
  </el-result>
</template>
