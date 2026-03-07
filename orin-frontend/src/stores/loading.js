import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useLoadingStore = defineStore('loading', () => {
  // Global loading counter
  const loadingCount = ref(0)
  
  // Named loading states for specific operations
  const namedLoadings = ref({})
  
  // Start global loading
  const startLoading = () => {
    loadingCount.value++
  }
  
  // Stop global loading
  const stopLoading = () => {
    if (loadingCount.value > 0) {
      loadingCount.value--
    }
  }
  
  // Check if any loading is active
  const isLoading = () => {
    return loadingCount.value > 0
  }
  
  // Set named loading state
  const setLoading = (name, loading) => {
    if (loading) {
      namedLoadings.value[name] = true
      startLoading()
    } else {
      delete namedLoadings.value[name]
      stopLoading()
    }
  }
  
  // Get named loading state
  const isNamedLoading = (name) => {
    return !!namedLoadings.value[name]
  }
  
  // Clear all loading states
  const clearAll = () => {
    loadingCount.value = 0
    namedLoadings.value = {}
  }
  
  return {
    loadingCount,
    namedLoadings,
    startLoading,
    stopLoading,
    isLoading,
    setLoading,
    isNamedLoading,
    clearAll
  }
})
