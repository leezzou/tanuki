package com.tanuki.web

import org.json.JSONObject

/**
 * DSH 官方 `$events` 流的旁路协议。
 *
 * DSH 的 `/api/remote.mux` 会把 `api-session/status`、`api-session/error`、
 * `approval/request` 和 `user-questions/request` 转发给页面。页面脚本只把这些事件
 * 归一化为会话 id、事件 id 和可见会话 id；不转发消息正文、工具参数、错误原文或凭据。
 */
enum class ChatSignalKind { STATUS, ERROR, APPROVAL, QUESTION }

data class ChatSignal(
    val kind: ChatSignalKind,
    val sessionId: String,
    val running: Boolean? = null,
    val eventId: String? = null,
    val sequence: Int? = null,
    val pageId: String,
    val visibleSessionId: String? = null,
)

object DshChatProtocol {
    /** 与页面脚本中的全局对象名保持一致。 */
    const val JS_OBJECT = "TanukiChatNotify"

    /** 通知点击后打开指定会话的页面函数。 */
    const val OPEN_SESSION_FUNCTION = "__tanukiOpenSession"

    const val MUX_PATH = "/api/remote.mux"
    const val STATUS_EVENT = "api-session/status"
    const val ERROR_EVENT = "api-session/error"
    const val APPROVAL_EVENT = "approval/request"
    const val QUESTION_EVENT = "user-questions/request"

    // 单条桥消息只应包含几个短字段；限制原始长度避免把大消息复制进策略层。
    private const val MAX_MESSAGE_LENGTH = 4096

    fun parse(raw: String?): ChatSignal? {
        if (raw.isNullOrBlank() || raw.length > MAX_MESSAGE_LENGTH) return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val kind = when (json.optString("kind")) {
            "status" -> ChatSignalKind.STATUS
            "error" -> ChatSignalKind.ERROR
            "approval" -> ChatSignalKind.APPROVAL
            "question" -> ChatSignalKind.QUESTION
            else -> return null
        }
        val pageId = json.optString("pageId")
        if (!safeId(pageId, 64)) return null
        val sessionId = json.optString("sessionId")
        if (!safeId(sessionId)) return null
        val visibleSessionId = json.optString("visibleSession").takeIf { it.isNotBlank() }?.also {
            if (!safeId(it)) return null
        }
        return when (kind) {
            ChatSignalKind.STATUS -> {
                if (!json.has("running") || json.opt("running") !is Boolean) return null
                val running = json.getBoolean("running")
                // running=true 的补种事件不需要序号；只有“结束”必须有事件身份用于去重。
                val sequence = if (running) json.optInt("seq", 0).takeIf { it > 0 }
                else positiveSequence(json) ?: return null
                ChatSignal(kind, sessionId, running = running, sequence = sequence,
                    pageId = pageId, visibleSessionId = visibleSessionId)
            }
            ChatSignalKind.ERROR -> {
                val sequence = positiveSequence(json) ?: return null
                ChatSignal(kind, sessionId, sequence = sequence, pageId = pageId, visibleSessionId = visibleSessionId)
            }
            ChatSignalKind.APPROVAL, ChatSignalKind.QUESTION -> {
                val eventId = json.optString("eventId")
                if (!safeId(eventId)) return null
                ChatSignal(kind, sessionId, eventId = eventId, pageId = pageId, visibleSessionId = visibleSessionId)
            }
        }
    }

    private fun positiveSequence(json: JSONObject): Int? =
        json.optInt("seq", 0).takeIf { it > 0 }

    private fun safeId(value: String, maxLength: Int = 128): Boolean =
        value.isNotBlank() && value.length <= maxLength && value.all { it.code in 0x21..0x7e }

    /**
     * 在页面文档开始时安装的旁路脚本。
     *
     * 只包装 `WebSocket.prototype.addEventListener`，不替换 WebSocket 构造器，也不改变
     * 原有消息监听。脚本需要 `window.TanukiChatNotify`（由 WebMessageListener 注入）。
     * 只有在 `/api/remote.mux` 的文本帧中匹配到四个官方事件名时才解析 JSON，避免把聊天
     * 正文或工具输出复制到原生层。
     */
    val SCRIPT: String = """
(function () {
  if (window.__tanukiChatNotifyInstalled || !window.WebSocket) return;
  window.__tanukiChatNotifyInstalled = true;
  var native = window.TanukiChatNotify;
  if (!native || typeof native.postMessage !== 'function') return;
  var pageId = '';
  try {
    if (window.crypto && typeof window.crypto.randomUUID === 'function') pageId = window.crypto.randomUUID();
  } catch (e) {}
  if (!pageId) pageId = 'p' + Date.now().toString(36) + Math.random().toString(36).slice(2, 10);
  var seq = 0;
  function visibleSession() {
    try {
      var el = document.querySelector('[data-conversation-session]');
      return el ? String(el.getAttribute('data-conversation-session') || '') : '';
    } catch (e) { return ''; }
  }
  function post(value) {
    try {
      value.pageId = pageId;
      value.visibleSession = visibleSession();
      native.postMessage(JSON.stringify(value));
    } catch (e) {}
  }
  function seedRunningSessions() {
    try {
      var rows = document.querySelectorAll('[data-row-key^="session:"]');
      for (var i = 0; i < rows.length; i += 1) {
        var row = rows[i];
        var key = String(row.getAttribute('data-row-key') || '');
        var sessionId = key.slice('session:'.length);
        if (!sessionId) continue;
        if (row.querySelector('[data-state="ongoing"]')) {
          post({ kind: 'status', sessionId: sessionId, running: true });
        }
      }
    } catch (e) {}
  }
  function inspect(text) {
    if (typeof text !== 'string') return;
    if (text.indexOf('api-session/status') < 0 && text.indexOf('api-session/error') < 0 &&
        text.indexOf('approval/request') < 0 && text.indexOf('user-questions/request') < 0) return;
    var frame;
    try { frame = JSON.parse(text); } catch (e) { return; }
    if (!frame || frame.type !== 'item' || !frame.value) return;
    var value = frame.value;
    if (value.type === 'emit') {
      if (value.event === 'api-session/status' && value.args && value.args.length >= 2) {
        seq += 1;
        post({ kind: 'status', sessionId: String(value.args[0] || ''), running: value.args[1] === true, seq: seq });
      } else if (value.event === 'api-session/error' && value.args && value.args.length >= 1) {
        seq += 1;
        post({ kind: 'error', sessionId: String(value.args[0] || ''), seq: seq });
      }
    } else if (value.type === 'waterfall' &&
        (value.event === 'approval/request' || value.event === 'user-questions/request')) {
      var sessionId = String(value.agentId || '');
      if (!sessionId) return;
      post({ kind: value.event === 'approval/request' ? 'approval' : 'question',
        sessionId: sessionId, eventId: String(value.eventId || '') });
    }
  }
  var proto = window.WebSocket.prototype;
  if (!proto || proto.__tanukiChatTap) return;
  var originalAdd = proto.addEventListener;
  if (typeof originalAdd !== 'function') return;
  proto.addEventListener = function (type, listener, options) {
    try {
      if (type === 'message' && !this.__tanukiChatTapped &&
          String(this.url || '').indexOf('/api/remote.mux') >= 0) {
        this.__tanukiChatTapped = true;
        originalAdd.call(this, 'message', function (event) {
          try { inspect(event.data); } catch (e) {}
        });
      }
    } catch (e) {}
    return originalAdd.call(this, type, listener, options);
  };
  proto.__tanukiChatTap = true;
  // 页面可能在回合已运行时才加载；从会话行的官方状态点补种 running，避免漏掉本次完成。
  window.setTimeout(seedRunningSessions, 1500);
  window.setTimeout(seedRunningSessions, 5000);
  var openTarget = null;
  function selectedSession() {
    try {
      var el = document.querySelector('[data-conversation-session]');
      return el ? String(el.getAttribute('data-conversation-session') || '') : '';
    } catch (e) { return ''; }
  }
  function expandSidebar() {
    try {
      var frame = document.querySelector('[data-sidebar-collapsed]');
      if (!frame) return true;
      var buttons = frame.querySelectorAll('button[aria-label]');
      for (var i = 0; i < buttons.length; i += 1) {
        var label = String(buttons[i].getAttribute('aria-label') || '');
        if (/sidebar|侧边栏|側邊欄|사이드바|サイドバー/i.test(label)) {
          buttons[i].click();
          return true;
        }
      }
      // 有些 ROM/旧页面只保留内部 class；作为官方 aria-label 的兜底。
      var toggle = frame.querySelector('button[class*="_toggle"]');
      if (toggle) { toggle.click(); return true; }
      return false;
    } catch (e) { return false; }
  }
  function findSearchInput() {
    try {
      var inputs = document.querySelectorAll('input[type="text"][placeholder]');
      for (var i = 0; i < inputs.length; i += 1) {
        // 官方搜索框 maxLength=500；用它避开重命名等临时文本输入。
        if (Number(inputs[i].maxLength) === 500) return inputs[i];
      }
    } catch (e) {}
    return null;
  }
  function clearSearchFilter(target) {
    try {
      if (target.searchCleared) return false;
      // 侧栏还没展开时搜索框不在 DOM，等下一次重试。
      if (document.querySelector('[data-sidebar-collapsed]')) return false;
      var buttons = document.querySelectorAll('button[aria-label]');
      for (var i = 0; i < buttons.length; i += 1) {
        var label = String(buttons[i].getAttribute('aria-label') || '');
        if (/清除搜索|清空搜索|clear search|検索をクリア|검색(어)? 지우기/i.test(label)) {
          buttons[i].click();
          target.searchCleared = true;
          return true;
        }
      }
      var input = findSearchInput();
      if (input && input.value) {
        // 兜底走输入框自己的 React onChange，而不是直接改 DOM 值。
        var descriptor = window.HTMLInputElement && Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
        if (descriptor && descriptor.set) {
          descriptor.set.call(input, '');
          input.dispatchEvent(new Event('input', { bubbles: true }));
          target.searchCleared = true;
          return true;
        }
        return false;
      }
      // 没有搜索过滤，或目标已不需要再清。
      target.searchCleared = true;
      return false;
    } catch (e) { return false; }
  }
  function expandCollapsedWorkspaces(target) {
    try {
      var groups = document.querySelectorAll('[data-row-key^="workspace:"][aria-expanded="false"]');
      for (var i = 0; i < groups.length; i += 1) {
        var key = String(groups[i].getAttribute('data-row-key') || '');
        if (!key || target.groups[key]) continue;
        target.groups[key] = true;
        groups[i].click();
      }
    } catch (e) {}
  }
  function findSessionRow(id) {
    try {
      return document.querySelector('[data-row-key="session:' + id + '"]');
    } catch (e) { return null; }
  }
  window.__tanukiOpenSession = function (id) {
    try {
      if (typeof id !== 'string' || !/^[A-Za-z0-9_.:-]{1,128}$/.test(id)) return false;
      if (selectedSession() === id) { openTarget = null; return true; }
      if (!openTarget || openTarget.id !== id) {
        openTarget = { id: id, sidebarToggled: false, searchCleared: false, groups: {}, rowClicked: false };
      }
      // 窄屏/初始收起时会话列表不在 DOM：先点官方展开入口，且只点一次，避免动画中反复切换。
      if (!openTarget.sidebarToggled) openTarget.sidebarToggled = expandSidebar();
      // 侧栏搜索会渲染 SearchResults 替换会话树；先走官方清除入口，再展开工作区分组。
      clearSearchFilter(openTarget);
      // 目标可能在收起的工作区分组；每个分组只展开一次，避免异步渲染期间反复折叠。
      expandCollapsedWorkspaces(openTarget);
      var row = findSessionRow(id);
      if (!row) return false;
      if (!openTarget.rowClicked) {
        openTarget.rowClicked = true;
        row.click();
      }
      // React 选择是异步提交的；只有下一次调用确认实际选中后才报告成功。
      return selectedSession() === id;
    } catch (e) { return false; }
  };
})();
""".trimIndent()
}
