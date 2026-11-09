'use strict';
// Tanuki DSH 启动期只读路径缓存。
//
// 只在 DSH `web` 启动阶段由 `node --require` 预加载；缓存范围仅限 rootfs 内
// 只读目录（/opt/node、/opt/engines、/opt/pnpm 与受控支持目录）。官方就绪
// 地址行出现或启动超时后立即卸载，之后所有调用直通 node:fs。
//
// 设计约束：
// - 任何缓存逻辑异常都退回原生实现，不阻塞 DSH 启动。
// - 只缓存成功结果；缺失路径每次仍由原生判断，避免负缓存过期。
// - 不修改官方 npm 包，不改变 DSH UI/协议。
// - `TANUKI_DSH_PATH_CACHE=0|false|off|no` 关闭；测试可用
//   `TANUKI_DSH_PATH_CACHE_ROOTS` 覆盖只读根目录（冒号分隔）。

const fs = require('node:fs');
const path = require('node:path');

const SCRIPT_VERSION = 1;
const DEFAULT_ROOTS = [
  '/opt/node',
  '/opt/engines',
  '/opt/pnpm',
  '/opt/tanuki',
  '/opt/tanuki-phone',
  '/opt/tanuki-startup',
];
const READY_MARKER = 'dsh web:';
const READY_PATTERN = /dsh web:\s*http:\/\/127\.0\.0\.1:\d+\//;
const MAX_REALPATH_ENTRIES = 4096;
const MAX_EXISTS_ENTRIES = 4096;
const MAX_PACKAGE_ENTRIES = 512;
const MAX_PACKAGE_BYTES = 256 * 1024;
const DEFAULT_TIMEOUT_MS = 30000;

const original = {
  realpathSync: fs.realpathSync,
  realpathSyncNative: typeof fs.realpathSync.native === 'function' ? fs.realpathSync.native : fs.realpathSync,
  existsSync: fs.existsSync,
  readFileSync: fs.readFileSync,
  stdoutWrite: process.stdout.write,
  stderrWrite: process.stderr.write,
};

const state = {
  installed: false,
  roots: [],
  realpath: new Map(),
  exists: new Map(),
  packages: new Map(),
  timer: null,
  realpathWrapper: null,
  existsWrapper: null,
  readWrapper: null,
  stdoutWrapper: null,
  stderrWrapper: null,
  hits: { realpath: 0, exists: 0, read: 0 },
  misses: { realpath: 0, exists: 0, read: 0 },
  skipped: { realpath: 0, exists: 0, read: 0 },
  uninstallReason: null,
};

function debugEnabled() {
  return process.env.TANUKI_DSH_PATH_CACHE_DEBUG === '1';
}

function debug(message) {
  if (!debugEnabled()) return;
  try {
    original.stderrWrite.call(process.stderr, `[tanuki-path-cache] ${message}\n`);
  } catch (_) {
    // 调试输出失败不能影响 DSH 启动。
  }
}

function disabledByEnvironment() {
  const value = String(process.env.TANUKI_DSH_PATH_CACHE || '').trim().toLowerCase();
  return value === '0' || value === 'false' || value === 'off' || value === 'no';
}

function configuredRoots(override) {
  const raw = override != null
    ? (Array.isArray(override) ? override.join(path.delimiter) : String(override))
    : process.env.TANUKI_DSH_PATH_CACHE_ROOTS;
  const list = raw ? String(raw).split(path.delimiter) : DEFAULT_ROOTS;
  const roots = [];
  for (const item of list) {
    const value = String(item || '').trim();
    if (!value || !path.isAbsolute(value)) continue;
    const normalized = path.normalize(value);
    if (!roots.includes(normalized)) roots.push(normalized);
  }
  return roots;
}

function configuredTimeoutMs() {
  const raw = Number(process.env.TANUKI_DSH_PATH_CACHE_TIMEOUT_MS);
  if (!Number.isFinite(raw) || raw < 0) return DEFAULT_TIMEOUT_MS;
  return Math.floor(raw);
}

// 只接受绝对路径，避免进程工作目录变化后命中错误条目。
// 不调用 path.normalize：符号链接后的 `..` 必须先由文件系统跟随链接再解析，
// 词法折叠会让 a/link/../package.json 与 a/package.json 共用错误缓存键。
function cacheablePath(input) {
  if (typeof input !== 'string' || input.length === 0 || input.indexOf('\0') !== -1) return null;
  if (!path.isAbsolute(input) || input.endsWith(path.sep)) return null;
  const parts = input.split(path.sep);
  for (let i = 1; i < parts.length; i += 1) {
    // `.`、`..`、重复斜杠和空段都改变文件系统解析语义，直接交给原生实现。
    if (parts[i] === '' || parts[i] === '.' || parts[i] === '..') return null;
  }
  for (const root of state.roots) {
    if (input === root || input.startsWith(root + path.sep)) return input;
  }
  return null;
}

function isPackageJson(file) {
  return path.basename(file) === 'package.json';
}

function encodingOf(options) {
  if (typeof options === 'string') return options;
  if (options && typeof options === 'object' && options.encoding != null) return options.encoding;
  return null;
}

function flagOf(options) {
  if (options && typeof options === 'object' && options.flag != null) return String(options.flag);
  return 'r';
}

function decodeRealpath(value, options) {
  const encoding = encodingOf(options);
  if (encoding === 'buffer') return Buffer.from(value);
  if (encoding && encoding !== 'utf8' && encoding !== 'utf-8') {
    if (!Buffer.isEncoding(encoding)) throw new TypeError(`Unknown encoding: ${encoding}`);
    return Buffer.from(value).toString(encoding);
  }
  return value;
}

function decodeRead(value, options) {
  const encoding = encodingOf(options);
  if (encoding == null) return Buffer.from(value);
  // readFileSync 不接受 'buffer' 作为编码；交给原生实现报同样的错。
  if (!Buffer.isEncoding(encoding)) throw new TypeError(`Unknown encoding: ${encoding}`);
  return value.toString(encoding);
}

function cachedRealpath(input, options, native) {
  if (!state.installed) return (native ? original.realpathSyncNative : original.realpathSync).call(fs, input, options);
  const callNative = () => (native ? original.realpathSyncNative : original.realpathSync).call(fs, input, options);
  try {
    const file = cacheablePath(input);
    if (file == null) {
      state.skipped.realpath += 1;
      return callNative();
    }
    const hit = state.realpath.get(file);
    if (hit !== undefined) {
      state.hits.realpath += 1;
      return decodeRealpath(hit, options);
    }
    state.misses.realpath += 1;
    const resolved = callNative();
    const text = Buffer.isBuffer(resolved) ? resolved.toString('utf8') : String(resolved);
    if (state.realpath.size < MAX_REALPATH_ENTRIES) state.realpath.set(file, text);
    return resolved;
  } catch (_) {
    // 缓存转换或原生异常都交回原生实现，保持与 node:fs 一致。
    return callNative();
  }
}

function cachedExists(input) {
  if (!state.installed) return original.existsSync.call(fs, input);
  try {
    const file = cacheablePath(input);
    if (file == null) {
      state.skipped.exists += 1;
      return original.existsSync.call(fs, input);
    }
    const hit = state.exists.get(file);
    if (hit !== undefined) {
      state.hits.exists += 1;
      return hit;
    }
    state.misses.exists += 1;
    const result = original.existsSync.call(fs, input);
    // 只缓存存在的只读路径；缺失结果每次重新判断，避免负缓存过期。
    if (result && state.exists.size < MAX_EXISTS_ENTRIES) state.exists.set(file, true);
    return result;
  } catch (_) {
    return original.existsSync.call(fs, input);
  }
}

function cachedReadFile(input, options) {
  if (!state.installed) return original.readFileSync.call(fs, input, options);
  try {
    const file = cacheablePath(input);
    if (file == null || !isPackageJson(file) || flagOf(options) !== 'r') {
      state.skipped.read += 1;
      return original.readFileSync.call(fs, input, options);
    }
    const hit = state.packages.get(file);
    if (hit !== undefined) {
      state.hits.read += 1;
      return decodeRead(hit, options);
    }
    state.misses.read += 1;
    const result = original.readFileSync.call(fs, input, options);
    const buffer = Buffer.isBuffer(result) ? Buffer.from(result) : Buffer.from(result, encodingOf(options) || 'utf8');
    if (buffer.length <= MAX_PACKAGE_BYTES && state.packages.size < MAX_PACKAGE_ENTRIES) {
      state.packages.set(file, buffer);
    }
    return result;
  } catch (_) {
    return original.readFileSync.call(fs, input, options);
  }
}

function readyLine(chunk) {
  try {
    if (typeof chunk === 'string') {
      return chunk.indexOf(READY_MARKER) !== -1 && READY_PATTERN.test(chunk);
    }
    if (Buffer.isBuffer(chunk)) {
      if (chunk.indexOf(READY_MARKER) === -1) return false;
      return READY_PATTERN.test(chunk.toString('utf8'));
    }
  } catch (_) {
    // 输出检测失败不能影响 DSH 输出。
  }
  return false;
}

function clearCaches() {
  state.realpath.clear();
  state.exists.clear();
  state.packages.clear();
}

function uninstall(reason) {
  if (state.timer != null) {
    clearTimeout(state.timer);
    state.timer = null;
  }
  state.installed = false;
  state.uninstallReason = reason || 'manual';
  // 只还原仍由本 shim 持有的函数；其他预加载钩子已替换时保留它们。
  if (state.realpathWrapper && fs.realpathSync === state.realpathWrapper) fs.realpathSync = original.realpathSync;
  if (state.existsWrapper && fs.existsSync === state.existsWrapper) fs.existsSync = original.existsSync;
  if (state.readWrapper && fs.readFileSync === state.readWrapper) fs.readFileSync = original.readFileSync;
  if (state.stdoutWrapper && process.stdout.write === state.stdoutWrapper) process.stdout.write = original.stdoutWrite;
  if (state.stderrWrapper && process.stderr.write === state.stderrWrapper) process.stderr.write = original.stderrWrite;
  const summary = stats();
  clearCaches();
  debug(`uninstalled (${state.uninstallReason}) hits=${JSON.stringify(summary.hits)} misses=${JSON.stringify(summary.misses)} skipped=${JSON.stringify(summary.skipped)}`);
}

function install(options) {
  if (state.installed) return true;
  if (disabledByEnvironment()) {
    debug('disabled by TANUKI_DSH_PATH_CACHE');
    return false;
  }
  const settings = options && typeof options === 'object' ? options : {};
  state.roots = configuredRoots(settings.roots);
  if (state.roots.length === 0) {
    debug('no usable read-only roots');
    return false;
  }
  clearCaches();
  state.hits = { realpath: 0, exists: 0, read: 0 };
  state.misses = { realpath: 0, exists: 0, read: 0 };
  state.skipped = { realpath: 0, exists: 0, read: 0 };
  state.uninstallReason = null;
  try {
    const realpathWrapper = function realpathSync(input, realpathOptions) {
      return cachedRealpath(input, realpathOptions, false);
    };
    realpathWrapper.native = function realpathSyncNative(input, realpathOptions) {
      return cachedRealpath(input, realpathOptions, true);
    };
    const existsWrapper = function existsSync(input) {
      return cachedExists(input);
    };
    const readWrapper = function readFileSync(input, readOptions) {
      return cachedReadFile(input, readOptions);
    };
    const stdoutWrapper = function write(chunk, encoding, callback) {
      try {
        if (state.installed && readyLine(chunk)) uninstall('ready');
      } catch (_) {
        // 就绪检测失败时继续正常输出。
      }
      return original.stdoutWrite.call(process.stdout, chunk, encoding, callback);
    };
    const stderrWrapper = function write(chunk, encoding, callback) {
      try {
        if (state.installed && readyLine(chunk)) uninstall('ready');
      } catch (_) {
        // 就绪检测失败时继续正常输出。
      }
      return original.stderrWrite.call(process.stderr, chunk, encoding, callback);
    };
    state.realpathWrapper = realpathWrapper;
    state.existsWrapper = existsWrapper;
    state.readWrapper = readWrapper;
    state.stdoutWrapper = stdoutWrapper;
    state.stderrWrapper = stderrWrapper;
    fs.realpathSync = realpathWrapper;
    fs.existsSync = existsWrapper;
    fs.readFileSync = readWrapper;
    process.stdout.write = stdoutWrapper;
    process.stderr.write = stderrWrapper;
    state.installed = true;
    const timeout = configuredTimeoutMs();
    if (timeout > 0) {
      state.timer = setTimeout(() => uninstall('timeout'), timeout);
      if (typeof state.timer.unref === 'function') state.timer.unref();
    }
    debug(`installed roots=${state.roots.join(',')} timeout=${timeout}ms`);
    return true;
  } catch (_) {
    uninstall('install-failed');
    return false;
  }
}

function stats() {
  return {
    version: SCRIPT_VERSION,
    installed: state.installed,
    roots: state.roots.slice(),
    entries: {
      realpath: state.realpath.size,
      exists: state.exists.size,
      packages: state.packages.size,
    },
    hits: { ...state.hits },
    misses: { ...state.misses },
    skipped: { ...state.skipped },
    uninstallReason: state.uninstallReason,
  };
}

module.exports = {
  SCRIPT_VERSION,
  DEFAULT_ROOTS,
  install,
  uninstall,
  stats,
};

// 作为 `node --require` 预加载时自动安装；显式 require 时也保持同一实例。
try {
  install();
} catch (_) {
  try {
    uninstall('preload-failed');
  } catch (_) {
    // 预加载失败必须静默退回原生 node:fs。
  }
}
