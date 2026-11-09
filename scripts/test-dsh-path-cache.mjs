#!/usr/bin/env node
// DSH 启动期只读路径缓存的可执行回归。
//
// 用法：node scripts/test-dsh-path-cache.mjs
// 每个场景在独立 Node 子进程中用 --require 预加载 shim，避免缓存状态互相污染。

import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { mkdtempSync, mkdirSync, writeFileSync, symlinkSync, unlinkSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const shim = join(here, '..', 'app', 'src', 'main', 'assets', 'dsh-startup', 'path-cache.cjs');
const self = fileURLToPath(import.meta.url);
const require = createRequire(import.meta.url);

const scenarios = ['cache', 'symlink', 'symlinkDotDot', 'missing', 'nonReadonly', 'fallback', 'uninstall', 'address', 'timeout', 'disabled'];

function fixture() {
  const root = mkdtempSync(join(tmpdir(), 'tanuki-path-cache-'));
  const readonly = join(root, 'readonly');
  const writable = join(root, 'writable');
  mkdirSync(readonly);
  mkdirSync(writable);
  writeFileSync(join(readonly, 'package.json'), '{"name":"fixture","version":"1.0.0"}\n');
  writeFileSync(join(readonly, 'target.txt'), 'target\n');
  writeFileSync(join(writable, 'package.json'), '{"name":"outside","version":"1.0.0"}\n');
  writeFileSync(join(writable, 'outside.txt'), 'outside\n');
  symlinkSync('target.txt', join(readonly, 'link.txt'));
  symlinkSync('not-there.txt', join(readonly, 'missing-link.txt'));
  // 用于验证符号链接后的 `..`：a/link -> ../b/sub，因此 a/link/../package.json
  // 文件系统解析为 b/package.json，不能与 a/package.json 共用词法缓存键。
  mkdirSync(join(readonly, 'a'));
  mkdirSync(join(readonly, 'b', 'sub'), { recursive: true });
  writeFileSync(join(readonly, 'a', 'package.json'), '{"name":"a"}\n');
  writeFileSync(join(readonly, 'b', 'package.json'), '{"name":"b"}\n');
  writeFileSync(join(readonly, 'b', 'sub', 'package.json'), '{"name":"sub"}\n');
  symlinkSync('../b/sub', join(readonly, 'a', 'link'));
  return { root, readonly, writable };
}

function runScenario(name, fixtureRoot) {
  const env = {
    ...process.env,
    TANUKI_SHIM_PATH: shim,
    TANUKI_DSH_PATH_CACHE_ROOTS: join(fixtureRoot, 'readonly'),
    TANUKI_DSH_PATH_CACHE_TIMEOUT_MS: name === 'timeout' ? '50' : '5000',
    TANUKI_DSH_PATH_CACHE_DEBUG: '0',
  };
  if (name === 'disabled') env.TANUKI_DSH_PATH_CACHE = '0';
  const result = spawnSync(process.execPath, ['--require', shim, self, '--scenario', name, fixtureRoot], {
    env,
    encoding: 'utf8',
    timeout: 15000,
  });
  if (result.error) throw result.error;
  if (result.status !== 0) {
    throw new Error(`scenario ${name} failed (exit ${result.status})\nstdout:\n${result.stdout}\nstderr:\n${result.stderr}`);
  }
}

function scenarioCache(root) {
  const fs = require('node:fs');
  const api = require(process.env.TANUKI_SHIM_PATH);
  const ro = join(root, 'readonly');
  const writable = join(root, 'writable');
  const pkg = join(ro, 'package.json');
  const target = join(ro, 'target.txt');
  const link = join(ro, 'link.txt');
  const outside = join(writable, 'outside.txt');

  assert.equal(api.stats().installed, true, 'shim 应在 --require 时自动安装');
  assert.equal(fs.realpathSync(link), fs.realpathSync(target));
  const realpathHits = api.stats().hits.realpath;
  assert.equal(fs.realpathSync(link), fs.realpathSync(target));
  assert.ok(api.stats().hits.realpath > realpathHits, '第二次 realpath 应命中缓存');
  assert.equal(fs.realpathSync.native(link), fs.realpathSync(target));

  assert.equal(fs.existsSync(target), true);
  const existsHits = api.stats().hits.exists;
  assert.equal(fs.existsSync(target), true);
  assert.ok(api.stats().hits.exists > existsHits, '第二次 exists 应命中缓存');

  const text = fs.readFileSync(pkg, 'utf8');
  assert.match(text, /"name":"fixture"/);
  const readHits = api.stats().hits.read;
  assert.equal(fs.readFileSync(pkg, 'utf8'), text);
  assert.ok(api.stats().hits.read > readHits, '第二次 package.json 读取应命中缓存');
  assert.deepEqual(fs.readFileSync(pkg), Buffer.from(text), '无编码读取仍返回 Buffer');

  // 只读根目录之外不进入缓存；删除后必须立即反映原生结果。
  assert.equal(fs.existsSync(outside), true);
  unlinkSync(outside);
  assert.equal(fs.existsSync(outside), false, '非只读目录必须直通 node:fs');
}

function scenarioSymlink(root) {
  const fs = require('node:fs');
  const api = require(process.env.TANUKI_SHIM_PATH);
  const ro = join(root, 'readonly');
  const target = join(ro, 'target.txt');
  const link = join(ro, 'link.txt');
  const missingLink = join(ro, 'missing-link.txt');

  assert.equal(api.stats().installed, true);
  assert.equal(fs.realpathSync(link), fs.realpathSync(target));
  assert.equal(fs.realpathSync.native(link), fs.realpathSync(target));
  assert.equal(fs.existsSync(link), true);
  assert.equal(fs.existsSync(missingLink), false, '指向缺失目标的符号链接应返回 false');
  assert.throws(() => fs.realpathSync(missingLink), (error) => error && error.code === 'ENOENT');
}

function scenarioSymlinkDotDot(root) {
  const fs = require('node:fs');
  const api = require(process.env.TANUKI_SHIM_PATH);
  const ro = join(root, 'readonly');
  const aPackage = join(ro, 'a', 'package.json');
  const bPackage = join(ro, 'b', 'package.json');
  // 不能用 path.join：它会词法折叠 `..`，测试不到真实文件系统解析。
  const viaDotDot = `${ro}/a/link/../package.json`;
  const viaLink = join(ro, 'a', 'link', 'package.json');

  assert.equal(api.stats().installed, true);
  assert.match(fs.readFileSync(aPackage, 'utf8'), /"a"/);
  assert.match(fs.readFileSync(viaDotDot, 'utf8'), /"b"/, '符号链接后的 .. 必须按文件系统解析，不能串到 a/package.json');
  // Node 的非 native realpathSync 本身会词法折叠 `..`；这里验证会跟随链接的 native 实现。
  assert.equal(fs.realpathSync.native(viaDotDot), fs.realpathSync(bPackage));
  assert.equal(fs.existsSync(viaDotDot), true);
  assert.match(fs.readFileSync(viaLink, 'utf8'), /"sub"/);
  assert.match(fs.readFileSync(aPackage, 'utf8'), /"a"/);
}

function scenarioMissing(root) {
  const fs = require('node:fs');
  const api = require(process.env.TANUKI_SHIM_PATH);
  const ro = join(root, 'readonly');
  const missing = join(ro, 'not-yet.txt');
  const late = join(ro, 'late.txt');

  assert.equal(api.stats().installed, true);
  assert.equal(fs.existsSync(missing), false);
  assert.equal(fs.existsSync(missing), false);
  // 缺失结果不做负缓存：启动窗口内新出现的路径必须被看到。
  assert.equal(fs.existsSync(late), false);
  writeFileSync(late, 'late\n');
  assert.equal(fs.existsSync(late), true);
  assert.throws(() => fs.realpathSync(missing), (error) => error && error.code === 'ENOENT');
}

function scenarioNonReadonly(root) {
  const fs = require('node:fs');
  const api = require(process.env.TANUKI_SHIM_PATH);
  const outside = join(root, 'writable', 'outside.txt');
  const outsidePkg = join(root, 'writable', 'package.json');

  assert.equal(api.stats().installed, true);
  assert.equal(fs.existsSync(outside), true);
  assert.equal(fs.readFileSync(outsidePkg, 'utf8').includes('outside'), true);
  unlinkSync(outside);
  unlinkSync(outsidePkg);
  assert.equal(fs.existsSync(outside), false);
  assert.throws(() => fs.readFileSync(outsidePkg), (error) => error && error.code === 'ENOENT');
  assert.equal(api.stats().entries.exists, 0, '非只读目录不应写入 exists 缓存');
  assert.equal(api.stats().entries.packages, 0, '非只读目录不应写入 package.json 缓存');
}

function scenarioFallback(root) {
  const fs = require('node:fs');
  const api = require(process.env.TANUKI_SHIM_PATH);
  const ro = join(root, 'readonly');
  const pkg = join(ro, 'package.json');
  const link = join(ro, 'link.txt');

  assert.equal(api.stats().installed, true);
  // 先写入缓存，再用原生同样会拒绝的编码触发回退；异常不能被吞掉。
  assert.equal(fs.readFileSync(pkg, 'utf8').includes('fixture'), true);
  assert.throws(() => fs.readFileSync(pkg, { encoding: 'tanuki-not-an-encoding' }), TypeError);
  assert.throws(() => fs.realpathSync(link, { encoding: 'tanuki-not-an-encoding' }), TypeError);
  assert.throws(() => fs.realpathSync('\0'), (error) => error && (error.code === 'ENOENT' || error.code === 'ERR_INVALID_ARG_VALUE'));
}

function scenarioUninstall(root) {
  const fs = require('node:fs');
  const api = require(process.env.TANUKI_SHIM_PATH);
  const ro = join(root, 'readonly');
  const target = join(ro, 'target.txt');

  assert.equal(fs.existsSync(target), true);
  unlinkSync(target);
  assert.equal(fs.existsSync(target), true, '卸载前应命中已有缓存');
  api.uninstall('test');
  assert.equal(api.stats().installed, false);
  assert.equal(fs.existsSync(target), false, '卸载后必须直通 node:fs');
  assert.equal(api.stats().entries.exists, 0);
}

function scenarioAddress(root) {
  const fs = require('node:fs');
  const api = require(process.env.TANUKI_SHIM_PATH);
  const ro = join(root, 'readonly');
  const target = join(ro, 'target.txt');

  assert.equal(fs.existsSync(target), true);
  // 与官方 DSH 就绪行一致；shim 应在写出该行时立即卸载。
  process.stdout.write('dsh web: http://127.0.0.1:23456/?token=test\n');
  assert.equal(api.stats().installed, false, '地址行出现后应卸载');
  assert.equal(api.stats().uninstallReason, 'ready');
  unlinkSync(target);
  assert.equal(fs.existsSync(target), false);
}

async function scenarioTimeout(root) {
  const fs = require('node:fs');
  const api = require(process.env.TANUKI_SHIM_PATH);
  const ro = join(root, 'readonly');
  const target = join(ro, 'target.txt');

  assert.equal(fs.existsSync(target), true);
  assert.equal(api.stats().installed, true);
  await new Promise((resolve) => setTimeout(resolve, 200));
  assert.equal(api.stats().installed, false, '超时后应卸载');
  assert.equal(api.stats().uninstallReason, 'timeout');
  unlinkSync(target);
  assert.equal(fs.existsSync(target), false);
}

function scenarioDisabled(root) {
  const fs = require('node:fs');
  const api = require(process.env.TANUKI_SHIM_PATH);
  const ro = join(root, 'readonly');
  const target = join(ro, 'target.txt');

  assert.equal(api.stats().installed, false, '环境变量关闭时不应安装');
  assert.equal(fs.existsSync(target), true);
  unlinkSync(target);
  assert.equal(fs.existsSync(target), false);
}

async function runAll() {
  assert.equal(typeof require('node:fs').existsSync, 'function');
  for (const name of scenarios) {
    const { root } = fixture();
    try {
      runScenario(name, root);
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  }
  console.log(`PASS dsh path cache (${scenarios.length} scenarios)`);
}

const scenarioIndex = process.argv.indexOf('--scenario');
if (scenarioIndex !== -1) {
  const name = process.argv[scenarioIndex + 1];
  const root = process.argv[scenarioIndex + 2];
  const handlers = {
    cache: scenarioCache,
    symlink: scenarioSymlink,
    symlinkDotDot: scenarioSymlinkDotDot,
    missing: scenarioMissing,
    nonReadonly: scenarioNonReadonly,
    fallback: scenarioFallback,
    uninstall: scenarioUninstall,
    address: scenarioAddress,
    timeout: scenarioTimeout,
    disabled: scenarioDisabled,
  };
  const handler = handlers[name];
  if (!handler) {
    console.error(`unknown scenario: ${name}`);
    process.exit(2);
  }
  await handler(root);
} else {
  await runAll();
}
