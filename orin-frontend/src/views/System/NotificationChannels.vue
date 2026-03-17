<template>
  <div class="notification-channels-container">
    <PageHeader
      title="通知渠道"
      description="配置告警通知渠道并验证发送能力"
      icon="Bell"
    />

    <NotificationChannelsPanel
      show-mail-center-link
      @go-mail-center="goToMailCenter"
    />
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router';
import PageHeader from '@/components/PageHeader.vue';
import NotificationChannelsPanel from '@/components/notification/NotificationChannelsPanel.vue';
import { ROUTES } from '@/router/routes';

const router = useRouter();

const goToMailCenter = () => {
  router.push(ROUTES.CONTROL.MAIL_CENTER);
};
</script>

<style scoped>
.notification-channels-container {
  padding: 24px;
  max-width: 1000px;
  margin: 0 auto;
}
</style>

