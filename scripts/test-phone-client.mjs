#!/usr/bin/env node
// 合成本机 HTTP 服务验证产品手机文件客户端，不读取真实连接、手机数据或凭据。
import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { mkdtemp, writeFile, readFile, rm, access, copyFile, appendFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { randomBytes } from 'node:crypto';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { callPhone, readToFile, writeFromFile } from '../app/src/main/assets/phone/tanuki-phone.mjs';

const execute = promisify(execFile);
const phoneMethods = ['status', 'stat', 'list', 'read', 'write_begin', 'write_chunk', 'write_commit',
  'write_cancel', 'mkdir', 'rename', 'delete'];
async function fixture(handler, block) {
  const directory = await mkdtemp(join(tmpdir(), 'tanuki-phone-client-'));
  const token = randomBytes(32).toString('base64url');
  const requests = [];
  const server = createServer(async (request, response) => {
    try {
      assert.equal(request.method, 'POST'); assert.equal(request.url, '/rpc');
      assert.equal(request.headers.authorization, `Bearer ${token}`);
      const chunks = [];
      for await (const chunk of request) chunks.push(chunk);
      const value = JSON.parse(Buffer.concat(chunks));
      requests.push(value);
      const result = await handler(value, { directory, token, requests });
      if (result && result.__raw !== undefined) {
        response.writeHead(result.status ?? 200, { 'Content-Type': result.contentType ?? 'application/json' });
        response.end(result.__raw);
      } else {
        response.writeHead(200, { 'Content-Type': 'application/json' });
        response.end(JSON.stringify(result));
      }
    } catch {
      response.writeHead(200, { 'Content-Type': 'application/json' });
      response.end(JSON.stringify({ ok: false, error: 'fixture_failed' }));
    }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const connectionFile = pathToFileURL(join(directory, 'connection.json'));
  await writeFile(connectionFile, JSON.stringify({ port: server.address().port, token }), { mode: 0o600 });
  try { await block({ directory, token, requests, connectionFile }); }
  finally { await new Promise(resolve => server.close(resolve)); await rm(directory, { recursive: true, force: true }); }
}
const ok = result => ({ ok: true, result });
function page(bytes, offset, length, revision = 'synthetic-revision') {
  const end = Math.min(offset + length, bytes.length);
  return ok({ dataBase64: bytes.subarray(offset, end).toString('base64'), offset,
    nextOffset: end === bytes.length ? null : end, totalBytes: bytes.length, revision });
}

test('控制与长IO请求均有界，CLI状态及配置错误不输出能力描述', async () => {
  await fixture(async () => ok({ readAllowed: true, writeAllowed: false, roots: [{ id: 'primary', path: '/phone/primary', label: 'fixture' }] }),
    async ({ directory, token, connectionFile }) => {
      const realSetTimeout = globalThis.setTimeout;
      const deadlines = [];
      try {
        globalThis.setTimeout = (callback, delay, ...args) => {
          if (delay === 20000 || delay === 120000) deadlines.push(delay);
          return realSetTimeout(callback, delay, ...args);
        };
        const result = await callPhone('status', {}, connectionFile);
        assert.equal(result.result.writeAllowed, false);
        for (const method of ['stat', 'list', 'read', 'write_begin', 'write_commit', 'rename', 'delete']) await callPhone(method, {}, connectionFile);
        for (const method of ['write_chunk', 'write_cancel', 'mkdir']) await callPhone(method, {}, connectionFile);
      } finally { globalThis.setTimeout = realSetTimeout; }
      assert.deepEqual(deadlines, [20000, ...Array(7).fill(120000), ...Array(3).fill(20000)]);
      const client = join(directory, 'tanuki-phone.mjs');
      await copyFile(new URL('../app/src/main/assets/phone/tanuki-phone.mjs', import.meta.url), client);
      const output = await execute(process.execPath, [client, 'status']);
      assert.equal(JSON.parse(output.stdout).result.writeAllowed, false);
      const publicOutput = output.stdout + output.stderr;
      for (const privateValue of [token, 'connection.json', 'Bearer ', '"token"', '"port"']) assert.ok(!publicOutput.includes(privateValue));
      await writeFile(connectionFile, JSON.stringify({ port: 'invalid', token }));
      let failed;
      try { await execute(process.execPath, [client, 'status']); } catch (error) { failed = error; }
      assert.equal(failed?.code, 1);
      assert.equal(JSON.parse(failed.stderr).error, 'phone_unavailable');
      for (const privateValue of [token, 'connection.json', 'Bearer ', '"token"', '"port"']) assert.ok(!(failed.stdout + failed.stderr).includes(privateValue));
    });
});

test('read-file按base64分页逐字节写入新目标并报告真实进度', async () => {
  const bytes = Buffer.alloc(3 * 65536 + 19).map((_, index) => index % 251);
  await fixture(async ({ method, params }) => {
    assert.equal(method, 'read'); assert.equal(params.path, '/phone/primary/fixture.bin');
    return page(bytes, params.offset, params.length);
  }, async ({ directory, connectionFile, requests }) => {
    const destination = join(directory, 'download.bin'); const progress = [];
    const result = await readToFile('/phone/primary/fixture.bin', destination, { connectionFile, progress: value => progress.push(value) });
    assert.deepEqual(await readFile(destination), bytes);
    assert.equal(result.bytes, bytes.length); assert.equal(requests.length, 4);
    assert.equal(progress.at(-1).bytes, bytes.length); assert.equal(progress.at(-1).total, bytes.length);
    assert.ok(progress.slice(1).every(value => value.elapsedMs >= 0));
  });
});

test('read-file不覆盖本地已有目标', async () => {
  await fixture(async ({ params }) => page(Buffer.from('new'), params.offset, params.length), async ({ directory, connectionFile }) => {
    const destination = join(directory, 'existing.txt'); await writeFile(destination, 'original');
    await assert.rejects(readToFile('/phone/primary/a.txt', destination, { connectionFile }), { code: 'local_destination_unavailable' });
    assert.equal(await readFile(destination, 'utf8'), 'original');
  });
});

test('read-file跨页revision变化时撤销仅本次创建的部分目标', async () => {
  const bytes = Buffer.alloc(80000, 0x61);
  await fixture(async ({ params }) => page(bytes, params.offset, params.length, params.offset === 0 ? 'first' : 'changed'),
    async ({ directory, connectionFile }) => {
      const destination = join(directory, 'partial.bin');
      await assert.rejects(readToFile('/phone/primary/a.bin', destination, { connectionFile }), { code: 'file_changed' });
      await assert.rejects(access(destination), { code: 'ENOENT' });
    });
});

test('read-file拒绝不能前进的分页', async () => {
  await fixture(async () => ok({ dataBase64: 'YQ==', offset: 0, nextOffset: 0, totalBytes: 2, revision: 'same' }),
    async ({ directory, connectionFile }) => {
      await assert.rejects(readToFile('/phone/primary/a', join(directory, 'bad.bin'), { connectionFile }), { code: 'invalid_response' });
    });
});

test('未获原生写许可时停止且不发chunk或commit', async () => {
  await fixture(async () => ({ ok: false, error: 'write_permission_required' }), async ({ directory, connectionFile, requests }) => {
    const source = join(directory, 'source.txt'); await writeFile(source, 'fixture');
    await assert.rejects(writeFromFile(source, '/phone/primary/output.txt', { connectionFile }), { code: 'write_permission_required' });
    assert.deepEqual(requests.map(value => value.method), ['write_begin']);
  });
});

test('write-file按事务分块并传显式expectedRevision，提交字节与本地一致', async () => {
  const bytes = Buffer.alloc(140000).map((_, index) => index % 241);
  const received = [];
  await fixture(async ({ method, params }) => {
    if (method === 'write_begin') { assert.equal(params.expectedRevision, 'reviewed-revision'); return ok({ id: 'transaction-1', received: 0, size: bytes.length }); }
    if (method === 'write_chunk') {
      assert.equal(params.id, 'transaction-1'); assert.equal(params.offset, Buffer.concat(received).length);
      const chunk = Buffer.from(params.dataBase64, 'base64'); assert.ok(chunk.length <= 65536); received.push(chunk);
      return ok({ received: Buffer.concat(received).length, size: bytes.length });
    }
    assert.equal(method, 'write_commit'); return ok({ revision: 'committed-revision' });
  }, async ({ directory, connectionFile, requests }) => {
    const source = join(directory, 'source.bin'); await writeFile(source, bytes);
    const result = await writeFromFile(source, '/phone/primary/output.bin', { expectedRevision: 'reviewed-revision', connectionFile });
    assert.deepEqual(Buffer.concat(received), bytes); assert.equal(result.bytes, bytes.length);
    assert.equal(result.revision, 'committed-revision'); assert.equal(requests.at(-1).method, 'write_commit');
  });
});

test('本地源传输中变化时cancel事务且不commit', async () => {
  const size = 90000;
  await fixture(async ({ method }, { directory, requests }) => {
    if (method === 'write_begin') return ok({ id: 'changed-source', received: 0, size });
    if (method === 'write_cancel') return ok({ cancelled: true });
    if (requests.filter(value => value.method === 'write_chunk').length === 1) await appendFile(join(directory, 'source.bin'), 'changed');
    return ok({ received: Math.min(65536 * requests.filter(value => value.method === 'write_chunk').length, size), size });
  }, async ({ directory, connectionFile, requests }) => {
    const source = join(directory, 'source.bin'); await writeFile(source, Buffer.alloc(size, 0x61));
    await assert.rejects(writeFromFile(source, '/phone/primary/output.bin', { connectionFile }), { code: 'local_source_changed' });
    assert.equal(requests.at(-1).method, 'write_cancel'); assert.ok(requests.every(value => value.method !== 'write_commit'));
  });
});

test('写入途中撤权时cancel，不循环申请或提交', async () => {
  await fixture(async ({ method }) => {
    if (method === 'write_begin') return ok({ id: 'revoked-write', received: 0, size: 70000 });
    if (method === 'write_cancel') return ok({ cancelled: true });
    return { ok: false, error: 'write_permission_required' };
  }, async ({ directory, connectionFile, requests }) => {
    const source = join(directory, 'source.bin'); await writeFile(source, Buffer.alloc(70000, 0x61));
    await assert.rejects(writeFromFile(source, '/phone/primary/output.bin', { connectionFile }), { code: 'write_permission_required' });
    assert.deepEqual(requests.map(value => value.method), ['write_begin', 'write_chunk', 'write_cancel']);
  });
});

test('响应反射合成token时拒绝且错误不包含能力值', async () => {
  await fixture(async (_, { token }) => ok({ token }), async ({ connectionFile, token }) => {
    await assert.rejects(callPhone('status', {}, connectionFile), error => error.code === 'invalid_response' && !error.message.includes(token));
  });
});

test('空文件仍完成begin和commit，不发送虚构数据块', async () => {
  await fixture(async ({ method }) => method === 'write_begin' ? ok({ id: 'empty', received: 0, size: 0 }) : ok({ revision: 'empty-file' }),
    async ({ directory, connectionFile, requests }) => {
      const source = join(directory, 'empty.bin'); await writeFile(source, '');
      const result = await writeFromFile(source, '/phone/primary/empty.bin', { connectionFile });
      assert.equal(result.bytes, 0);
      assert.deepEqual(requests.map(value => value.method), ['write_begin', 'write_commit']);
    });
});

test('远端提交版本冲突时cancel且不自动刷新revision重试', async () => {
  await fixture(async ({ method }) => {
    if (method === 'write_begin') return ok({ id: 'conflict', received: 0, size: 3 });
    if (method === 'write_chunk') return ok({ received: 3, size: 3 });
    if (method === 'write_cancel') return ok({ cancelled: true });
    return { ok: false, error: 'file_changed' };
  }, async ({ directory, connectionFile, requests }) => {
    const source = join(directory, 'source.txt'); await writeFile(source, 'new');
    await assert.rejects(writeFromFile(source, '/phone/primary/old.txt', { expectedRevision: 'reviewed', connectionFile }), { code: 'file_changed' });
    assert.deepEqual(requests.map(value => value.method), ['write_begin', 'write_chunk', 'write_commit', 'write_cancel']);
  });
});

test('分页期间配置改变不会静默切换到另一能力连接', async () => {
  const bytes = Buffer.alloc(80000, 0x61);
  await fixture(async ({ params }, { directory }) => {
    if (params.offset === 0) await writeFile(join(directory, 'connection.json'), JSON.stringify({ port: 1, token: 'invalid' }));
    return page(bytes, params.offset, params.length);
  }, async ({ directory, connectionFile, requests }) => {
    const destination = join(directory, 'fixed-session.bin');
    await readToFile('/phone/primary/a.bin', destination, { connectionFile });
    assert.deepEqual(await readFile(destination), bytes);
    assert.equal(requests.length, 2);
  });
});

test('非200、无效JSON与缺失ok字段统一为invalid_response', async () => {
  await fixture(({ method }) => {
    if (method === 'status') return { __raw: 'not-json' };
    if (method === 'list') return { __raw: JSON.stringify({ ok: true, result: {} }), status: 500 };
    return { __raw: JSON.stringify({ result: {} }) };
  }, async ({ connectionFile }) => {
    await assert.rejects(callPhone('status', {}, connectionFile), { code: 'invalid_response' });
    await assert.rejects(callPhone('list', {}, connectionFile), { code: 'invalid_response' });
    await assert.rejects(callPhone('stat', {}, connectionFile), { code: 'invalid_response' });
  });
});

test('超过1MiB的响应以response_too_large拒绝', async () => {
  await fixture(() => ({ __raw: Buffer.alloc(1048576 + 1, 0x61) }), async ({ connectionFile }) => {
    await assert.rejects(callPhone('status', {}, connectionFile), { code: 'response_too_large' });
  });
});

test('长IO超时以phone_timeout拒绝且不等待完整120秒', async () => {
  const realSetTimeout = globalThis.setTimeout;
  await fixture(async () => {
    await new Promise(resolve => realSetTimeout(resolve, 150));
    return ok({});
  }, async ({ connectionFile }) => {
    globalThis.setTimeout = (callback, delay, ...args) => realSetTimeout(callback,
      delay === 20000 || delay === 120000 ? 20 : delay, ...args);
    try {
      await assert.rejects(callPhone('stat', {}, connectionFile), { code: 'phone_timeout' });
    } finally {
      globalThis.setTimeout = realSetTimeout;
    }
  });
});

test('方法集与请求大小限制在本地拒绝，未知方法不发出请求', async () => {
  await fixture(() => ok({}), async ({ connectionFile, requests }) => {
    for (const method of phoneMethods) await callPhone(method, {}, connectionFile);
    assert.deepEqual(requests.map(value => value.method), phoneMethods);
    const sent = requests.length;

    await assert.rejects(callPhone('eval', {}, connectionFile), { code: 'invalid_request' });
    await assert.rejects(callPhone('status', [], connectionFile), { code: 'invalid_request' });
    await assert.rejects(callPhone('read', { data: 'x'.repeat(300000) }, connectionFile), { code: 'request_too_large' });
    assert.equal(requests.length, sent);
  });
});

test('原生错误码不符合短码格式时拒绝为invalid_response', async () => {
  await fixture(() => ({ ok: false, error: 'Bad Code' }), async ({ connectionFile }) => {
    await assert.rejects(callPhone('status', {}, connectionFile), { code: 'invalid_response' });
  });
});
