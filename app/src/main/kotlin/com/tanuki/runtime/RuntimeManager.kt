package com.tanuki.runtime

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import android.util.AtomicFile
import android.webkit.WebView
import com.tanuki.BuildConfig
import com.tanuki.download.Downloader
import com.tanuki.storage.SafeTar
import com.tanuki.storage.WorkspaceStorage
import com.tanuki.storage.WorkspaceCapacity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import java.nio.file.Files
import java.nio.file.AccessDeniedException
import java.nio.file.LinkOption
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

class RuntimeManager(val context: Context) {
    val paths = Paths(context)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val log = EventLog(paths, scope)
    val launcher = Launcher(context, paths, log)
    val state = MutableStateFlow(Snapshot())
    private val remoteMachine = RemoteHostMachine()
    @Volatile var cookieSwitchGuard: DshCookieSwitchGuard? = null
        private set
    private val cookieIsolation = DshCookieIsolation(
        scopeStore = SharedPreferencesKeyValueStore(
            context.getSharedPreferences("remote-host-cookie-scope", Context.MODE_PRIVATE)
        ),
        guardProvider = { cookieSwitchGuard }
    )
    val remoteHost = RemoteHostController(
        storage = androidRemoteHostStorage(context),
        transport = RoutingSshTransport(),
        scope = scope,
        machine = remoteMachine,
        onStatus = { status ->
            state.update {
                it.copy(
                    remote = status,
                    // Cookie 切换失败时保持页面不可用，不能把上一台主机的认证 Cookie 交给新端点。
                    web = if (status.error.contains("Cookie 隔离失败")) WebState.RENDER_ERROR else it.web
                )
            }
        },
        activateScope = { scopeName -> cookieIsolation.switchTo(scopeName) },
        profileStore = androidRemoteHostProfileStore(context)
    )
    val failurePolicy = RuntimeFailurePolicy()
    private val recoveryState = MutableStateFlow(RuntimeRecoveryState())
    private var autoRestartJob: Job? = null
    private var stabilityJob: Job? = null
    @Volatile private var cookieIsolationFailed = false
    val fileTransfers = FileTransfers { android.os.SystemClock.elapsedRealtime() }
    val phoneFiles = com.tanuki.storage.PhoneFileService(context, paths, fileTransfers)
    val mutation = Mutex()
    /** 版本元数据窄锁：切换、卸载与更新检查串行，避免并发读写 installed.json 与 engines。 */
    val versionGate = DshVersionGate()
    private val stopMutex = Mutex()
    private var job: Job? = null
    private val operationRetry = OperationRetry()
    private var serverJob: Job? = null
    private var workspaceMonitor: Job? = null
    private var workspaceRefresh: Job? = null
    private val earlyStartGate = EarlyStartGate()
    /** 独立更新管理器（DSH 安装/备用/恢复、APK 更新）的忙碌信号，由 TanukiApp 注入。 */
    @Volatile private var externalBusy: () -> Boolean = { false }
    @Volatile private var stopping = false
    private var runtimeGeneration = 0L
    @Volatile private var child: Child? = null
    val workspaceStorage = WorkspaceStorage(paths)
    val requiredTools = MutableStateFlow(RequiredToolsStatus())
    internal val hasLiveChild: Boolean get() = child != null
    internal val hasPendingAutoRestart: Boolean get() = autoRestartJob?.isActive == true
    val components = RuntimeComponents(this)
    val npmSources = NpmSources(this)
    private val browserGeneration=AtomicLong()
    val hasActiveOperation: Boolean get() = job?.isActive == true || serverJob?.isActive == true || mutation.isLocked
    val authenticatedUrl: String? get() = remoteMachine.authenticatedUrl
    val origin: String? get() = remoteMachine.origin
    private data class RunAttempt(val mode: RecoveryMode) {
        @Volatile var readyAtMillis: Long = 0L
    }
    init {
        val componentRecovery=runCatching { components.recover() }
        val restoreRecovery=runCatching { com.tanuki.storage.DataSnapshots.recoverPreparedRestore(paths) }
        val meta = paths.metadata()
        val metadataExists=File(paths.state,"installed.json").exists()
        val selectedVersion=meta.optString("dsh")
        val selectedEngine=if(selectedVersion.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?")))File(paths.engines,selectedVersion) else null
        val installed = components.installedUbuntuVersion()!=null && components.installedNodeVersion()!=null && meta.optBoolean("ready") && meta.has("dsh") && meta.has("node") && meta.has("ubuntu") && File(paths.root, ".base-verified").isFile &&
            (File(paths.node,"bin/node").isFile || File(paths.root,"opt/node/bin/node").isFile) && selectedEngine!=null &&
            File(selectedEngine,".installed").isFile && File(selectedEngine,"node_modules/@deepseek-ai/dsh/lib/bin.js").isFile
        val previous = paths.read("service")
        // 进程重启后恢复安全模式/试用状态；试用运行中进程消失视为试用失败，回到安全模式。
        val persistedRecovery = failurePolicy.decode(paths.read("failure-state"))
        val restoredRecovery = failurePolicy.afterProcessRestart(persistedRecovery, previous in setOf("running", "starting", "interrupted"), System.currentTimeMillis())
        recoveryState.value = restoredRecovery
        if (restoredRecovery != persistedRecovery) runCatching { paths.atomic("failure-state", failurePolicy.encode(restoredRecovery)) }
        val processLossNote = restoredRecovery.mode == RecoveryMode.SAFE && persistedRecovery.mode == RecoveryMode.PROBATION
        state.value = Snapshot(environment = if (installed) EnvironmentState.READY else if (metadataExists || paths.stage.exists() || paths.read("transaction") != null) EnvironmentState.REPAIR else EnvironmentState.ABSENT,
            service = when(previous){"running","starting","interrupted"->ServiceState.INTERRUPTED;"failed"->ServiceState.FAILED;else->ServiceState.STOPPED},
            error = if (processLossNote) "上次正常模式试用期间进程中断，已回到安全模式" else paths.read("last-error") ?: "",
            confinement = runCatching { Confinement.valueOf(paths.read("confinement") ?: "UNKNOWN") }.getOrDefault(Confinement.UNKNOWN),
            stage = if (installed) "环境已安装，健康状态待检查" else "首次联网准备 Ubuntu、Node.js 和官方 DSH",
            ubuntu = components.installedUbuntuVersion() ?: meta.optString("ubuntu", "未安装"), node = components.installedNodeVersion() ?: meta.optString("node", "未安装"), dsh = meta.optString("dsh", "未安装"),
            recovery = restoredRecovery)
        val updateJournal=paths.read("update-transaction")
        if(updateJournal!=null) {
            val phase=runCatching { JSONObject(updateJournal).optString("phase") }.getOrDefault("unknown")
            if(phase=="staging") {
                File(paths.state,"update-transaction").delete()
                state.update { it.copy(update=UpdateState.FAILED,error="上次候选安装或检查已中断，当前版本未切换，可以重试") }
            }else state.update { it.copy(update=UpdateState.RECOVERY_REQUIRED,error="更新中断；正式数据可能已迁移，请检查当前版本或恢复匹配快照") }
        }
        val restorePhase=runCatching { JSONObject(paths.read("restore-transaction") ?: "{}").optString("phase") }.getOrDefault("unknown")
        if(restorePhase=="switched")state.update { it.copy(update=UpdateState.RESTORED_PENDING_CHECK,stage=restoreRecovery.getOrNull() ?: "数据已恢复，等待启动检查") }
        restoreRecovery.exceptionOrNull()?.let { error -> state.update { it.copy(update=UpdateState.RECOVERY_REQUIRED,error=Redact.text(error.message ?: "数据恢复中断")) } }
        if(paths.read("transaction")=="background-probe")state.update { it.copy(stage="上次后台自检未完整结束，未自动重放") }
        componentRecovery.exceptionOrNull()?.let { error -> state.update { it.copy(environment=EnvironmentState.REPAIR,error=Redact.text(error.message ?: "组件恢复未完成")) } }
        components.refresh(false)
        runCatching {
            val marker = File(paths.root,".base-verified").takeIf { it.isFile }?.readText().orEmpty()
            val saved = JSONObject(paths.read("required-tools.json") ?: "{}")
            if (marker.isNotEmpty() && saved.optInt("schema") == RuntimeRequirements.PROFILE && saved.optString("ubuntuMarker") == marker) {
                val missing = saved.optJSONArray("missing")
                requiredTools.value = RequiredToolsStatus(true, List(missing?.length() ?: 0) { missing!!.getString(it) }, marker, saved.optLong("checkedAt"))
            }
        }
        scope.launch {
            mutation.withLock {
                try {
                    if (workspaceStorage.recoverInterruptedImport()) {
                        event("已清理上次未提交的项目导入，现有项目保留")
                        workspaceStorage.refresh()
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Throwable) { event("项目导入恢复需要处理：${error.message ?: "请检查存储"}") }
            }
        }
        log.append("app start uid=${Process.myUid()} pid=${Process.myPid()} debuggable=${BuildConfig.DEBUG} target=${context.applicationInfo.targetSdkVersion} sdk=${Build.VERSION.SDK_INT}")
        scope.launch {
            val clock = OperationClock()
            combine(state.map { it.operationSignal() }.distinctUntilChanged(), log.activityAtMillis) { signal, output -> signal to output }
                .collect { (signal, output) ->
                    val timing = clock.observe(signal, output, android.os.SystemClock.elapsedRealtime())
                    state.update { if (it.operationSignal() == signal) it.copy(timing = timing) else it }
                }
        }
    }
    fun event(text: String) { log.append(text); state.update { it.copy(events = (it.events + Redact.text(text)).takeLast(12)) } }
    fun fail(t: Throwable) = fail(t, null)
    fun fail(t: Throwable, operation: String?) {
        val detail = when (t) {
            is AccessDeniedException -> "文件访问被拒绝；安装或恢复时无法读取目录：${t.file ?: t.message}"
            else -> t.message ?: t.javaClass.simpleName
        }
        val message = Redact.text(detail)
        log.append("ERROR ${t.javaClass.simpleName}: $message")
        runCatching { paths.atomic("last-error",message) }
        state.update { current ->
            // 分类只在这里做一次，各页面读取同一份标题、原因和动作。
            val failure = FailureCatalog.describe(
                FailureInput(
                    message = message,
                    operation = operation ?: current.operation,
                    retry = current.operationRetry.takeIf { operation == null || operation == current.operation },
                    stage = current.stage,
                    environment = current.environment,
                    update = current.update,
                    service = current.service,
                    recovery = current.recovery,
                    occurredAtMillis = System.currentTimeMillis()
                )
            )
            current.copy(error = message, failure = failure)
        }
    }
    internal fun phase(label: String, env: EnvironmentState) { state.update { it.copy(stage = label, environment = env, bytes = 0, total = -1, download = null, phasePercent = null) }; event(label) }
    fun cancel() { job?.cancel() }

    @Synchronized internal fun beginOperationRetry(action: () -> Unit): Long {
        val id = operationRetry.begin(action)
        state.update { it.copy(failedOperation = null) }
        return id
    }

    @Synchronized internal fun recordOperationFailure(id: Long, cancelled: Boolean = false) {
        if (!operationRetry.fail(id)) return
        val elapsed = operationRetry.elapsedMillis(id)
        state.update { snapshot ->
            snapshot.copy(failedOperation = FailedOperation(id, snapshot.stage,
                if (cancelled) "" else snapshot.error, elapsed, cancelled))
        }
    }

    internal fun completeOperationRetry(id: Long) = operationRetry.complete(id)

    @Synchronized internal fun invalidateOperationRetry() {
        operationRetry.invalidate()
        state.update { it.copy(failedOperation = it.failedOperation?.copy(retryAvailable = false)) }
    }

    fun retryOperation(id: Long) {
        if (hasActiveOperation || externalBusy() || state.value.operationActive ||
            state.value.failedOperation?.retryId != id || state.value.failedOperation?.retryAvailable != true) return
        // 与第一次执行共用前台服务入口；应用退到后台后，重试仍保持安装通知。
        context.startForegroundService(android.content.Intent(context, RuntimeService::class.java).setAction("KEEPALIVE"))
        operationRetry.retry(id)
    }
    internal fun launchExclusive(label: String, retry: FailureRetry? = null, block: suspend () -> Unit) {
        synchronized(this) {
            if (job?.isActive == true) { event("已有环境操作正在进行"); return }
            job = scope.launch {
                if (!mutation.tryLock()) {
                    event("另一个操作占用环境，请稍后重试")
                    // 不能只写事件日志：版本按钮失败时用户需要看到中文原因。
                    state.update { it.copy(error = "另一个安装、切换、卸载或更新操作正在进行，请稍后重试", stage = "操作未开始：环境正被其他操作占用") }
                    return@launch
                }
                val before=state.value
                val retryId = beginOperationRetry(operationRetryAction(
                    request = retry,
                    installComponent = components::install,
                    replay = { launchExclusive(label, retry, block) },
                ))
                state.update { it.copy(operation=label,operationRetry=retry,stage=operationLabel(label),bytes=0,total=-1,download=null,phasePercent=null,error="",failure=null) }
                try {
                    check(child == null || label == "background-probe") { "请先停止服务；可能中断正在执行的任务" }
                    paths.atomic("transaction", label)
                    block()
                    File(paths.state, "transaction").delete()
                    completeOperationRetry(retryId)
                } catch (e: CancellationException) {
                    recordOperationFailure(retryId, cancelled = true)
                    if(label.startsWith("component-")) {
                        components.refresh()
                        state.update { it.copy(stage="组件操作已停止；当前安装状态已刷新",download=null,bytes=0,total=-1) }
                    } else state.update { if(label=="ubuntu-cache")it.copy(environment=before.environment,stage="Ubuntu 缓存整理已取消；已生效的精简策略保留") else if (label in setOf("health", "background-probe")) it.copy(health = HealthState.UNKNOWN) else it.copy(environment = EnvironmentState.CANCELLED, stage = "已取消，可重试并复用完整下载") }
                    event("操作取消")
                } catch (e: Throwable) {
                    fail(e)
                    recordOperationFailure(retryId)
                    if(label.startsWith("component-")) {
                        components.refresh()
                        state.update { it.copy(stage="组件操作失败，请查看错误信息",download=null,bytes=0,total=-1) }
                    } else state.update { if(label=="ubuntu-cache")it.copy(environment=before.environment,stage="Ubuntu 缓存整理失败，详见错误记录") else if (label in setOf("health", "background-probe")) it.copy(health = HealthState.FAILED) else it.copy(environment = EnvironmentState.FAILED) }
                } finally {
                    try { components.refresh(false) } finally {
                        state.update { it.copy(operation=null,operationRetry=null,download=null,bytes=0,total=-1,phasePercent=null) }
                        mutation.unlock()
                    }
                }
            }
        }
    }
    fun initialize(baseOnly: Boolean = false) = launchExclusive("initialize") {
        if(File(paths.state,"installed.json").exists()) {
            val known=paths.metadata()
            check(known.optBoolean("ready") && known.has("dsh") && known.has("node") && known.has("ubuntu")) { "安装元数据损坏，已保留所有数据；请恢复匹配快照，不能自动选择旧程序读取现有会话" }
            check(known.getString("dsh")==DSH) { "当前环境已独立选择其他 DSH 版本；请使用匹配版本的恢复流程，不能用 APK 默认版本覆盖现有会话" }
        }
        ensureBase()
        configureMinimalUbuntu()
        val probe = launcher.run(paths.root, listOf("/bin/bash", "-c", "cat /etc/os-release; uname -m; printf 'LINUX_APP_OK\\n'"))
        check(probe.exitCode == 0 && probe.stdout.contains("LINUX_APP_OK")) { "App 内 Linux 启动失败 exit=${probe.exitCode}: ${probe.stderr}" }
        if (baseOnly) { state.update { it.copy(stage = "Ubuntu 基础启动实验通过；尚未安装 Node.js 和 DSH", environment = EnvironmentState.ABSENT) }; return@launchExclusive }
        ensureUbuntuTools()
        installNode()
        ensurePnpm()
        phase("安装官方 DSH $DSH（锁定入口版本，保存依赖锁）", EnvironmentState.INSTALLING)
        installEngine(DSH)
        phase("运行检查", EnvironmentState.VERIFYING)
        val result = diagnostics()
        check(result.exitCode == 0) { "健康检查失败: ${result.stderr}" }
        val ubuntuVersion=components.installedUbuntuVersion() ?: error("Ubuntu 镜像版本信息缺失")
        val nodeVersion=components.installedNodeVersion() ?: error("Node.js 版本信息缺失")
        paths.atomic("installed.json", JSONObject().put("ready", true).put("ubuntu", ubuntuVersion).put("node", nodeVersion).put("dsh", DSH).toString())
        state.update { it.copy(environment = EnvironmentState.READY, health = HealthState.HEALTHY, ubuntu = ubuntuVersion, node = nodeVersion, dsh = DSH, stage = "初始化完成，启动后可在官方设置中配置 API Key", error = "", failure = null) }
        start()
    }
    internal fun publishRequiredTools(missing: List<String>) {
        val marker = File(paths.root,".base-verified").takeIf { it.isFile }?.readText().orEmpty()
        val checked = RequiredToolsStatus(true, missing, marker, System.currentTimeMillis())
        paths.atomic("required-tools.json", JSONObject().put("schema",RuntimeRequirements.PROFILE).put("ubuntuMarker",marker)
            .put("checkedAt",checked.checkedAt).put("missing",org.json.JSONArray(missing)).toString())
        requiredTools.value = checked
    }
    fun installRequiredTools() = launchExclusive("component-tools") {
        check(components.baseReady()) { "请先完成 Ubuntu 和 Node.js 安装" }
        val before = state.value.environment
        ensureUbuntuTools()
        ensurePnpm()
        check(requiredTools.value.passed) { "基础工具检查尚未通过" }
        state.update { it.copy(stage="基础工具已检查，缺失项已补齐",environment=before,download=null,phasePercent=null) }
    }
    internal suspend fun ensureUbuntuTools() {
        configureMinimalUbuntu()
        RuntimeRequirements(this).ensure()
        ensureWorkspaceSupport()
        cleanUbuntuCache()
    }
    internal suspend fun ensureNodeLibraries() {
        RuntimeRequirements(this).ensure(tools=false)
        cleanUbuntuCache()
    }
    private suspend fun ensureWorkspaceSupport() {
        // DSH resolves its default workspace via the standard Linux xdg-user-dir command.
        val binary=File(paths.root,"usr/bin/xdg-user-dir")
        if(!binary.isFile || !binary.canExecute()) {
            state.update { it.copy(stage="补齐工作区基础组件（Ubuntu 签名软件源）") }
            configureMinimalUbuntu()
            checked(listOf("/usr/bin/dpkg","--configure","-a"),600000)
            checked(listOf("/usr/bin/apt-get","update"),600000)
            checked(listOf("/usr/bin/apt-get","install","-y","--no-install-recommends","xdg-user-dirs"),600000)
            cleanUbuntuCache()
        }
        val config=File(paths.home,".config/user-dirs.dirs")
        check(config.canonicalFile.toPath().startsWith(paths.home.canonicalFile.toPath())) { "用户目录配置路径越界，已保留原配置" }
        // Respect existing user choices; new installations keep DSH-created workspaces in the
        // same persistent mount used by project import/export.
        if(!Files.exists(config.toPath(),java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            check(config.parentFile!!.isDirectory || config.parentFile!!.mkdirs()) { "无法创建用户目录配置" }
            val atomic=AtomicFile(config);val output=atomic.startWrite()
            try { output.write("XDG_DOCUMENTS_DIR=\"/workspaces\"\n".toByteArray(Charsets.UTF_8));atomic.finishWrite(output) }
            catch(t:Throwable){atomic.failWrite(output);throw t}
        }
        checked(listOf("/usr/bin/xdg-user-dir","DOCUMENTS"),30000)
    }
    internal fun configureMinimalUbuntu() {
        fun managed(relative:String,body:String) {
            val file=File(paths.root,relative)
            check(file.canonicalFile.toPath().startsWith(paths.root.canonicalFile.toPath())) { "Ubuntu 配置路径越界" }
            val atomic=AtomicFile(file)
            val output=atomic.startWrite()
            try { output.write(body.toByteArray(Charsets.UTF_8));atomic.finishWrite(output) }
            catch(t:Throwable){atomic.failWrite(output);throw t}
        }
        managed("etc/apt/apt.conf.d/90tanuki-minimal","""
            // Tanuki minimal Ubuntu policy; optional tools remain available via apt.
            APT::Install-Recommends "false";
            APT::Install-Suggests "false";
            Acquire::Languages "none";
            APT::Keep-Downloaded-Packages "false";
            Binary::apt::APT::Keep-Downloaded-Packages "false";
        """.trimIndent()+"\n")
        // Ubuntu Base ships dpkg.cfg.d/excludes: our legal-file exceptions must sort after it.
        managed("etc/dpkg/dpkg.cfg.d/zz-tanuki-minimal","""
            # Keep legal notices; omit optional manual, documentation and translation payloads.
            path-exclude=/usr/share/man/*
            path-exclude=/usr/share/doc/*
            path-include=/usr/share/doc/*/copyright
            path-include=/usr/share/doc/*/LICENSE*
            path-include=/usr/share/doc/*/COPYING*
            path-exclude=/usr/share/locale/*
        """.trimIndent()+"\n")
    }
    private suspend fun cleanUbuntuCache() {
        // Do not run guest apt clean: its configuration/hooks can redirect deletion to a bind.
        val cache=File(paths.root.canonicalFile,"var/cache/apt")
        fun requireDefault(file:File) {
            check(!Files.isSymbolicLink(file.toPath()) && file.canonicalFile==file.absoluteFile) { "Ubuntu 默认缓存路径被重定向，已保留缓存和项目" }
        }
        requireDefault(cache)
        if(!cache.exists())return
        check(cache.isDirectory) { "Ubuntu 默认缓存路径不是目录" }
        val archives=File(cache,"archives")
        requireDefault(archives)
        if(archives.exists()) {
            check(archives.isDirectory) { "Ubuntu 安装包缓存路径不是目录" }
            for(entry in archives.listFiles() ?: error("无法读取 Ubuntu 安装包缓存")) {
                currentCoroutineContext().ensureActive()
                if(entry.name=="lock")continue
                if(Files.isSymbolicLink(entry.toPath()))Files.delete(entry.toPath()) else deleteTree(entry)
                if(entry.name=="partial")check(entry.mkdirs()) { "无法恢复 Ubuntu partial 缓存目录" }
            }
        }
        for(name in listOf("pkgcache.bin","srcpkgcache.bin")) {
            currentCoroutineContext().ensureActive()
            Files.deleteIfExists(File(cache,name).toPath())
        }
    }
    fun slimUbuntu() = launchExclusive("ubuntu-cache") {
        check(paths.metadata().optBoolean("ready") && File(paths.root,".base-verified").exists()) { "请先完成 Ubuntu 初始化" }
        phase("整理 Ubuntu 可重建安装缓存",EnvironmentState.VERIFYING)
        configureMinimalUbuntu()
        cleanUbuntuCache()
        state.update { it.copy(environment=EnvironmentState.READY,stage="Ubuntu 精简策略已启用，默认 apt 安装包缓存已清理；项目和已安装工具保留") }
        event("Ubuntu Base 按需安装工具；保留版权，后续包不默认安装可选文档和翻译")
    }
    suspend fun ensureBase() {
        phase("设备检查", EnvironmentState.CHECKING)
        check(Build.SUPPORTED_ABIS.contains("arm64-v8a")) { "第一版仅支持 ARM64：${Build.SUPPORTED_ABIS.toList()}" }
        check(paths.base.usableSpace >= 1536L * 1024 * 1024) { "至少需要 1.5GB 可用空间，建议预留 4GB" }
        event(deviceInfo())
        if (File(paths.root, ".base-verified").exists()) {
            check(components.installedUbuntuVersion()!=null) { "Ubuntu 镜像校验记录未知，已保留环境；请使用组件重新安装入口" }
            return
        }
        if (File(paths.root, ".base-uncommitted").exists()) { finishBase(); return }
        val artifact=components.selected(RuntimeComponent.UBUNTU)
        phase("Ubuntu ${artifact.version} 下载", EnvironmentState.DOWNLOADING)
        state.update { it.copy(download=RuntimeDownloadProgress("ubuntu","正在选择下载源",0,artifact.size,0)) }
        val archive = components.downloadArtifact(RuntimeComponent.UBUNTU)
        phase("Ubuntu 校验与解压", EnvironmentState.INSTALLING)
        deleteTree(paths.stage)
        // 解压总量未知：只上报真实写入量，界面用不定进度 + 已写入 MB，不造百分比。
        var lastExtractReport = 0L
        SafeTar.extract(archive, paths.stage) { written ->
            if (written - lastExtractReport >= 32L * 1024 * 1024) {
                lastExtractReport = written
                state.update { it.copy(stage = "正在解压 Ubuntu（已写入 ${written / (1024 * 1024)} MB）") }
            }
        }
        File(paths.stage, "tmp").mkdirs(); File(paths.stage, "home/tanuki").mkdirs()
        File(paths.stage, "etc/resolv.conf").let { file ->
            Files.deleteIfExists(file.toPath())
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val dns = cm.getLinkProperties(cm.activeNetwork)?.dnsServers.orEmpty().mapNotNull { it.hostAddress }.filter { ':' !in it }
            file.writeText((dns.ifEmpty { listOf("1.1.1.1", "8.8.8.8") }).joinToString("\n", postfix = "\n") { "nameserver $it" })
        }
        phase("验证暂存 Ubuntu 启动能力", EnvironmentState.VERIFYING)
        val test = launcher.run(paths.stage, listOf("/bin/true"), 30000)
        check(test.exitCode == 0) { "Ubuntu 启动失败 exit=${test.exitCode} ${test.stderr}; target=${context.applicationInfo.targetSdkVersion}" }
        File(paths.stage, ".base-uncommitted").writeText("${artifact.version} ${artifact.sha256}")
        check(!paths.root.exists()) { "已有不完整环境，请通过专用修复入口处理" }
        check(paths.stage.renameTo(paths.root)) { "无法提交 Ubuntu 环境" }
        finishBase()
    }
    private suspend fun finishBase() {
        val artifact=RuntimeCatalog.ubuntuFromMarker(File(paths.root,".base-uncommitted").readText()) ?: error("Ubuntu 暂存镜像身份不匹配，已保留文件")
        val journal = File(paths.root, ".hp-hardlinks.json")
        if (journal.exists()) {
            val links = org.json.JSONArray(journal.readText())
            for (i in 0 until links.length()) {
                val link = links.getJSONObject(i)
                val path = link.getString("path"); val target = link.getString("target")
                SafeTar.entryPath(paths.root.toPath(), path); SafeTar.entryPath(paths.root.toPath(), target)
                checked(listOf("/bin/ln", "-f", "/$target", "/$path"), 30000)
            }
            journal.delete()
        }
        File(paths.root, ".base-verified").writeText("${artifact.version} ${artifact.sha256}")
        File(paths.root, ".base-uncommitted").delete()
    }
    suspend fun download(url: String, name: String, sha: String, component: String = "", source: String = "", expectedSize: Long = -1): File {
        components.sourceTests.values.forEach { it.cancel() }
        val started=System.nanoTime()
        val target=File(paths.downloads,name)
        val existed=target.isFile && !Files.isSymbolicLink(target.toPath())
        var initial=-1L
        var reused=false
        var reported=false
        var lastReport=0L
        if(component.isNotEmpty())state.update { it.copy(download=RuntimeDownloadProgress(component,source,0,expectedSize,0),phasePercent=null) }
        return Downloader { done,total ->
            if(initial<0) { initial=done;reused=existed && total>0 && done==total }
            val now=System.nanoTime()
            if(!reported || now-lastReport>=100_000_000 || total>0 && done>=total) {
                val elapsed=(now-started)/1_000_000_000.0
                val speed=if(elapsed>=0.25)((done-initial)/elapsed).toLong().coerceAtLeast(0) else 0
                state.update { it.copy(bytes=done,total=total,download=if(component.isNotEmpty())RuntimeDownloadProgress(component,source,done,total,speed,reused) else it.download) }
                reported=true;lastReport=now
            }
        }.fetch(url,target,sha,expectedBytes=expectedSize).also { file ->
            if(reused)event("复用已校验下载缓存：$name")
            if(component.isNotEmpty())state.update { it.copy(bytes=file.length(),total=file.length(),download=RuntimeDownloadProgress(component,source,file.length(),file.length(),0,reused)) }
        }
    }
    private suspend fun installNode() = components.ensureNode()
    suspend fun ensurePnpm(nodeHome:File?=null) {
        components.migrateLegacyLayers()
        val destination=File(paths.pnpm,PNPM)
        val previous=File(paths.pnpm,"$PNPM.previous")
        val stage=File(paths.pnpm,"$PNPM.staging")
        for(directory in listOf(destination,previous,stage))check(!Files.isSymbolicLink(directory.toPath())) { "pnpm 组件路径被重定向" }
        fun verified(directory:File)=File(directory,".verified").takeIf { it.isFile }?.readText()==PNPM_SHA && File(directory,"package/bin/pnpm.cjs").isFile
        if(previous.exists()) {
            if(verified(destination))deleteTree(previous)
            else { deleteTree(destination);check(previous.renameTo(destination)) { "pnpm 恢复未完成" } }
        }
        suspend fun probe(versionDirectory:String) {
            val result=launcher.run(paths.root,listOf("/opt/node/bin/node","/opt/pnpm/$versionDirectory/package/bin/pnpm.cjs","--version"),nodeHome=nodeHome)
            check(result.exitCode==0 && result.stdout.trim()==PNPM) { "候选 Node.js 无法运行 pnpm $PNPM，当前 Node 保留：${result.stderr.takeLast(1500)}" }
        }
        if(verified(destination)) { if(nodeHome!=null)probe(PNPM);return }
        state.update { it.copy(stage="准备官方插件管理工具 pnpm $PNPM") }
        event("准备 pnpm $PNPM（官方 npm 发行包）")
        val archive=download("https://registry.npmjs.org/pnpm/-/pnpm-$PNPM.tgz","pnpm-$PNPM.tgz",PNPM_SHA,"node","官方 npm（pnpm）",expectedSize=5736887)
        state.update { it.copy(stage="校验与解压 pnpm $PNPM",download=null,bytes=0,total=-1) }
        deleteTree(stage)
        try {
            SafeTar.extract(archive,stage,128L*1024*1024)
            check(File(stage,"package/bin/pnpm.cjs").isFile) { "pnpm 入口缺失" }
            File(stage,"bin").mkdirs()
            Files.createSymbolicLink(File(stage,"bin/pnpm").toPath(),java.nio.file.Paths.get("../package/bin/pnpm.cjs"))
            probe("$PNPM.staging")
            File(stage,".verified").writeText(PNPM_SHA)
            if(destination.exists())check(destination.renameTo(previous)) { "无法保留旧 pnpm" }
            try { check(stage.renameTo(destination)) { "无法提交 pnpm 组件" } }
            catch(t:Throwable) { if(previous.exists())check(previous.renameTo(destination));throw t }
            deleteTree(previous)
        } finally { deleteTree(stage) }
    }
    suspend fun installEngine(version: String, expectedIntegrity: String? = if (version == DSH) DSH_INTEGRITY else null,
                              replaceExisting:Boolean=false, validate:(suspend (File)->Unit)?=null) {
        require(version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?")))
        val final = File(paths.engines, version)
        if (!replaceExisting && File(final, ".installed").exists()) {
            val lock = File(final, "package-lock.json")
            require(lock.exists() && Downloader.hash(lock) == File(final, ".installed").readText()) { "已安装 DSH 的依赖锁不一致，请保留用户数据后修复此版本" }
            validate?.invoke(final)
            return
        }
        require(expectedIntegrity != null) { "缺少选定 DSH 发布的完整性信息，请刷新版本列表" }
        val container = File(paths.runtime, "engine-install"); deleteTree(container)
        // Keep only isolated public npm metadata and verified/partial archives across retries.
        val isolated = File(paths.runtime, "install-data")
        val stage = NpmPackageDownloads(this).install(version, expectedIntegrity, container, isolated)
        val lock = File(stage, "package-lock.json"); check(lock.exists())
        val integrity = JSONObject(lock.readText()).getJSONObject("packages").getJSONObject("node_modules/@deepseek-ai/dsh").getString("integrity")
        require(expectedIntegrity != null && integrity == expectedIntegrity) { "DSH 完整性与选定版本元数据不一致" }
        File(stage, ".installed").writeText(Downloader.hash(lock))
        validate?.invoke(stage)
        check(File(stage,"node_modules/@deepseek-ai/dsh/lib/bin.js").isFile) { "候选 DSH 程序不完整" }
        if(final.exists()) {
            check(replaceExisting) { "候选目录冲突，当前版本保留" }
            components.replaceEngine(version) { check(stage.renameTo(final)) { "无法提交候选 DSH" } }
        } else check(stage.renameTo(final)) { "候选目录冲突" }
        deleteTree(container); deleteTree(isolated)
    }
    private suspend fun checked(args: List<String>, timeout: Long = 120000) = launcher.run(paths.root, args, timeout).also {
        check(it.exitCode == 0) { "${args.first()} 失败 exit=${it.exitCode}: ${it.stderr.takeLast(5000)}" }
    }
    fun backgroundProbe() = launchExclusive("background-probe") {
        check(paths.metadata().optBoolean("ready")) { "请先完成环境初始化" }
        state.update { it.copy(health = HealthState.CHECKING, stage = "后台自检运行中：0/60", bytes = 0, total = -1) }
        File(paths.state,"background-diagnostics.json").delete()
        val workspace=File(paths.runtime,"background-workspace")
        val program="const fs=require('fs');let count=0;const timer=setInterval(()=>{count++;fs.writeFileSync('background-proof.json',JSON.stringify({count,at:Date.now(),pid:process.pid}));console.log('BACKGROUND_TICK '+count);if(count===60)clearInterval(timer)},1000)"
        val c=launcher.start(paths.root,listOf("/opt/node/bin/node","-e",program),workspace=workspace)
        val result=try { withTimeout(90000) { launcher.collect(c) { line ->
            if(line.startsWith("BACKGROUND_TICK ")) state.update { it.copy(stage="后台自检运行中：${line.substringAfter(' ')}/60") }
        } } }finally{if(c.process.isAlive)c.stop()}
        check(result.exitCode==0) { "后台自检中断 exit=${result.exitCode}，不会自动重放任务" }
        val proof=JSONObject(File(workspace,"background-proof.json").readText())
        check(proof.getInt("count")==60)
        proof.put("hostUid",Process.myUid()).put("appPid",Process.myPid()).put("debuggable",BuildConfig.DEBUG).put("exit",result.exitCode)
        paths.atomic("background-diagnostics.json",proof.toString(2))
        state.update { it.copy(health=HealthState.HEALTHY,stage="后台自检完成：60/60，详见诊断结果") }
        event("后台自检实际完成 60 次计时与文件写入，exit=0")
    }
    fun healthCheck() = launchExclusive("health") {
        ensurePnpm()
        state.update { it.copy(health = HealthState.CHECKING) }
        val r = diagnostics(); state.update { it.copy(health = if (r.exitCode == 0) HealthState.HEALTHY else HealthState.FAILED) }
        check(r.exitCode == 0) { r.stderr }
    }
    private val sandboxProbeProgram = """
        import {createRequire} from 'node:module';
        import {spawnSync} from 'node:child_process';
        const bwrap = spawnSync('bwrap', ['--ro-bind','/','/','--dev','/dev','--unshare-pid','--proc','/proc','--die-with-parent','--','true'], {timeout:7000,stdio:'pipe'});
        console.log('BWRAP_ENFORCEMENT=' + (bwrap.status === 0 ? 'full' : bwrap.error && bwrap.error.code !== 'ENOENT' ? 'unknown' : 'unusable'));
        try {
            const req = createRequire(process.argv[1]);
            const m = await import(req.resolve('@deepseek-ai/node-addon-system/landlock-run'));
            console.log('LANDLOCK_ENFORCEMENT=' + m.probe(m.launcherPath(), {timeoutMs:5000}));
        } catch (_) { console.log('LANDLOCK_ENFORCEMENT=unknown'); }
    """.trimIndent()
    private fun sandboxCapability(output: String): Confinement = when {
        "BWRAP_ENFORCEMENT=full" in output || "LANDLOCK_ENFORCEMENT=full" in output -> Confinement.FULL
        "LANDLOCK_ENFORCEMENT=partial" in output -> Confinement.PARTIAL
        "BWRAP_ENFORCEMENT=unusable" in output && "LANDLOCK_ENFORCEMENT=unusable" in output -> Confinement.UNAVAILABLE
        else -> Confinement.UNKNOWN
    }
    suspend fun probeSandbox(engineVersion: String, engineHome:File=paths.engines) {
        require(engineVersion.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?")))
        val isolated=File(paths.runtime,"sandbox-probe")
        val guestEngine=if(engineHome==paths.engines)"/opt/engines/$engineVersion" else "/opt/engines"
        val result=try {
            launcher.run(paths.root,listOf("/opt/node/bin/node","--input-type=module","-e",sandboxProbeProgram,"$guestEngine/node_modules/@deepseek-ai/dsh/lib/bin.js"),30000,
                File(isolated,"home"),File(isolated,"dsh"),File(isolated,"workspace"),engineHome=engineHome)
        } finally { deleteTree(isolated) }
        val capability=if(result.exitCode == 0) sandboxCapability(result.stdout) else Confinement.UNKNOWN
        paths.atomic("confinement",capability.name)
        state.update { it.copy(confinement=capability) }
        event(when(capability) {
            Confinement.UNAVAILABLE -> "本机没有可用的命令沙箱；workspace-write 文件工具可用，shell 需在官方 DSH 中明确选择兼容权限模式"
            Confinement.PARTIAL -> "本机命令沙箱提供部分能力，请以 DSH 权限设置的能力提示为准"
            Confinement.FULL -> "本机命令沙箱检测通过"
            Confinement.UNKNOWN -> "命令沙箱能力尚未确认，可在运行检查中重试"
        })
    }
    suspend fun diagnostics(): CommandResult {
        val script = """
            set -e
            cat /etc/os-release
            uname -m
            node --version
            npm --version
            pnpm --version
            git --version
            python3 --version
            node -e 'console.log(JSON.stringify({platform:process.platform,arch:process.arch,execPath:process.execPath,cwd:process.cwd()}));const fs=require("fs");fs.writeFileSync(".tanuki-probe","persistent-file");if(fs.readFileSync(".tanuki-probe","utf8")!=="persistent-file")process.exit(2);const r=require("child_process").spawnSync("/bin/sh",["-c","exit 17"]);console.log("child_exit="+r.status);if(r.status!==17)process.exit(3)'
            node --input-type=module -e "${'$'}2" "${'$'}1"
            curl --fail --max-time 30 --silent --show-error --head --output /dev/null --write-out 'HTTPS_STATUS=%{http_code}\n' https://registry.npmjs.org/
        """.trimIndent()
        val engine = paths.metadata().optString("dsh", DSH)
        require(engine.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?")))
        val isolated=File(paths.runtime,"diagnostics-data")
        val result = launcher.run(paths.root, listOf("/bin/bash", "-c", script, "diagnostics", "/opt/engines/$engine/node_modules/@deepseek-ai/dsh/lib/bin.js", sandboxProbeProgram),
            home=File(isolated,"home"),dshHome=File(isolated,"dsh"),workspace=File(isolated,"workspace"))
        check(result.exitCode == 0) { "健康检查失败 exit=${result.exitCode}: ${result.stderr}" }
        val confinement = sandboxCapability(result.stdout)
        state.update { it.copy(confinement = confinement) }
        return result.also {
            paths.atomic("confinement", confinement.name)
            paths.atomic("diagnostics.json", JSONObject().put("device", deviceInfo()).put("uid", Process.myUid()).put("appPid", Process.myPid()).put("debuggable", BuildConfig.DEBUG).put("exit", it.exitCode).put("stdout", it.stdout).put("stderr", it.stderr).toString(2))
        }
    }
    @Synchronized fun refreshWorkspaceStorage() {
        if (workspaceRefresh?.isActive == true) return
        workspaceRefresh = scope.launch { workspaceStorage.refresh() }
    }
    suspend fun configureWorkspaceCapacity(limitBytes: Long?) = mutation.withLock {
        check(!hasLiveChild && state.value.service in setOf(ServiceState.STOPPED, ServiceState.FAILED, ServiceState.INTERRUPTED)) { "请先停止 DSH，再调整项目容量预算" }
        workspaceStorage.configure(limitBytes)
        event("项目容量预算已保存；现有文件保留")
        refreshWorkspaceStorage()
    }
    /**
     * 首帧前提前拉起已安装且空闲的运行时。
     *
     * 只发起既有前台服务 START 动作，安装/密钥/用户数据仍走 [start] 的原有流程；
     * 条件不满足、重复调用或系统拒绝时返回 false，不改变当前状态。
     */
    fun requestEarlyStart(enabled: Boolean): Boolean {
        return earlyStartGate.request(earlyConditions(enabled)) {
            try {
                context.startForegroundService(Intent(context, RuntimeService::class.java).setAction("START_EARLY"))
                event("启动优化：已请求在首帧前提前拉起已就绪的 DSH")
            } catch (error: Throwable) {
                event("提前启动未能发起，仍可手动启动：${Redact.text(error.message ?: error.javaClass.simpleName)}")
                throw error
            }
        }
    }
    /** 由 Application 注入独立更新/恢复任务的忙碌信号；默认不阻塞。 */
    fun registerBusySignal(signal: () -> Boolean) {
        externalBusy = signal
    }
    private fun externalBusyActive(): Boolean = runCatching { externalBusy() }.getOrDefault(false)
    private fun earlyConditions(enabled: Boolean): EarlyRuntimeStart.Conditions {
        val snapshot = state.value
        return EarlyRuntimeStart.conditionsFrom(
            enabled = enabled,
            snapshot = snapshot,
            runtimeOperationActive = job?.isActive == true || serverJob?.isActive == true,
            independentOperationActive = externalBusyActive(),
            liveChild = child != null,
            stopping = stopping,
        )
    }
    private fun publishRecovery(recovery: RuntimeRecoveryState) {
        recoveryState.value = recovery
        state.update { it.copy(recovery = recovery) }
        runCatching { paths.atomic("failure-state", failurePolicy.encode(recovery)) }
    }

    private fun failureEvidence(attempt: RunAttempt?, launched: Boolean, timedOut: Boolean, errorText: String, result: CommandResult?): FailureEvidence {
        val output = buildString {
            result?.stderr?.let { append(it) }
            result?.stdout?.let { if (isNotEmpty()) append('\n'); append(it) }
        }
        return FailureEvidence(
            launched = launched,
            failedAtMillis = System.currentTimeMillis(),
            readyAtMillis = attempt?.readyAtMillis?.takeIf { it > 0L },
            // 就绪超时后的退出码来自主动清理，不能作为原始失败证据。
            exitCode = if (timedOut) null else result?.exitCode,
            output = output.takeLast(32_000),
            errorText = Redact.text(errorText).takeLast(4_000),
            timedOut = timedOut,
            diskFreeBytes = runCatching { paths.base.usableSpace }.getOrDefault(-1L)
        )
    }

    /** 记录一次失败并按策略决定是否自动重启；不删除用户数据、不重放任务。 */
    private fun applyFailure(kind: FailureKind, result: CommandResult?) {
        val nowMillis = System.currentTimeMillis()
        val decision = failurePolicy.onFailure(recoveryState.value, kind, nowMillis, result?.exitCode)
        publishRecovery(decision.state)
        val message = Redact.text(decision.summary)
        runCatching { paths.atomic("last-error", message) }
        runCatching { paths.atomic("service", if (kind == FailureKind.LAUNCH_FAILED || kind == FailureKind.STARTUP_FAILED) "failed" else "interrupted") }
        val failure = FailureCatalog.fromRuntimeFailure(kind, decision.state, message, nowMillis)
        state.update {
            it.copy(
                service = if (kind == FailureKind.LAUNCH_FAILED || kind == FailureKind.STARTUP_FAILED) ServiceState.FAILED else ServiceState.INTERRUPTED,
                webHealthy = false,
                web = if (remoteMachine.remoteActive) it.web else WebState.DISCONNECTED,
                error = message,
                failure = failure
            )
        }
        event(message)
        if (decision.autoRestart) scheduleAutoRestart(decision.restartDelayMillis, decision.state.mode)
    }

    private fun scheduleAutoRestart(delayMillis: Long, expectedMode: RecoveryMode) {
        autoRestartJob?.cancel()
        val generation = synchronized(this) { runtimeGeneration }
        autoRestartJob = scope.launch {
            if (delayMillis > 0L) delay(delayMillis)
            synchronized(this@RuntimeManager) {
                if (stopping || serverJob?.isActive == true || child != null) return@launch
                if (runtimeGeneration != generation || state.value.service == ServiceState.STOPPED) return@launch
                if (recoveryState.value.mode != expectedMode) return@launch
            }
            val attempt = recoveryState.value.restartAttempts
            val suffix = if (expectedMode == RecoveryMode.SAFE) "，使用安全模式" else ""
            event("按失败策略自动重启（$attempt/${failurePolicy.maxRestartAttempts}）$suffix")
            start(automatic = true)
        }
    }

    private fun scheduleStability(c: Child) {
        stabilityJob?.cancel()
        stabilityJob = scope.launch {
            delay(failurePolicy.probationWindowMillis)
            if (child !== c || !c.process.isAlive || stopping) return@launch
            val before = recoveryState.value
            val stable = failurePolicy.onStable(before, System.currentTimeMillis())
            if (stable != before) {
                publishRecovery(stable)
                when {
                    before.mode == RecoveryMode.PROBATION && stable.mode == RecoveryMode.NORMAL -> event("正常模式试用通过，已恢复 normal")
                    stable.mode == RecoveryMode.SAFE -> event("安全模式运行稳定；不会自动切回正常模式，可在界面点击尝试恢复")
                    else -> event("服务稳定运行，启动失败计数已清零")
                }
            }
        }
    }

    /** 用户显式从安全模式试用正常配置；失败会在试用窗口内自动回到安全模式。 */
    suspend fun tryNormalMode() {
        autoRestartJob?.cancel()
        if (state.value.service in setOf(ServiceState.STARTING, ServiceState.RUNNING, ServiceState.STOPPING) || child != null || serverJob?.isActive == true) {
            stop(userRequested = true)
        }
        publishRecovery(failurePolicy.toProbation(recoveryState.value, System.currentTimeMillis()))
        event("开始试用正常模式；2 分钟内失败会自动回到安全模式")
        start()
    }

    /** 用户显式退出正常模式试用，回到安全模式。 */
    suspend fun returnToSafeMode() {
        autoRestartJob?.cancel()
        if (state.value.service in setOf(ServiceState.STARTING, ServiceState.RUNNING, ServiceState.STOPPING) || child != null || serverJob?.isActive == true) {
            stop(userRequested = true)
        }
        publishRecovery(failurePolicy.toSafeByUser(recoveryState.value, System.currentTimeMillis()))
        event("已退出正常模式试用，回到安全模式")
        start()
    }

    fun start(early: Boolean = false, automatic: Boolean = false) {
        synchronized(this) {
            // 服务端收到 START_EARLY 时重新校验，避免请求后环境变化或已被安装/手动启动占用。
            if (early && !EarlyRuntimeStart.shouldStart(earlyConditions(true))) return
            if(stopping || state.value.service==ServiceState.STOPPING){event("服务正在停止，请稍后启动");return}
            if (serverJob?.isActive == true || child != null) return
            if (!automatic) autoRestartJob?.cancel()
            val nowMillis = System.currentTimeMillis()
            val prepared = if (automatic) failurePolicy.prepareAutomaticStart(recoveryState.value, nowMillis)
            else failurePolicy.prepareManualStart(recoveryState.value, nowMillis)
            if (prepared != recoveryState.value) publishRecovery(prepared)
            val attempt = RunAttempt(prepared.mode)
            runtimeGeneration++
            state.update { it.copy(service=ServiceState.STARTING,error="",recovery=prepared,failedOperation=null) }
            serverJob = scope.launch {
                mutation.withLock {
                    var completion: Deferred<CommandResult>? = null
                    var launched = false
                    var timedOut = false
                    try {
                        check(state.value.environment == EnvironmentState.READY) { "请先完成初始化" }
                        check(state.value.update != UpdateState.RECOVERY_REQUIRED) { "请先恢复匹配快照或完成中断版本检查" }
                        workspaceStorage.requireCanStart()
                        state.update { it.copy(service = ServiceState.STARTING, web = if (remoteMachine.remoteActive) it.web else WebState.DISCONNECTED, webHealthy = false, error = "") }; paths.atomic("service", "starting")
                        ensurePnpm()
                        ensureWorkspaceSupport()
                        clearLocalEndpoint()
                        val v = paths.metadata().getString("dsh")
                        components.checkDshNodeCompatibility(v)
                        check(paths.base.usableSpace > WorkspaceCapacity.DISK_RESERVE) { "设备剩余空间不足 64 MiB，请先清理存储" }
                        // 启动期只读路径缓存只覆盖官方 web 启动；准备失败时按原生方式启动。
                        val startupTools = DshStartup.prepare(context, paths)
                        val startupConfig = DshStartupConfig.fromEnvironment()
                        val safe = attempt.mode == RecoveryMode.SAFE
                        val safeDshData = paths.safeDshData
                        val rescueProfileExists = File(safeDshData, "profiles/rescue").isDirectory
                        val dshArguments = failurePolicy.startArguments(v, safe, rescueProfileExists)
                        // 正常模式只给客体受控客户端；安全模式不加载用户插件/配置，也不挂载手机写入工具。
                        if (!safe) phoneFiles.start()
                        val c = launcher.start(
                            paths.root,
                            listOf("/opt/node/bin/node") + dshArguments,
                            home = if (safe) paths.safeHome else paths.home,
                            dshHome = if (safe) safeDshData else paths.dshData,
                            workspace = paths.workspaces,
                            phoneFiles = !safe,
                            startupTools = startupTools,
                            startupConfig = startupConfig
                        )
                        child = c
                        launched = true
                        monitorWorkspace(c)
                        // Collection lives independently of the Activity and of the environment mutex.
                        val comp = scope.async {
                            launcher.collect(c) { raw ->
                                captureUrl(raw)
                                if (remoteMachine.localAuthenticatedUrl != null && attempt.readyAtMillis == 0L) {
                                    attempt.readyAtMillis = System.currentTimeMillis()
                                }
                            }
                        }
                        completion = comp
                        withTimeout(180000) {
                            while (attempt.readyAtMillis == 0L) {
                                check(!cookieIsolationFailed) { "管理页 Cookie 隔离失败，已停止加载；请重启应用" }
                                check(c.process.isAlive) { "DSH 在就绪前退出" }
                                delay(200)
                            }
                            while (!httpReady(remoteMachine.localOrigin)) { check(c.process.isAlive) { "DSH 在就绪后退出" }; delay(300) }
                        }
                        val readyNow = System.currentTimeMillis()
                        if (attempt.readyAtMillis == 0L) attempt.readyAtMillis = readyNow
                        val preparedReady = failurePolicy.onReady(recoveryState.value, readyNow)
                        publishRecovery(preparedReady)
                        File(paths.engines, "$v/.web-verified").writeText("${Build.MODEL}; API ${Build.VERSION.SDK_INT}; target ${context.applicationInfo.targetSdkVersion}; debug ${BuildConfig.DEBUG}")
                        state.update { it.copy(service = ServiceState.RUNNING, webHealthy = true, recovery = preparedReady, error = "", failure = null) }; paths.atomic("service", "running"); paths.atomic("last-error","")
                        event(if (preparedReady.mode == RecoveryMode.SAFE) "DSH 已用安全模式启动：独立最小配置，未加载用户插件与配置" else "DSH 本机服务已就绪")
                        scheduleStability(c)
                        if(state.value.update==UpdateState.RESTORED_PENDING_CHECK) {
                            if (failurePolicy.canVerifyRestoredData(preparedReady.mode)) {
                                val restored=JSONObject(paths.read("restore-transaction") ?: "{}")
                                paths.atomic("restore-transaction",restored.put("phase","verified").toString())
                                state.update { it.copy(update=UpdateState.SUCCESS) }
                                event("恢复后的版本已通过本机启动检查；会话内容仍可在官方页面核对")
                            } else {
                                event("安全模式使用独立配置，未加载恢复的用户数据；恢复检查仍待正常模式完成")
                            }
                        }
                        scope.launch {
                            while (child === c && c.process.isAlive) {
                                delay(10000)
                                if (child !== c || !c.process.isAlive) break
                                val healthy = httpReady(remoteMachine.localOrigin)
                                state.update { it.copy(webHealthy = healthy, web = if (!healthy && !remoteMachine.remoteActive) WebState.DISCONNECTED else it.web) }
                            }
                        }
                        scope.launch {
                            val result = try { comp.await() } catch (t: Throwable) {
                                fail(t); if (c.process.isAlive) c.stop()
                                CommandResult(-1, "", Redact.text(t.message ?: "日志管道异常"))
                            }
                            stabilityJob?.cancel()
                            if (child === c && !stopping) {
                                phoneFiles.stop()
                                workspaceMonitor?.cancel()
                                child = null; clearLocalEndpoint()
                                val stopped = state.value.service == ServiceState.STOPPING
                                if (stopped) {
                                    state.update { it.copy(service = ServiceState.STOPPED, webHealthy = false, web = if (remoteMachine.remoteActive) it.web else WebState.DISCONNECTED) }
                                    paths.atomic("service", "stopped")
                                } else {
                                    val kind = failurePolicy.classify(failureEvidence(attempt, true, false, "", result))
                                    applyFailure(kind, result)
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        val errorText = Redact.text(t.message ?: t.javaClass.simpleName)
                        timedOut = t is TimeoutCancellationException
                        val cleanup = runCatching {
                            withContext(NonCancellable) {
                                child?.stop()
                                phoneFiles.stop()
                                child = null; clearLocalEndpoint()
                                workspaceMonitor?.cancel(); workspaceMonitor = null
                            }
                        }.exceptionOrNull()
                        if (t is CancellationException && t !is TimeoutCancellationException) {
                            cleanup?.let(::fail)
                            throw t // A requested stop owns the final state; cancellation is not a failure.
                        }
                        cleanup?.let(t::addSuppressed)
                        val result = completion?.let { runCatching { withTimeoutOrNull(3000) { it.await() } }.getOrNull() }
                        val evidence = failureEvidence(attempt, launched, timedOut, errorText, result)
                        val kind = failurePolicy.classify(evidence)
                        fail(t)
                        applyFailure(kind, result)
                    }
                }
            }
        }
    }
    /** 由管理 WebView 注册；切换 Cookie 前用于停止旧文档和在途请求。 */
    fun installCookieSwitchGuard(guard: DshCookieSwitchGuard?) {
        cookieSwitchGuard = guard
    }
    /** 管理页端点只有在 Cookie 作用域与状态机一致时才允许加载/认证/下载。 */
    fun cookieReady(): Boolean = cookieIsolation.currentScope() == remoteMachine.status.cookieScope
    /** 仅设备测试使用：注入本地端点，避免反射访问已移除的字段。 */
    internal fun attachLocalEndpointForTesting(origin: String, authenticatedUrl: String = "$origin/") {
        remoteMachine.localConnected(origin, authenticatedUrl)
        state.update { it.copy(remote = remoteMachine.status) }
    }
    /** 只清理本地 DSH 端点；远程隧道仍在时保持远程 origin。 */
    private fun clearLocalEndpoint() {
        remoteMachine.localCleared()
        state.update { it.copy(remote = remoteMachine.status) }
    }
    private fun captureUrl(line: String) {
        if (!line.contains("dsh web:")) return
        val candidate = Regex("http://127\\.0\\.0\\.1:\\d+/[^\\s\\u001B]*").find(line)?.value ?: return
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return
        if (uri.host == "127.0.0.1" && uri.port in 1..65535 && uri.userInfo == null && uri.rawQuery != null) {
            if (!remoteMachine.remoteActive) {
                // 先确认 Cookie 已切回本地，再发布可加载的本地端点；失败则不暴露 origin。
                val switched = runCatching { cookieIsolation.switchTo(DshCookieIsolation.LOCAL_SCOPE) }.isSuccess
                cookieIsolationFailed = !switched
                if (!switched) {
                    cookieIsolationFailed = true
                    event("管理页 Cookie 隔离失败，已拒绝加载本地端点；请重启应用")
                    state.update { it.copy(web = WebState.RENDER_ERROR, error = "管理页 Cookie 隔离失败，请重启应用") }
                    return
                }
            }
            remoteMachine.localConnected("http://127.0.0.1:${uri.port}", candidate)
            state.update { it.copy(remote = remoteMachine.status) }
        }
    }
    /** 本地就绪检查必须绑定本地端点；远程模式不能拿远程 origin 冒充本地进程健康。 */
    private fun httpReady(endpoint: String?): Boolean = runCatching {
        val c = URL("${endpoint ?: return false}/").openConnection() as HttpURLConnection
        c.connectTimeout = 2000; c.readTimeout = 2000; c.instanceFollowRedirects = false
        try { c.responseCode in 200..399 || c.responseCode == 401 } finally { c.disconnect() }
    }.getOrDefault(false)
    private fun monitorWorkspace(expected: Child) {
        workspaceMonitor?.cancel()
        workspaceMonitor = scope.launch {
            while (child === expected && expected.process.isAlive) {
                delay(15000)
                if (child !== expected || !expected.process.isAlive) break
                try {
                    if (enforceWorkspaceBudget(expected)) return@launch
                } catch (error: CancellationException) { throw error }
                catch (_: Throwable) {
                    event("空间保护未能停止全部进程，将在下次检查时重试；也可使用停止入口")
                }
            }
        }
    }
    internal suspend fun enforceWorkspaceBudget(expected: Child): Boolean {
        if (child !== expected || !expected.process.isAlive || !expected.identified) return false
        val reason = workspaceStorage.runtimeViolation() ?: return false
        stopRuntime(userRequested = false, expected = expected, reason = reason)
        return true
    }
    fun stopAfterServiceLoss(): Job {
        val expectedGeneration = synchronized(this) { runtimeGeneration }
        return scope.launch {
            try { stopRuntime(userRequested = false, expectedGeneration = expectedGeneration) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Throwable) { fail(error) }
        }
    }
    suspend fun stop(userRequested: Boolean = true) = stopRuntime(userRequested)
    private suspend fun stopRuntime(userRequested: Boolean, expected: Child? = null, reason: String? = null, expectedGeneration: Long? = null) = stopMutex.withLock {
        val caller = currentCoroutineContext()[Job]
        // Publish STOPPING atomically with identity validation, before switching contexts.
        val before = synchronized(this) {
            if ((expected != null && child !== expected) || (expectedGeneration != null && runtimeGeneration != expectedGeneration)) return@withLock
            val previous = state.value.service
            stopping = true
            // 停止入口优先于自动重启/试用计时，避免用户停止后又被后台拉起。
            runtimeGeneration++
            autoRestartJob?.cancel(); autoRestartJob = null
            stabilityJob?.cancel(); stabilityJob = null
            phoneFiles.setWriteAllowed(false)
            state.update { it.copy(service = ServiceState.STOPPING) }
            previous
        }
        withContext(NonCancellable) {
            try {
                val observer = workspaceMonitor
                if (observer != caller) observer?.cancelAndJoin()
                val stopWasRequested = userRequested || paths.read("service") == "stopping-user"
                if (stopWasRequested) runCatching { paths.atomic("service", "stopping-user") }
                serverJob?.cancelAndJoin()
                serverJob = null
                child?.stop()
                phoneFiles.stop()
                child = null
                workspaceMonitor = null
                clearLocalEndpoint()
                val final = if (stopWasRequested) ServiceState.STOPPED else when (before) {
                    ServiceState.RUNNING, ServiceState.STARTING, ServiceState.STOPPING -> ServiceState.INTERRUPTED
                    else -> before
                }
                runCatching { paths.atomic("service", when (final) { ServiceState.STOPPED -> "stopped"; ServiceState.FAILED -> "failed"; else -> "interrupted" }) }
                val interrupted = reason?.let { "$it；DSH 已停止，任务可能中断，不会自动重放。请调整预算或清理空间后手动启动" }
                val stopFailure = interrupted?.let { text ->
                    FailureCatalog.describe(
                        FailureInput(
                            message = text,
                            operation = "runtime-stop",
                            stage = state.value.stage,
                            service = final,
                            recovery = recoveryState.value,
                            domainHint = FailureDomain.RUNTIME,
                            componentId = "dsh",
                            occurredAtMillis = System.currentTimeMillis()
                        )
                    )
                }
                state.update {
                    it.copy(
                        service = final,
                        webHealthy = false,
                        web = if (remoteMachine.remoteActive) it.web else WebState.DISCONNECTED,
                        error = interrupted ?: it.error,
                        // 用户主动停止时清掉旧失败；资源原因自动停止时给出可操作的错误。
                        failure = if (stopWasRequested) null else stopFailure ?: it.failure
                    )
                }
                if (interrupted != null) {
                    paths.atomic("last-error", interrupted)
                    event(interrupted)
                } else if (stopWasRequested) event("用户停止服务；不自动恢复任务")
            } catch (error: Throwable) {
                // Keep the stop action available if process termination could not be confirmed.
                fail(IllegalStateException("${reason ?: "停止服务"}：未能确认进程已全部停止", error))
                state.update { it.copy(service = ServiceState.RUNNING, webHealthy = false) }
                throw error
            } finally { synchronized(this@RuntimeManager) { stopping = false } }
        }
    }
    fun verifyBrowserCookie(cookie: String?) {
        if (!cookieReady()) return
        val expected = origin ?: return
        val generation=browserGeneration.incrementAndGet()
        scope.launch {
            val result = runCatching {
                val c = URL("$expected/").openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false; c.connectTimeout = 5000; c.readTimeout = 5000
                cookie?.let { c.setRequestProperty("Cookie", it) }
                try { c.responseCode } finally { c.disconnect() }
            }.getOrNull()
            state.update { current ->
                if(origin==expected && browserGeneration.get()==generation &&
                    current.web==WebState.AUTHENTICATING &&
                    com.tanuki.web.DshConnectionPolicy.canVerifyCookie(origin, expected))
                    current.copy(web=when(result){200->WebState.CONNECTED;401,403->WebState.AUTH_EXPIRED;else->WebState.DISCONNECTED})
                else current
            }
        }
    }
    fun web(value: WebState) { browserGeneration.incrementAndGet();state.update { it.copy(web = value) } }
    fun deviceInfo(): String = "${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE} API ${Build.VERSION.SDK_INT}; ABI=${Build.SUPPORTED_ABIS.toList()}; uid=${Process.myUid()} pid=${Process.myPid()}; target=${context.applicationInfo.targetSdkVersion}; debuggable=${BuildConfig.DEBUG}; free=${paths.base.usableSpace}; WebView=${WebView.getCurrentWebViewPackage()?.versionName}"
    companion object {
        const val UBUNTU = "24.04.5"
        const val UBUNTU_URL = "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-arm64.tar.gz"
        const val UBUNTU_SHA = "a91d5a93010193712d346d761372b7c9db6dfcf093893161c64ca107f05914f2"
        const val NODE = "24.22.2"
        const val NODE_SHA = "95c264150aceb7f3437a29fd6984924f5f8d1511ea3cbd6c508b83f6c555dae2"
        const val PNPM = "11.28.5"
        const val PNPM_SHA = "1ef4e447a68713656064f3f174c0f44719960a043a3fe2299169d08cf1dbf8ee"
        const val DSH = "0.2.1-rc.5"
        const val DSH_INTEGRITY = "sha512-WkyJZv4zl9sCAxSzRctGm58m0rilGqwR6pniBiLOz5CPDfHfDuAUicg0AAazXI7oj/Cph6/cjNTncA/rpLTXpw=="
        /** Only for an app-owned tree whose guest processes have been stopped; never follows links. */
        fun deleteTree(file: File) {
            val root = file.toPath()
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
            val retried = HashSet<Path>()
            fun makeTraversable(path: Path) {
                val stat = android.system.Os.lstat(path.toString())
                check(android.system.OsConstants.S_ISDIR(stat.st_mode) && stat.st_uid == Process.myUid()) {
                    "无法清理非应用所有的目录：${path.fileName}"
                }
                // PRoot may leave bind placeholders at mode 000. Only relax directories being removed,
                // never the persistent bind source or a symlink target.
                if (stat.st_mode and 448 != 448) android.system.Os.chmod(path.toString(), (stat.st_mode and 4095) or 448)
            }
            Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(path: Path, attrs: BasicFileAttributes): FileVisitResult {
                    makeTraversable(path)
                    return FileVisitResult.CONTINUE
                }
                override fun visitFileFailed(path: Path, exc: java.io.IOException): FileVisitResult {
                    if (exc !is AccessDeniedException || !retried.add(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw exc
                    makeTraversable(path)
                    // walkFileTree opens a directory before preVisitDirectory; retry once after restoring access.
                    Files.walkFileTree(path, this)
                    return FileVisitResult.CONTINUE
                }
                override fun visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult { Files.delete(path); return FileVisitResult.CONTINUE }
                override fun postVisitDirectory(path: Path, exc: java.io.IOException?): FileVisitResult { if (exc != null) throw exc; Files.delete(path); return FileVisitResult.CONTINUE }
            })
        }
    }
}
