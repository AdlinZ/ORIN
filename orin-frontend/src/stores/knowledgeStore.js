import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

export const useKnowledgeStore = defineStore('knowledge', () => {
    // State
    const knowledgeList = ref([])
    const loading = ref(false)
    const uploadingMap = ref(new Map()) // taskId -> status

    // Actions

    // 1. Unified Load
    const loadKnowledge = async (agentId, type) => {
        loading.value = true
        try {
            const res = await request.get(`/knowledge/agents/${agentId}`, { params: { type } })
            knowledgeList.value = res.data || []
        } catch (error) {
            console.error(error)
            ElMessage.error('Failed to load knowledge')
        } finally {
            loading.value = false
        }
    }

    // 2. Unstructured File Upload (Async Tracking)
    const uploadFile = async (agentId, file, kbId) => {
        const formData = new FormData()
        formData.append('file', file)
        formData.append('kbId', kbId)
        formData.append('agentId', agentId)

        // Mock Task ID for now, in real backend this comes from response
        const taskId = Date.now().toString()

        // Start "Amber" state tracking
        uploadingMap.value.set(taskId, { fileName: file.name, status: 'VECTORIZING', progress: 0 })

        try {
            await request.post('/knowledge/unstructured/upload', formData)
            // Poll for status or simulate progress
            simulateProgress(taskId)
        } catch (e) {
            uploadingMap.value.set(taskId, { fileName: file.name, status: 'ERROR', progress: 0 })
            ElMessage.error('Upload failed')
        }

        return taskId
    }

    const simulateProgress = (taskId) => {
        let p = 0
        const interval = setInterval(() => {
            p += 10
            if (p > 100) {
                clearInterval(interval)
                uploadingMap.value.set(taskId, { ...uploadingMap.value.get(taskId), status: 'COMPLETED', progress: 100 })
                // Clear after delay
                setTimeout(() => uploadingMap.value.delete(taskId), 3000)
            } else {
                uploadingMap.value.set(taskId, { ...uploadingMap.value.get(taskId), progress: p })
            }
        }, 500)
    }

    // 3. Save Workflow (Procedural)
    const saveWorkflow = async (agentId, workflowData) => {
        // Validation handled in Component usually, but we can double check
        try {
            // Validate and Serialize is implicitly done by JSON.stringify in request
            // But we ensure struct is correct
            const payload = {
                ...workflowData,
                agentId
            }
            await request.post('/knowledge/procedural/skills', payload)
            ElMessage.success('Skill registered successfully')
        } catch (e) {
            ElMessage.error('Failed to save skill')
            throw e
        }
    }

    return {
        knowledgeList,
        loading,
        uploadingMap,
        loadKnowledge,
        uploadFile,
        saveWorkflow
    }
})
