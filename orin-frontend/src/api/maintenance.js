import request from '@/utils/request'

export function getMaintenanceInfo() {
  return request.get('/system/maintenance/info')
}

export function getMaintenanceLogs() {
  return request.get('/system/maintenance/logs')
}

export function startMaintenanceBackup(data) {
  return request.post('/system/maintenance/backup', data)
}

export function restoreMaintenanceBackup(data) {
  return request.post('/system/maintenance/restore', data)
}

export function startSystemUpgrade() {
  return request.post('/system/maintenance/upgrade')
}

export function archiveMaintenanceLogs(data) {
  return request.post('/system/maintenance/log-archive', data)
}

export function cleanMaintenanceCache(data) {
  return request.post('/system/maintenance/cache-clean', data)
}

export function getMaintenanceHealth() {
  return request.get('/system/maintenance/health')
}
