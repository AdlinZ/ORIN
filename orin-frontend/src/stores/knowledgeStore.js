import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
    getBoundKnowledge,
    saveProceduralKnowledgeSkill,
    uploadUnstructuredKnowledge
} from '@/api/knowledge'

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
            const res = await getBoundKnowledge(agentId, type)
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
        const uploadKey = `${agentId}:${kbId}:${file.name}:${file.size}:${file.lastModified || 0}`
        uploadingMap.value.set(uploadKey, { fileName: file.name, status: 'UPLOADING', progress: 0 })

        try {
            const res = await uploadUnstructuredKnowledge(agentId, kbId, file)
            const taskId = res?.taskId || res?.id || uploadKey
            if (taskId !== uploadKey) {
                uploadingMap.value.delete(uploadKey)
            }
            uploadingMap.value.set(taskId, {
                fileName: file.name,
                status: res?.status || 'COMPLETED',
                progress: Number.isFinite(Number(res?.progress)) ? Number(res.progress) : 100
            })
            await loadKnowledge(agentId, 'DOCUMENT')
            setTimeout(() => uploadingMap.value.delete(taskId), 3000)
        } catch (e) {
            uploadingMap.value.set(uploadKey, { fileName: file.name, status: 'ERROR', progress: 0 })
            ElMessage.error('Upload failed')
        }

        return uploadKey
    }

    // 3. Save Workflow (Procedural)
    const saveWorkflow = async (agentId, workflowData) => {
        try {
            await saveProceduralKnowledgeSkill(agentId, workflowData)
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
