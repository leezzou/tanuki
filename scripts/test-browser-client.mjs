#!/usr/bin/env node
// 合成回环 HTTP 服务验证产品浏览器客户端，不连接真实浏览器、设备或用户能力配置。
import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { mkdtemp, writeFile, copyFile, rm, realpath } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { randomBytes, randomUUID } from 'node:crypto';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { callBrowser } from '../app/src/main/assets/browser/tanuki-browser.mjs';

const execute = promisify(execFile);
const browserMethods = ['status', 'request_control', 'navigate', 'snapshot', 'click', 'type', 'scroll',
  'screenshot', 'back', 'forward', 'reload', 'tabs', 'new_tab', 'select_tab', 'close_tab', 'find', 'zoom'];

async function fixture(handler, block) {
  const directory = await mkdtemp(join(tmpdir(), 'tanuki-browser-client-'));
  const token = randomBytes(32).toString('base64url');
  const clientId = randomUUID();
  const requests = [];
  const server = createServer(async (request, response) => {
    try {
      assert.equal(request.method, 'POST');
      assert.equal(request.url, '/rpc');
      assert.equal(request.headers.authorization, `Bearer ${token}`);
      const chunks = [];
      for await (const chunk of request) chunks.push(chunk);
      const value = JSON.parse(Buffer.concat(chunks));
      requests.push(value);
      const result = await handler(value, { directory, token, clientId, requests });
      if (result && result.__raw !== undefined) {
        response.writeHead(result.status ?? 200, { 'Content-Type': result.contentType ?? 'application/json' });
        response.end(result.__raw);
      } else {
        response.writeHead(200, { 'Content-Type': 'application/json' });
        response.end(JSON.stringify(result));
      }
    } catch {
      response.writeHead(200, { 'Content-Type': 'application/json' });
      response.end(JSON.stringify({ ok: false, error: { code: 'fixture_failed', message: 'fixture_failed' } }));
    }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const connectionFile = pathToFileURL(join(directory, 'connection.json'));
  const writeConnection = value => writeFile(connectionFile, JSON.stringify(value), { mode: 0o600 });
  await writeConnection({ port: server.address().port, token, clientId });
  try {
    await block({ directory, token, clientId, port: server.address().port, requests, connectionFile, writeConnection });
  } finally {
    server.closeAllConnections?.();
    await new Promise(resolve => server.close(resolve));
    await rm(directory, { recursive: true, force: true });
  }
}

test('成功响应回传 clientId 与 result，默认与显式 clientId 都正确', async () => {
  await fixture(({ method, params }) => ({ ok: true, result: { method, params } }),
    async ({ connectionFile, clientId, requests }) => {
      const result = await callBrowser('status', { probe: 1 }, undefined, connectionFile);
      assert.equal(result.ok, true);
      assert.equal(result.clientId, clientId);
      assert.deepEqual(result.result, { method: 'status', params: { probe: 1 } });
      assert.equal(requests[0].clientId, clientId);
      assert.equal(requests[0].method, 'status');

      const other = randomUUID();
      const explicit = await callBrowser('tabs', {}, other, connectionFile);
      assert.equal(explicit.clientId, other);
      assert.equal(requests[1].clientId, other);
    });
});

test('ok:false 与原生错误对象原样返回，不伪造成功', async () => {
  await fixture(() => ({ ok: false, error: { code: 'permission_required', message: 'permission_required' } }),
    async ({ connectionFile }) => {
      const result = await callBrowser('request_control', {}, undefined, connectionFile);
      assert.equal(result.ok, false);
      assert.equal(result.error.code, 'permission_required');
    });
});

test('无效 JSON 与非 200 响应分别拒绝或保留业务错误', async () => {
  await fixture(({ method }) => {
    if (method === 'status') return { __raw: 'not-json' };
    if (method === 'tabs') {
      return { __raw: JSON.stringify({ ok: false, error: { code: 'unauthorized', message: 'unauthorized' } }), status: 401 };
    }
    return { __raw: '<html>bad gateway</html>', status: 502, contentType: 'text/html' };
  }, async ({ connectionFile }) => {
    await assert.rejects(callBrowser('status', {}, undefined, connectionFile), /invalid_response/);
    const denied = await callBrowser('tabs', {}, undefined, connectionFile);
    assert.equal(denied.ok, false);
    assert.equal(denied.error.code, 'unauthorized');
    await assert.rejects(callBrowser('screenshot', {}, undefined, connectionFile), /invalid_response/);
  });
});

test('超时以 browser_timeout 拒绝且不等待完整 12 秒', async () => {
  const realSetTimeout = globalThis.setTimeout;
  await fixture(async () => {
    await new Promise(resolve => realSetTimeout(resolve, 150));
    return { ok: true, result: {} };
  }, async ({ connectionFile }) => {
    globalThis.setTimeout = (callback, delay, ...args) => realSetTimeout(callback, delay === 12000 ? 20 : delay, ...args);
    try {
      await assert.rejects(callBrowser('status', {}, undefined, connectionFile), /browser_timeout/);
    } finally {
      globalThis.setTimeout = realSetTimeout;
    }
  });
});

test('超过 3 MiB 的响应以 response_too_large 拒绝', async () => {
  await fixture(() => ({ __raw: Buffer.alloc(3 * 1024 * 1024 + 1, 0x61) }),
    async ({ connectionFile }) => {
      await assert.rejects(callBrowser('screenshot', {}, undefined, connectionFile), /response_too_large/);
    });
});

test('方法集与参数大小限制在本地拒绝，未知方法不发出请求', async () => {
  await fixture(() => ({ ok: true, result: {} }), async ({ connectionFile, requests }) => {
    for (const method of browserMethods) await callBrowser(method, {}, undefined, connectionFile);
    assert.deepEqual(requests.map(value => value.method), browserMethods);
    const sent = requests.length;

    await assert.rejects(callBrowser('eval', {}, undefined, connectionFile), /invalid_request/);
    await assert.rejects(callBrowser('status', [], undefined, connectionFile), /invalid_request/);
    await assert.rejects(callBrowser('status', {}, 'not-a-uuid', connectionFile), /invalid_request/);
    await assert.rejects(callBrowser('type', { text: 'x'.repeat(70000) }, undefined, connectionFile), /request_too_large/);
    assert.equal(requests.length, sent);
  });
});

test('能力配置无效时快速失败，CLI 输出不泄露 token 或连接细节', async () => {
  await fixture(() => ({ ok: true, result: { visible: true } }), async ({ directory, token, clientId, port, connectionFile, writeConnection }) => {
    await writeConnection({ port: 1, token: 'short', clientId: randomUUID() });
    await assert.rejects(callBrowser('status', {}, undefined, connectionFile), /browser_unavailable/);
    await writeConnection({ port: 0, token, clientId: randomUUID() });
    await assert.rejects(callBrowser('status', {}, undefined, connectionFile), /browser_unavailable/);
    await writeConnection({ port: 1, token });
    await assert.rejects(callBrowser('status', {}, undefined, connectionFile), /browser_unavailable/);

    const clientPath = join(directory, 'tanuki-browser.mjs');
    await copyFile(new URL('../app/src/main/assets/browser/tanuki-browser.mjs', import.meta.url), clientPath);
    const client = await realpath(clientPath);
    const privateValues = [token, 'connection.json', 'Bearer ', '"token"', '"port"'];

    await writeConnection({ port, token, clientId });
    const output = await execute(process.execPath, [client, 'status']);
    assert.equal(JSON.parse(output.stdout).ok, true);
    for (const privateValue of privateValues) assert.ok(!(output.stdout + output.stderr).includes(privateValue));

    await writeConnection({ port: 1, token });
    let failed;
    try { await execute(process.execPath, [client, 'status']); } catch (error) { failed = error; }
    assert.equal(failed?.code, 1);
    for (const privateValue of privateValues) assert.ok(!(failed.stdout + failed.stderr).includes(privateValue));
  });
});
