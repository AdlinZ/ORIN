<template>
  <div class="not-found-container">
    <div class="content">
        <h1 class="code">404</h1>
        <p class="desc">抱歉，您访问的页面不存在</p>
        <el-button type="primary" @click="$router.push('/')">回到首页</el-button>
    </div>
  </div>
</template>

<style scoped>
.not-found-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background-color: #f0f2f5;
}
.content {
    text-align: center;
}
.code {
    font-size: 120px;
    color: var(--neutral-gray-3);
    margin: 0;
}
.desc {
    font-size: 24px;
    color: var(--neutral-gray-5);
    margin-bottom: 30px;
}
</style>
