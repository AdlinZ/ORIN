import request from '@/utils/request'

// ==================== 邮件配置 ====================

export function getMailConfig() {
    return request({
        url: '/system/mail-config',
        method: 'get'
    })
}

export function saveMailConfig(data) {
    return request({
        url: '/system/mail-config',
        method: 'post',
        data
    })
}

export function testMailConnection(data) {
    const testEmail = data?.testEmail || data?.to || ''
    return request({
        url: '/system/mail-config/test',
        method: 'post',
        params: {
            testEmail
        },
        data: {
            testEmail,
            type: data?.type,
            code: data?.code
        }
    })
}

export function getMailConfigStatus() {
    return request({
        url: '/system/mail-config/status',
        method: 'get'
    })
}

export function sendMail(data) {
    return request({
        url: '/system/mail-config/send',
        method: 'post',
        data
    })
}

// ==================== 邮件模板 ====================

export function getMailTemplates() {
    return request({
        url: '/system/mail-templates',
        method: 'get'
    })
}

export function getMailTemplateById(id) {
    return request({
        url: `/system/mail-templates/${id}`,
        method: 'get'
    })
}

export function getMailTemplateByCode(code) {
    return request({
        url: `/system/mail-templates/code/${code}`,
        method: 'get'
    })
}

export function getDefaultMailTemplate() {
    return request({
        url: '/system/mail-templates/default',
        method: 'get'
    })
}

export function getEnabledMailTemplates() {
    return request({
        url: '/system/mail-templates/enabled',
        method: 'get'
    })
}

export function saveMailTemplate(data) {
    const id = data.id
    if (id) {
        return request({
            url: `/system/mail-templates/${id}`,
            method: 'put',
            data
        })
    }
    return request({
        url: '/system/mail-templates',
        method: 'post',
        data
    })
}

export function deleteMailTemplate(id) {
    return request({
        url: `/system/mail-templates/${id}`,
        method: 'delete'
    })
}

export function batchSendMail(data) {
    return request({
        url: '/system/mail-templates/batch-send',
        method: 'post',
        data
    })
}

// ==================== 邮件收件箱 ====================

export function getMailInbox(params) {
    return request({
        url: '/system/mail-inbox',
        method: 'get',
        params
    })
}

export function getUnreadMailCount() {
    return request({
        url: '/system/mail-inbox/unread-count',
        method: 'get'
    })
}

export function getMailById(id) {
    return request({
        url: `/system/mail-inbox/${id}`,
        method: 'get'
    })
}

export function markMailAsRead(id) {
    return request({
        url: `/system/mail-inbox/${id}/read`,
        method: 'post'
    })
}

export function toggleMailStar(id) {
    return request({
        url: `/system/mail-inbox/${id}/star`,
        method: 'post'
    })
}

export function deleteMail(id) {
    return request({
        url: `/system/mail-inbox/${id}`,
        method: 'delete'
    })
}

export function fetchMail() {
    return request({
        url: '/system/mail-inbox/fetch',
        method: 'post'
    })
}

export function getImapStatus() {
    return request({
        url: '/system/mail-inbox/imap-status',
        method: 'get'
    })
}

// ==================== 邮件发送日志 ====================

export function getMailSendLogs(params) {
    return request({
        url: '/system/mail-logs',
        method: 'get',
        params
    })
}

export function retryMailSendLog(id) {
    return request({
        url: `/system/mail-logs/${id}/retry`,
        method: 'post'
    })
}

export function batchRetryMailSendLogs(ids) {
    return request({
        url: '/system/mail-logs/batch-retry',
        method: 'post',
        data: { ids }
    })
}
