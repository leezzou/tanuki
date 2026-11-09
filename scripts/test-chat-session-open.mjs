#!/usr/bin/env node
// 回归：侧栏初始收起且保留非空搜索时，通知点击仍能展开侧栏、清除搜索过滤、
// 展开工作区分组，并在确认实际选中目标后才报告成功。脚本直接执行页面同一份 JS。
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'

const source = readFileSync('app/src/main/kotlin/com/tanuki/web/DshChatProtocol.kt', 'utf8')
const start = source.indexOf('val SCRIPT: String = """')
if (start < 0) throw new Error('DshChatProtocol.SCRIPT not found')
const bodyStart = start + 'val SCRIPT: String = """'.length
const end = source.indexOf('""".trimIndent()', bodyStart)
if (end < 0) throw new Error('DshChatProtocol.SCRIPT end not found')
const pageScript = source.slice(bodyStart, end)

function createHarness({ searchQuery: initialQuery = 'session-a' } = {}) {
  let collapsed = true
  let selected = 'session-old'
  let query = initialQuery
  let pendingQuery = null
  let pendingSelection = null
  let toggleClicks = 0
  let clearClicks = 0
  let rowClicks = 0
  const groupClicks = new Map()
  const groups = [
    { key: 'workspace:a', expanded: false },
    { key: 'workspace:b', expanded: false },
  ]
  const pendingGroups = new Set()

  const element = (attrs, onClick = () => {}) => ({
    getAttribute: (name) => Object.hasOwn(attrs, name) ? String(attrs[name]) : null,
    click: onClick,
  })

  const toggle = element({ 'aria-label': '展开侧边栏' }, () => { collapsed = false; toggleClicks += 1 })
  const clear = element({ 'aria-label': '清除搜索' }, () => {
    pendingQuery = ''
    clearClicks += 1
  })
  const searchInput = {
    value: initialQuery,
    maxLength: 500,
    dispatchEvent: () => {},
  }
  const frame = {
    querySelectorAll: (selector) => selector === 'button[aria-label]' ? [toggle] : [],
    querySelector: (selector) => selector === 'button[class*="_toggle"]' ? toggle : null,
  }

  const document = {
    querySelector(selector) {
      if (selector === '[data-conversation-session]') {
        return selected ? element({ 'data-conversation-session': selected }) : null
      }
      if (selector === '[data-sidebar-collapsed]') return collapsed ? frame : null
      if (selector === 'input[type="text"][placeholder]') return searchInput
      const session = /^\[data-row-key="session:(.+)"\]$/.exec(selector)
      if (session) {
        if (collapsed || query !== '' || !groups.some((group) => group.expanded)) return null
        return element({ 'data-row-key': `session:${session[1]}` }, () => {
          rowClicks += 1
          pendingSelection = session[1]
        })
      }
      return null
    },
    querySelectorAll(selector) {
      if (selector === 'button[aria-label]') return query === '' ? [] : [clear]
      if (selector === 'input[type="text"][placeholder]') return [searchInput]
      if (selector !== '[data-row-key^="workspace:"][aria-expanded="false"]' || collapsed || query !== '') return []
      return groups.filter((group) => !group.expanded).map((group) => element({
        'data-row-key': `workspace:${group.key}`,
        'aria-expanded': 'false',
      }, () => {
        if (pendingGroups.has(group.key)) return
        pendingGroups.add(group.key)
        groupClicks.set(group.key, (groupClicks.get(group.key) ?? 0) + 1)
      }))
    },
  }

  class FakeWebSocket {}
  FakeWebSocket.prototype.addEventListener = function () {}
  const window = {
    WebSocket: FakeWebSocket,
    crypto: { randomUUID: () => 'page-test' },
    setTimeout: () => {},
    TanukiChatNotify: { postMessage: () => {} },
  }
  const context = {
    window, document, Date, Math, JSON, String, RegExp, Error, Number,
    Event: class FakeEvent { constructor(type) { this.type = type } },
  }
  vm.createContext(context)
  vm.runInContext(pageScript, context)

  return {
    open: (id) => window.__tanukiOpenSession(id),
    flush: () => {
      if (pendingQuery !== null) { query = pendingQuery; pendingQuery = null }
      for (const key of pendingGroups) {
        const group = groups.find((candidate) => candidate.key === key)
        if (group) group.expanded = true
      }
      pendingGroups.clear()
      if (pendingSelection !== null) { selected = pendingSelection; pendingSelection = null }
    },
    select: (id) => { selected = id },
    state: () => ({
      collapsed, selected, query, toggleClicks, clearClicks, rowClicks,
      groupClicks: Object.fromEntries(groupClicks),
    }),
  }
}

// 1) 侧栏收起 + 非空搜索 + 当前为另一会话：
//    第一次只展开侧栏并清除搜索，不能谎报成功；搜索/分组渲染需后续重试。
const harness = createHarness()
assert.equal(harness.open('session-target'), false, '搜索/React 提交前不能返回成功')
let state = harness.state()
assert.equal(state.collapsed, false, '应先展开侧栏')
assert.equal(state.toggleClicks, 1, '展开按钮只应点击一次')
assert.equal(state.clearClicks, 1, '应先通过官方入口清除搜索过滤')
assert.equal(state.query, 'session-a', '搜索清理由 React 异步提交，本次不谎报已清')
assert.equal(state.rowClicks, 0)

// 2) 搜索清除生效后展开分组；分组渲染完成前仍不能成功。
harness.flush()
assert.equal(harness.open('session-target'), false, '分组提交前不能返回成功')
state = harness.state()
assert.equal(state.query, '', '搜索过滤已解除')
assert.deepEqual(state.groupClicks, { 'workspace:a': 1, 'workspace:b': 1 }, '折叠分组各展开一次')
assert.equal(state.rowClicks, 0)

// 3) 分组渲染后点击目标行；选中尚未提交时仍为 false，且不能重复展开/点击。
harness.flush()
assert.equal(harness.open('session-target'), false, '选中提交前不能返回成功')
state = harness.state()
assert.equal(state.rowClicks, 1, '找到目标行后点击一次')
assert.equal(state.toggleClicks, 1)
assert.equal(state.clearClicks, 1)
assert.deepEqual(state.groupClicks, { 'workspace:a': 1, 'workspace:b': 1 })

// 4) 只有实际选中目标后才返回 true。
harness.flush()
assert.equal(harness.open('session-target'), true, '确认 data-conversation-session 后才报告成功')
state = harness.state()
assert.equal(state.selected, 'session-target')
assert.equal(state.rowClicks, 1, '成功后不应再次点击')

// 5) 已在目标会话时直接成功，不改变侧栏/搜索/行。
const current = createHarness({ searchQuery: '' })
current.select('session-target')
const before = current.state()
assert.equal(current.open('session-target'), true)
assert.deepEqual(current.state(), before)

// 6) 非法 id 不触发展开、清理或点击。
const invalid = createHarness()
assert.equal(invalid.open('bad id'), false)
assert.equal(invalid.state().toggleClicks, 0)
assert.equal(invalid.state().clearClicks, 0)

console.log('CHAT_SESSION_OPEN_TEST_OK')
