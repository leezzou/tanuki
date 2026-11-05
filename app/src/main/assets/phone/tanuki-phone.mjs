#!/usr/bin/env node
// 手机文件受控客户端：能力配置只在内存中使用，不输出令牌或原始传输异常。
import { constants } from 'node:fs';
import { readFile, open, lstat, unlink, realpath } from 'node:fs/promises';
import { request } from 'node:http';
import { fileURLToPath } from 'node:url';

const methods = new Set(['status', 'stat', 'list', 'read', 'write_begin', 'write_chunk', 'write_commit', 'write_cancel', 'mkdir', 'rename', 'delete']);
// 首次强哈希、目录扫描和原子提交可能处理大文件；保留有界长超时，短控制请求仍快速失败。
const longIoMethods = new Set(['stat', 'list', 'read', 'write_begin', 'write_commit', 'rename', 'delete']);
const defaultConnection = new URL('./connection.json', import.meta.url);
const chunkSize = 64 * 1024;
export class PhoneFileError extends Error {
  constructor(code) { super(code); this.code = code; }
}
const fail = code => { throw new PhoneFileError(code); };
const integer = value => Number.isSafeInteger(value) && value >= 0;

async function caller(connectionFile = defaultConnection) {
  let connection;
  try { connection = JSON.parse(await readFile(connectionFile, 'utf8')); } catch { fail('phone_unavailable'); }
  if (!Number.isInteger(connection?.port) || connection.port < 1 || connection.port > 65535 ||
      !/^[A-Za-z0-9_-]{43}$/.test(connection.token ?? '')) fail('phone_unavailable');
  // 一次分块传输固定当前能力，重启连接不能把旧事务静默切到新会话。
  return async (method, params = {}) => {
    if (!methods.has(method) || !params || Array.isArray(params) || typeof params !== 'object') fail('invalid_request');
    const body = Buffer.from(JSON.stringify({ method, params }));
    if (body.length > 300000) fail('request_too_large');
    return await new Promise((resolve, reject) => {
      const req = request({ host: '127.0.0.1', port: connection.port, path: '/rpc', method: 'POST', agent: false,
        headers: { Authorization: `Bearer ${connection.token}`, 'Content-Type': 'application/json', 'Content-Length': body.length } }, response => {
        const chunks = []; let received = 0;
        response.on('data', chunk => {
          received += chunk.length;
          if (received > 1048576) req.destroy(new PhoneFileError('response_too_large'));
          else chunks.push(chunk);
        });
        response.on('error', () => reject(new PhoneFileError('phone_unavailable')));
        response.on('end', () => {
          try {
            if (response.statusCode !== 200) fail('invalid_response');
            const text = Buffer.concat(chunks).toString('utf8');
            if (text.includes(connection.token)) fail('invalid_response');
            const value = JSON.parse(text);
            if (!value || typeof value.ok !== 'boolean') fail('invalid_response');
            if (!value.ok && (typeof value.error !== 'string' || !/^[a-z_]{1,64}$/.test(value.error))) fail('invalid_response');
            if (value.ok && (!value.result || Array.isArray(value.result) || typeof value.result !== 'object')) fail('invalid_response');
            resolve(value.ok ? { ok: true, result: value.result } : { ok: false, error: value.error });
          } catch { reject(new PhoneFileError('invalid_response')); }
        });
      });
      const timer = setTimeout(() => req.destroy(new PhoneFileError('phone_timeout')), longIoMethods.has(method) ? 120000 : 20000);
      req.on('close', () => clearTimeout(timer));
      req.on('error', error => reject(new PhoneFileError(error instanceof PhoneFileError ? error.code : 'phone_unavailable')));
      req.end(body);
    });
  };
}

export async function callPhone(method, params = {}, connectionFile = defaultConnection) {
  return await (await caller(connectionFile))(method, params);
}

async function resultOf(rpc, method, params) {
  const response = await rpc(method, params);
  if (!response.ok) fail(response.error);
  return response.result;
}

function decodePage(page, offset, previous) {
  if (!page || page.offset !== offset || !integer(page.totalBytes) ||
      typeof page.revision !== 'string' || page.revision.length < 1 || page.revision.length > 256 ||
      typeof page.dataBase64 !== 'string') fail('invalid_response');
  const bytes = Buffer.from(page.dataBase64, 'base64');
  if (bytes.toString('base64') !== page.dataBase64 || bytes.length > chunkSize || offset + bytes.length > page.totalBytes) fail('invalid_response');
  if (previous && (page.revision !== previous.revision || page.totalBytes !== previous.totalBytes)) fail('file_changed');
  if (page.nextOffset === null) {
    if (offset + bytes.length !== page.totalBytes) fail('invalid_response');
  } else if (!integer(page.nextOffset) || page.nextOffset !== offset + bytes.length || page.nextOffset <= offset) fail('invalid_response');
  return bytes;
}

export async function readToFile(path, destination, { connectionFile = defaultConnection, progress = () => {} } = {}) {
  const rpc = await caller(connectionFile);
  const started = Date.now();
  let handle, identity, received = 0, offset = 0, baseline;
  try {
    progress({ stage: '读取手机文件', bytes: 0, total: null, elapsedMs: 0 });
    while (true) {
      const page = await resultOf(rpc, 'read', { path, offset, length: chunkSize });
      const bytes = decodePage(page, offset, baseline);
      baseline ??= { revision: page.revision, totalBytes: page.totalBytes };
      if (!handle) {
        try { handle = await open(destination, 'wx', 0o600); } catch { fail('local_destination_unavailable'); }
        identity = await handle.stat();
      }
      let written = 0;
      while (written < bytes.length) {
        const part = await handle.write(bytes, written, bytes.length - written, received + written);
        if (part.bytesWritten <= 0) fail('local_write_failed');
        written += part.bytesWritten;
      }
      received += bytes.length;
      progress({ stage: '读取手机文件', bytes: received, total: baseline.totalBytes, elapsedMs: Date.now() - started });
      if (page.nextOffset === null) break;
      offset = page.nextOffset;
    }
    await handle.sync(); await handle.close(); handle = null;
    return { path, destination, bytes: received, revision: baseline.revision };
  } catch (error) {
    if (handle) await handle.close().catch(() => {});
    // 只清理本次创建且身份未被替换的目标，不能删除用户已有文件。
    if (identity) {
      const current = await lstat(destination).catch(() => null);
      if (current?.dev === identity.dev && current?.ino === identity.ino) await unlink(destination).catch(() => {});
    }
    throw error instanceof PhoneFileError ? error : new PhoneFileError('local_write_failed');
  }
}

export async function writeFromFile(source, path, { expectedRevision, connectionFile = defaultConnection, progress = () => {} } = {}) {
  const rpc = await caller(connectionFile);
  const started = Date.now();
  let handle, transaction;
  try {
    try { handle = await open(source, constants.O_RDONLY | constants.O_NOFOLLOW); } catch { fail('local_source_unavailable'); }
    const before = await handle.stat();
    if (!before.isFile() || !integer(before.size)) fail('local_source_unavailable');
    if (expectedRevision !== undefined && (typeof expectedRevision !== 'string' || expectedRevision.length < 1 || expectedRevision.length > 256)) fail('invalid_request');
    progress({ stage: '等待原生写入许可', bytes: 0, total: before.size, elapsedMs: 0 });
    const begin = await resultOf(rpc, 'write_begin', { path, size: before.size, ...(expectedRevision === undefined ? {} : { expectedRevision }) });
    if (typeof begin.id !== 'string' || begin.id.length < 1 || begin.id.length > 128) fail('invalid_response');
    transaction = begin.id;
    if (begin.received !== 0 || begin.size !== before.size) fail('invalid_response');
    const buffer = Buffer.alloc(chunkSize);
    let received = 0;
    while (received < before.size) {
      const count = (await handle.read(buffer, 0, Math.min(buffer.length, before.size - received), received)).bytesRead;
      if (count <= 0) fail('local_source_changed');
      const result = await resultOf(rpc, 'write_chunk', { id: transaction, offset: received, dataBase64: buffer.subarray(0, count).toString('base64') });
      received += count;
      if (result.received !== received || result.size !== before.size) fail('invalid_response');
      progress({ stage: '暂存手机文件', bytes: received, total: before.size, elapsedMs: Date.now() - started });
    }
    const after = await handle.stat();
    if (before.size !== after.size || before.dev !== after.dev || before.ino !== after.ino || before.mtimeMs !== after.mtimeMs || before.ctimeMs !== after.ctimeMs) fail('local_source_changed');
    progress({ stage: '提交手机文件', bytes: received, total: before.size, elapsedMs: Date.now() - started });
    const result = await resultOf(rpc, 'write_commit', { id: transaction });
    transaction = undefined;
    return { ...result, path, bytes: received };
  } catch (error) {
    if (transaction) await resultOf(rpc, 'write_cancel', { id: transaction }).catch(() => {});
    throw error instanceof PhoneFileError ? error : new PhoneFileError('local_read_failed');
  } finally { if (handle) await handle.close().catch(() => {}); }
}

// 宿主和客体都可能存在 /var 等路径别名，直接执行判定需比较真实入口。
if (process.argv[1] && await realpath(process.argv[1]).catch(() => null) === fileURLToPath(import.meta.url)) {
  const [method, ...args] = process.argv.slice(2);
  let published = 0;
  const progress = value => {
    if (Date.now() - published >= 200 || value.bytes === value.total) {
      process.stderr.write(JSON.stringify(value) + '\n'); published = Date.now();
    }
  };
  try {
    let response;
    if (method === 'read-file' && args.length === 2) response = { ok: true, result: await readToFile(args[0], args[1], { progress }) };
    else if (method === 'write-file' && (args.length === 2 || (args.length === 4 && args[2] === '--expected-revision'))) {
      response = { ok: true, result: await writeFromFile(args[0], args[1], { expectedRevision: args[3], progress }) };
    } else {
      if (!methods.has(method) || args.length > 1) fail('usage');
      response = await callPhone(method, JSON.parse(args[0] ?? '{}'));
    }
    process.stdout.write(JSON.stringify(response) + '\n');
    if (!response.ok) process.exitCode = 1;
  } catch (error) {
    const code = error instanceof PhoneFileError ? error.code : 'invalid_request';
    process.stderr.write(JSON.stringify({ ok: false, error: code }) + '\n');
    if (code === 'write_permission_required') process.stderr.write('请用户在 Tanuki 原生文件页单独授权手机文件写入；不要循环重试或绕过。\n');
    process.exitCode = 1;
  }
}
