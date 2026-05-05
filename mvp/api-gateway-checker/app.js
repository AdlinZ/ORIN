const defaultConfig = {
  baseUrl: "http://127.0.0.1:8080",
  model: "Qwen/Qwen2.5-7B-Instruct",
  timeout: 30,
};

const configForm = document.querySelector("#config-form");
const chatForm = document.querySelector("#chat-form");
const baseUrlInput = document.querySelector("#base-url");
const apiKeyInput = document.querySelector("#api-key");
const modelInput = document.querySelector("#model");
const timeoutInput = document.querySelector("#timeout");
const testApiButton = document.querySelector("#test-api");
const sendButton = document.querySelector("#send-message");
const clearButton = document.querySelector("#clear-chat");
const chatInput = document.querySelector("#chat-input");
const messages = document.querySelector("#messages");
const statusBox = document.querySelector(".status-box");
const statusTitle = document.querySelector("#status-title");
const statusText = document.querySelector("#status-text");

hydrateConfig();
addMessage("system", "这里是一个最小聊天测试区。先在左侧配置 API，然后发送消息验证统一网关。");

configForm.addEventListener("submit", (event) => {
  event.preventDefault();
  saveConfig();
  setStatus("ok", "配置已保存", "现在可以发送消息，或先点“测试连接”确认公开 API 是否可访问。");
});

testApiButton.addEventListener("click", async () => {
  saveConfig();
  const config = readConfig();
  testApiButton.disabled = true;
  setStatus("warn", "正在测试", "正在检查网关连接和 API Key。");

  try {
    const health = await apiRequest({ method: "GET", path: "/v1/health", auth: false });
    const providers = await apiRequest({ method: "GET", path: "/v1/providers", auth: false });
    const healthyProviders = health.body?.statistics?.healthyProviders ?? "-";
    const totalProviders = providers.body?.statistics?.totalProviders ?? "-";

    if (health.status === 200 && health.body?.status === "ok" && providers.status === 200) {
      if (!config.apiKey) {
        setStatus("warn", "网关连接正常", `健康 Provider ${healthyProviders}/${totalProviders}。聊天还需要填写 ORIN 网关 API Key。`);
        addMessage("system", `网关连接正常：健康 Provider ${healthyProviders}/${totalProviders}。未填写 API Key，未验证聊天认证。`);
        return;
      }

      const models = await apiRequest({ method: "GET", path: "/v1/models", auth: true });
      if (models.status === 200 && models.body?.object === "list") {
        setStatus("ok", "连接和认证正常", `网关可访问，API Key 可用，健康 Provider ${healthyProviders}/${totalProviders}。`);
        addMessage("system", `连接和认证测试通过：健康 Provider ${healthyProviders}/${totalProviders}。`);
        return;
      }

      if (models.status === 401 || models.status === 403) {
        setStatus("error", "API Key 不可用", "这个 Key 没通过 ORIN 网关认证。请使用 ORIN 系统里创建的 API Key，不是模型供应商 Key。");
        addMessage("error", "API Key 验证失败：请使用 ORIN 系统里创建的网关 API Key。");
        return;
      }

      setStatus("warn", "认证结果不确定", `网关连接正常，但 /v1/models 返回 status ${models.status}。`);
      addMessage("system", `网关连接正常，但模型列表验证返回 status ${models.status}。`);
    } else {
      setStatus("error", "连接异常", "公开网关端点没有返回预期结果，请检查后端地址。");
      addMessage("error", "连接测试失败：公开网关端点没有返回预期结果。");
    }
  } catch (error) {
    setStatus("error", "连接失败", friendlyError(error));
    addMessage("error", "连接测试失败：" + friendlyError(error));
  } finally {
    testApiButton.disabled = false;
  }
});

chatForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const text = chatInput.value.trim();
  if (!text) return;

  saveConfig();
  const config = readConfig();
  if (!config.apiKey) {
    setStatus("warn", "需要 API Key", "聊天接口需要左侧填写 API Key。");
    addMessage("error", "请先在左侧填写 API Key，再发送聊天消息。");
    return;
  }

  chatInput.value = "";
  addMessage("user", text);
  const pending = addMessage("assistant", "正在请求统一网关...");
  sendButton.disabled = true;

  try {
    const response = await apiRequest({
      method: "POST",
      path: "/v1/chat/completions",
      auth: true,
      payload: {
        model: config.model,
        messages: [{ role: "user", content: text }],
        temperature: 0.2,
        max_tokens: 512,
        stream: false,
      },
    });

    if (response.status === 200 && Array.isArray(response.body?.choices)) {
      const answer = extractAnswer(response.body);
      updateMessage(pending, answer || "模型返回了响应，但没有找到可展示文本。", {
        status: response.status,
        traceId: headerValue(response.headers, "x-trace-id"),
        elapsedMs: response.elapsedMs,
      });
      setStatus("ok", "聊天成功", "统一网关已返回模型响应。");
    } else if (response.status === 503) {
      updateMessage(pending, "网关可访问，但当前没有健康 Provider 可处理聊天请求。", {
        status: response.status,
        elapsedMs: response.elapsedMs,
      });
      setStatus("warn", "Provider 不可用", "请检查模型供应商配置和健康状态。");
    } else if (response.status === 401 || response.status === 403) {
      updateMessage(pending, "ORIN 网关拒绝了这个 API Key。请确认左侧填写的是 ORIN 系统创建的网关 API Key，不是 SiliconFlow/OpenAI 等供应商 Key。", { status: response.status });
      pending.classList.add("error");
      setStatus("error", "认证失败", "问题在 ORIN 网关认证层：API Key 无效或没有权限。");
    } else {
      const message = response.body?.error?.message || response.body?.message || "聊天请求没有返回预期格式。";
      updateMessage(pending, message, { status: response.status, elapsedMs: response.elapsedMs });
      pending.classList.add("error");
      setStatus("error", "聊天失败", message);
    }
  } catch (error) {
    updateMessage(pending, friendlyError(error));
    pending.classList.add("error");
    setStatus("error", "请求失败", friendlyError(error));
  } finally {
    sendButton.disabled = false;
  }
});

clearButton.addEventListener("click", () => {
  messages.innerHTML = "";
  addMessage("system", "聊天记录已清空。");
});

function readConfig() {
  return {
    baseUrl: baseUrlInput.value.trim() || defaultConfig.baseUrl,
    apiKey: apiKeyInput.value.trim(),
    model: modelInput.value.trim() || defaultConfig.model,
    timeout: Number(timeoutInput.value || defaultConfig.timeout),
  };
}

function saveConfig() {
  const config = readConfig();
  localStorage.setItem(
    "orinChatTesterConfig",
    JSON.stringify({
      baseUrl: config.baseUrl,
      model: config.model,
      timeout: config.timeout,
    }),
  );
}

function hydrateConfig() {
  try {
    const stored = JSON.parse(localStorage.getItem("orinChatTesterConfig") || "{}");
    baseUrlInput.value = stored.baseUrl || defaultConfig.baseUrl;
    modelInput.value = stored.model || defaultConfig.model;
    timeoutInput.value = stored.timeout || defaultConfig.timeout;
  } catch {
    baseUrlInput.value = defaultConfig.baseUrl;
    modelInput.value = defaultConfig.model;
    timeoutInput.value = defaultConfig.timeout;
  }
}

async function apiRequest({ method, path, auth, payload }) {
  const config = readConfig();
  const started = performance.now();

  if (window.location.protocol === "http:" || window.location.protocol === "https:") {
    const response = await fetchWithTimeout("/api/request", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({
        baseUrl: config.baseUrl,
        apiKey: config.apiKey,
        timeout: config.timeout,
        method,
        path,
        payload: payload || null,
        auth,
      }),
    }, config.timeout + 2);
    const body = await response.json();
    if (!response.ok) throw new Error(body?.error || "代理请求失败");
    return {
      status: body.status,
      headers: body.headers || {},
      body: body.body ?? parseMaybeJson(body.rawBody),
      elapsedMs: body.elapsedMs ?? Math.round(performance.now() - started),
    };
  }

  const headers = new Headers({ Accept: "application/json", "X-Trace-Id": "mvp-chat-" + Date.now() });
  const init = { method, headers };
  if (auth && config.apiKey) headers.set("Authorization", "Bearer " + config.apiKey);
  if (payload) {
    headers.set("Content-Type", "application/json");
    init.body = JSON.stringify(payload);
  }

  const response = await fetchWithTimeout(config.baseUrl.replace(/\/+$/, "") + path, init, config.timeout);
  const raw = await response.text();
  return {
    status: response.status,
    headers: response.headers,
    body: parseMaybeJson(raw),
    elapsedMs: Math.round(performance.now() - started),
  };
}

async function fetchWithTimeout(url, init, timeoutSeconds) {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), timeoutSeconds * 1000);
  try {
    return await fetch(url, { ...init, signal: controller.signal });
  } finally {
    window.clearTimeout(timeout);
  }
}

function addMessage(type, text, meta) {
  const node = document.createElement("div");
  node.className = "message " + type;
  node.textContent = text;
  if (meta) appendMeta(node, meta);
  messages.appendChild(node);
  messages.scrollTop = messages.scrollHeight;
  return node;
}

function updateMessage(node, text, meta) {
  node.textContent = text;
  if (meta) appendMeta(node, meta);
  messages.scrollTop = messages.scrollHeight;
}

function appendMeta(node, meta) {
  const clean = Object.entries(meta).filter(([, value]) => value !== undefined && value !== null && value !== "");
  if (clean.length === 0) return;
  const metaNode = document.createElement("span");
  metaNode.className = "meta";
  metaNode.textContent = clean.map(([key, value]) => `${key}: ${value}`).join(" · ");
  node.appendChild(metaNode);
}

function setStatus(state, title, text) {
  statusBox.className = "status-box " + state;
  statusTitle.textContent = title;
  statusText.textContent = text;
}

function extractAnswer(body) {
  const first = body?.choices?.[0];
  return first?.message?.content || first?.text || "";
}

function headerValue(headers, name) {
  if (!headers) return "";
  if (typeof headers.get === "function") return headers.get(name) || "";
  const found = Object.entries(headers).find(([key]) => key.toLowerCase() === name.toLowerCase());
  return found ? found[1] : "";
}

function parseMaybeJson(raw) {
  if (!raw || typeof raw !== "string") return raw ?? null;
  try {
    return JSON.parse(raw);
  } catch {
    return raw;
  }
}

function friendlyError(error) {
  if (error?.name === "AbortError") return "请求超时，请检查服务状态或调大超时秒数。";
  return error?.message || "未知错误";
}
