import Cookies from "js-cookie"
import requestClient from "@/utils/request"

const PLAYGROUND_BASE_URL = "/api/playground"

function resolveApiUrl(path) {
  const normalizedPath = String(path || "")
  const base = String(PLAYGROUND_BASE_URL || "").replace(/\/$/, "")
  const cleanPath = normalizedPath.startsWith("/api/")
    ? normalizedPath.slice(4)
    : normalizedPath
  if (!cleanPath.startsWith("/")) return `${base}/${cleanPath}`
  return `${base}${cleanPath}`
}

async function playgroundRequest(path, options = {}) {
  const { body, headers, method = "GET", ...config } = options
  const payload = typeof body === "string" ? JSON.parse(body) : body
  return requestClient({
    url: resolveApiUrl(path),
    baseURL: "",
    method,
    data: payload,
    headers,
    ...config,
  })
}

export function fetchTemplates() {
  return playgroundRequest("/api/workflow-templates")
}

export function fetchAppSettings() {
  return playgroundRequest("/api/settings")
}

export function updateAppSettings(payload) {
  return playgroundRequest("/api/settings", {
    method: "PUT",
    body: JSON.stringify(payload),
  })
}

export function fetchSkills() {
  return playgroundRequest("/api/skills")
}

export function createSkill(payload) {
  return playgroundRequest("/api/skills", {
    method: "POST",
    body: JSON.stringify(payload),
  })
}

export function syncSkills(payload) {
  return playgroundRequest("/api/skills/sync", {
    method: "POST",
    body: JSON.stringify(payload),
  })
}

export function installSkill(skillId) {
  return playgroundRequest(`/api/skills/${skillId}/install`, {
    method: "POST",
  })
}

export function fetchAgents() {
  return playgroundRequest("/api/agents")
}

export function createAgent(payload) {
  return playgroundRequest("/api/agents", {
    method: "POST",
    body: JSON.stringify(payload),
  })
}

export function updateAgent(agentId, payload) {
  return playgroundRequest(`/api/agents/${agentId}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  })
}

export function deleteAgent(agentId) {
  return playgroundRequest(`/api/agents/${agentId}`, {
    method: "DELETE",
  })
}

export function fetchWorkflows() {
  return playgroundRequest("/api/workflows")
}

export function createWorkflow(payload) {
  return playgroundRequest("/api/workflows", {
    method: "POST",
    body: JSON.stringify(payload),
  })
}

export function updateWorkflow(workflowId, payload) {
  return playgroundRequest(`/api/workflows/${workflowId}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  })
}

export function deleteWorkflow(workflowId) {
  return playgroundRequest(`/api/workflows/${workflowId}`, {
    method: "DELETE",
  })
}

export function fetchWorkflowGraph(workflowId) {
  return playgroundRequest(`/api/workflows/${workflowId}/graph`)
}

export function runWorkflow(payload) {
  return playgroundRequest("/api/runs", {
    method: "POST",
    body: JSON.stringify(payload),
  })
}

export function fetchConversations(workflowId) {
  const query = workflowId ? `?workflow_id=${encodeURIComponent(workflowId)}` : ""
  return playgroundRequest(`/api/conversations${query}`)
}

export function createConversation(workflowId) {
  return playgroundRequest("/api/conversations", {
    method: "POST",
    body: JSON.stringify({ workflow_id: workflowId }),
  })
}

export function fetchConversation(conversationId) {
  return playgroundRequest(`/api/conversations/${conversationId}`)
}

export function deleteConversation(conversationId) {
  return playgroundRequest(`/api/conversations/${conversationId}`, {
    method: "DELETE",
  })
}

function parseSseFrame(frame) {
  const lines = frame.split(/\r?\n/)
  let eventName = "message"
  let dataText = ""
  for (const line of lines) {
    if (!line || line.startsWith(":")) continue
    if (line.startsWith("event:")) {
      eventName = line.slice(6).trim()
      continue
    }
    if (line.startsWith("data:")) {
      dataText += `${line.slice(5).trimStart()}\n`
    }
  }
  if (!dataText) return null
  const raw = dataText.trim()
  try {
    return { event: eventName, data: JSON.parse(raw) }
  } catch {
    return { event: eventName, data: raw }
  }
}

export async function runWorkflowStream(payload, { onTrace, onFinal, onError, onEnd, signal } = {}) {
  const token = Cookies.get("orin_token") || window.localStorage.getItem("orin_token") || window.sessionStorage.getItem("orin_token") || ""
  const response = await fetch(resolveApiUrl("/api/runs/stream"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(payload),
    signal,
  })

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(errorText || `Request failed: ${response.status}`)
  }

  if (!response.body) {
    throw new Error("Streaming body is not available in this browser.")
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder("utf-8")
  let buffer = ""

  let streamDone = false
  while (!streamDone) {
    const { value, done } = await reader.read()
    streamDone = done
    if (streamDone) break
    buffer += decoder.decode(value, { stream: true })
    buffer = buffer.replace(/\r\n/g, "\n")

    let splitIndex = buffer.indexOf("\n\n")
    while (splitIndex >= 0) {
      const frame = buffer.slice(0, splitIndex)
      buffer = buffer.slice(splitIndex + 2)

      const parsed = parseSseFrame(frame)
      if (parsed) {
        if (parsed.event === "trace") onTrace?.(parsed.data)
        if (parsed.event === "final") onFinal?.(parsed.data)
        if (parsed.event === "error") onError?.(parsed.data)
      }
      splitIndex = buffer.indexOf("\n\n")
    }
  }

  const parsedRemainder = parseSseFrame(buffer)
  if (parsedRemainder) {
    if (parsedRemainder.event === "trace") onTrace?.(parsedRemainder.data)
    if (parsedRemainder.event === "final") onFinal?.(parsedRemainder.data)
    if (parsedRemainder.event === "error") onError?.(parsedRemainder.data)
  }

  onEnd?.()
}
