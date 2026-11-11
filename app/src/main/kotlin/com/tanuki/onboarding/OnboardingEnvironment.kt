package com.tanuki.onboarding

import java.util.Locale

/**
 * 首次启动引导采集到的最小设备画像。
 *
 * 这些数值来自系统 API，判断逻辑放在纯 Kotlin 中，测试可以构造任意内存/存储/架构
 * 组合，不需要设备或模拟器。
 */
data class DeviceProfile(
    val totalMemoryBytes: Long,
    val usableStorageBytes: Long,
    val supportedAbis: List<String>,
    val sdkInt: Int,
) {
    val primaryAbi: String get() = supportedAbis.firstOrNull() ?: "未知"

    val supportsArm64: Boolean get() = supportedAbis.any { it == ARM64_ABI }

    companion object {
        const val ARM64_ABI = "arm64-v8a"
    }
}

enum class RequirementStatus { OK, WARNING, BLOCKING }

/**
 * 单项环境结论。
 *
 * [actual] / [requirement] 是给用户看的实测值与门槛；[advice] 只在偏低或不满足时
 * 给出可执行建议，避免界面自行拼技术术语。
 */
data class EnvironmentRequirement(
    val id: String,
    val title: String,
    val status: RequirementStatus,
    val actual: String,
    val requirement: String,
    val advice: String? = null,
)

data class EnvironmentReport(val requirements: List<EnvironmentRequirement>) {
    val blocking: Boolean get() = requirements.any { it.status == RequirementStatus.BLOCKING }

    val hasWarning: Boolean get() = requirements.any { it.status == RequirementStatus.WARNING }

    val ready: Boolean get() = requirements.all { it.status == RequirementStatus.OK }

    /** 不满足最低要求时不允许继续，但仍可跳过引导；偏低只提示。 */
    val canContinue: Boolean get() = !blocking

    fun requirement(id: String): EnvironmentRequirement? = requirements.firstOrNull { it.id == id }
}

/**
 * 运行环境门槛。
 *
 * 存储与架构沿用现有运行时的真实检查：至少 1.5 GB、建议 4 GB，且必须 arm64-v8a。
 * 内存没有硬编码在运行时的旧检查里，这里按可运行一个 DSH 会话的经验下限给出
 * 2 GB 最低、4 GB 建议；低于最低值只阻止继续并解释原因，不阻止用户跳过或改用远程。
 */
object OnboardingEnvironment {
    const val STORAGE_MIN_BYTES = 1536L * 1024 * 1024
    const val STORAGE_RECOMMENDED_BYTES = 4L * 1024 * 1024 * 1024
    const val MEMORY_MIN_BYTES = 2L * 1024 * 1024 * 1024
    const val MEMORY_RECOMMENDED_BYTES = 4L * 1024 * 1024 * 1024
    const val MIN_SDK = 26

    fun evaluate(profile: DeviceProfile): EnvironmentReport {
        val storage = when {
            profile.usableStorageBytes < STORAGE_MIN_BYTES -> EnvironmentRequirement(
                id = "storage",
                title = "可用存储",
                status = RequirementStatus.BLOCKING,
                actual = formatBytes(profile.usableStorageBytes),
                requirement = "至少 ${formatBytes(STORAGE_MIN_BYTES)}",
                advice = "可用空间不足。请先清理手机存储或转移大文件，至少留出 1.5 GB；建议预留 4 GB 后再回来重试。",
            )
            profile.usableStorageBytes < STORAGE_RECOMMENDED_BYTES -> EnvironmentRequirement(
                id = "storage",
                title = "可用存储",
                status = RequirementStatus.WARNING,
                actual = formatBytes(profile.usableStorageBytes),
                requirement = "建议 ${formatBytes(STORAGE_RECOMMENDED_BYTES)}",
                advice = "空间偏少，仍可继续；安装或更新时可能因空间不足中断，建议清理到 4 GB 以上。",
            )
            else -> EnvironmentRequirement(
                id = "storage",
                title = "可用存储",
                status = RequirementStatus.OK,
                actual = formatBytes(profile.usableStorageBytes),
                requirement = "建议 ${formatBytes(STORAGE_RECOMMENDED_BYTES)}",
            )
        }
        val memory = when {
            profile.totalMemoryBytes < MEMORY_MIN_BYTES -> EnvironmentRequirement(
                id = "memory",
                title = "运行内存",
                status = RequirementStatus.BLOCKING,
                actual = formatBytes(profile.totalMemoryBytes),
                requirement = "至少 ${formatBytes(MEMORY_MIN_BYTES)}",
                advice = "运行内存不足。请关闭后台应用后重试；这台设备更适合连接远程电脑使用。",
            )
            profile.totalMemoryBytes < MEMORY_RECOMMENDED_BYTES -> EnvironmentRequirement(
                id = "memory",
                title = "运行内存",
                status = RequirementStatus.WARNING,
                actual = formatBytes(profile.totalMemoryBytes),
                requirement = "建议 ${formatBytes(MEMORY_RECOMMENDED_BYTES)}",
                advice = "内存偏少，仍可继续；同时运行较多应用时可能变慢，建议一次只保留一个会话。",
            )
            else -> EnvironmentRequirement(
                id = "memory",
                title = "运行内存",
                status = RequirementStatus.OK,
                actual = formatBytes(profile.totalMemoryBytes),
                requirement = "建议 ${formatBytes(MEMORY_RECOMMENDED_BYTES)}",
            )
        }
        val architecture = when {
            !profile.supportsArm64 -> EnvironmentRequirement(
                id = "architecture",
                title = "设备架构",
                status = RequirementStatus.BLOCKING,
                actual = profile.primaryAbi,
                requirement = DeviceProfile.ARM64_ABI,
                advice = "本版本只支持 ARM64 设备。当前设备无法在本机准备运行环境，可以改用“连接远程电脑”。",
            )
            else -> EnvironmentRequirement(
                id = "architecture",
                title = "设备架构",
                status = RequirementStatus.OK,
                actual = profile.primaryAbi,
                requirement = DeviceProfile.ARM64_ABI,
            )
        }
        val system = when {
            profile.sdkInt < MIN_SDK -> EnvironmentRequirement(
                id = "system",
                title = "系统版本",
                status = RequirementStatus.BLOCKING,
                actual = "API ${profile.sdkInt}",
                requirement = "Android 8.0（API $MIN_SDK）或更高",
                advice = "系统版本过低，无法安装本应用所需的运行组件。请升级系统或改用其他设备。",
            )
            else -> EnvironmentRequirement(
                id = "system",
                title = "系统版本",
                status = RequirementStatus.OK,
                actual = "Android API ${profile.sdkInt}",
                requirement = "Android 8.0（API $MIN_SDK）或更高",
            )
        }
        return EnvironmentReport(listOf(storage, memory, architecture, system))
    }

    /** 统一按 GiB 展示；不足 1 GB 时保留 MB，避免出现 0.0 GB。 */
    fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "未知"
        val gb = bytes.toDouble() / (1024.0 * 1024 * 1024)
        return when {
            gb >= 1 -> String.format(Locale.ROOT, "%.1f GB", gb)
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
}
