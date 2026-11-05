package com.tanuki.storage

import android.content.Context
import android.system.Os
import android.util.AtomicFile
import com.tanuki.runtime.Paths
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/** 发布原生手机文件能力客户端，不修改官方 DSH 的模型、配置或审批策略。 */
object PhoneFileIntegration {
    fun directory(paths: Paths) = File(paths.base, "phone-tools")

    @Synchronized fun publish(context: Context, paths: Paths, port: Int, token: String) {
        require(port in 1..65535 && token.matches(Regex("[A-Za-z0-9_-]{43}"))) { "手机文件连接配置无效" }
        val tools = directory(paths)
        safeDirectory(tools)
        val agents = File(paths.home, ".agents")
        safeDirectory(agents)
        val skills = File(agents, "skills")
        safeDirectory(skills)
        val skill = File(skills, "tanuki-phone")
        safeDirectory(skill)
        val instructions = File(skill, "SKILL.md")
        check(!Files.isSymbolicLink(instructions.toPath())) { "手机文件 Skill 路径被重定向" }
        if (instructions.exists()) check(instructions.readText().contains("managed-by: Tanuki")) {
            "已有同名自定义手机文件 Skill，未覆盖"
        }
        copyAsset(context, "phone/tanuki-phone.mjs", File(tools, "tanuki-phone.mjs"))
        copyAsset(context, "phone/SKILL.md", instructions)
        // 配置最后发布：只读客户端与受管 Skill 安装完成后才暴露当前连接。
        atomic(File(tools, "connection.json"), JSONObject().put("port", port).put("token", token).toString())
    }

    @Synchronized fun disconnect(paths: Paths) {
        val tools = directory(paths)
        if (Files.isSymbolicLink(tools.toPath())) return
        val config = File(tools, "connection.json")
        if (listOf(config, File(config.path + ".bak"), File(config.path + ".new")).any { Files.isSymbolicLink(it.toPath()) }) return
        AtomicFile(config).delete()
    }

    private fun safeDirectory(file: File) {
        check(!Files.isSymbolicLink(file.toPath())) { "手机文件工具目录被重定向" }
        Files.createDirectories(file.toPath())
        check(Files.isDirectory(file.toPath(), LinkOption.NOFOLLOW_LINKS)) { "手机文件工具目录不可用" }
        Os.chmod(file.absolutePath, 448)
    }

    private fun copyAsset(context: Context, asset: String, destination: File) {
        check(!Files.isSymbolicLink(destination.toPath())) { "手机文件工具路径被重定向" }
        val text = context.assets.open(asset).bufferedReader().use { it.readText() }
        if (!destination.exists() || destination.readText() != text) atomic(destination, text)
        else Os.chmod(destination.absolutePath, 384)
    }

    private fun atomic(file: File, text: String) {
        check(listOf(file, File(file.path + ".bak"), File(file.path + ".new")).none { Files.isSymbolicLink(it.toPath()) }) {
            "手机文件工具暂存路径被重定向"
        }
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            Os.fchmod(output.fd, 384)
            output.write(text.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (failure: Throwable) { atomic.failWrite(output); throw failure }
    }
}
