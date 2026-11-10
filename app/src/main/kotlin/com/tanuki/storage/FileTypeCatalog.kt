package com.tanuki.storage

import com.tanuki.storage.WorkspaceFileManager.FileKind
import java.util.Locale

/**
 * 集中维护“文件如何查看”的扩展名与 MIME 映射。
 *
 * 设计约束：
 * 1. 纯 Kotlin，不依赖 Android `MimeTypeMap`，保证同一文件在不同系统版本上分类一致，也便于 JVM 回归。
 * 2. 显式扩展名优先于 MIME。系统 MIME 表存在冲突（例如 `.ts` 同时是 TypeScript 和 MPEG-TS 视频），
 *    以本目录的代码/文本规则为准，避免把源码送进媒体播放器。
 * 3. 只决定使用哪个查看器，不表示具备格式解析能力。旧版 Office、压缩包、二进制仍归为
 *    [FileKind.OTHER]，由界面给出中文说明并回退到文本或外部应用。
 * 4. 未知扩展名返回 `null`，调用方再按 MIME 或内容探测回退，不能凭猜测把二进制当文本。
 */
internal object FileTypeCatalog {
    const val OCTET_STREAM = "application/octet-stream"

    /** 代码、配置、标记语言等按纯文本查看的扩展名。 */
    private val textExtensions = setOf(
        // 通用文本 / 日志 / 数据
        "txt", "text", "log", "logs", "out", "err", "md", "markdown", "mdx", "rst", "adoc", "asciidoc", "org",
        "csv", "tsv", "srt", "vtt", "ass", "ssa", "diff", "patch", "rtf", "ics",
        // 结构化配置
        "json", "jsonl", "ndjson", "json5", "jsonc", "xml", "xsl", "xslt", "xsd", "dtd", "plist",
        "yaml", "yml", "toml", "ini", "cfg", "conf", "config", "properties", "env", "lock", "sum", "mod",
        "editorconfig", "npmrc", "yarnrc", "babelrc", "eslintrc", "prettierrc", "service", "timer", "desktop",
        // Web / 样式 / 查询
        "html", "htm", "xhtml", "shtml", "css", "scss", "sass", "less", "styl", "vue", "svelte", "astro",
        "sql", "hql", "cql", "graphql", "gql", "svg",
        // C / C++ / 头文件 / 汇编
        "c", "h", "cc", "cp", "cpp", "cxx", "c++", "hh", "hpp", "hxx", "h++", "inl", "ipp", "tpp",
        "m", "mm", "cu", "cuh", "asm", "s", "inc",
        // JVM / Android
        "java", "kt", "kts", "gradle", "groovy", "clj", "cljs", "cljc", "scala", "sc", "fs", "fsx", "cs", "vb",
        // Python / JS / TS
        "py", "pyw", "pyi", "pyx", "ipynb", "js", "jsx", "mjs", "cjs", "ts", "tsx", "mts", "cts",
        // Go / Rust / 其他语言与构建脚本
        "go", "rs", "rb", "rake", "gemspec", "php", "pl", "pm", "lua", "r", "swift", "dart",
        "ex", "exs", "erl", "hrl", "hs", "lhs", "ml", "mli", "nim", "zig", "v", "sv", "svh", "vhd", "vhdl",
        "proto", "thrift", "sol", "hcl", "tf", "tfvars", "nix", "dhall", "jsonnet", "tex", "bib",
        // Shell 与终端脚本
        "sh", "bash", "zsh", "fish", "ksh", "csh", "tcsh", "bat", "cmd", "ps1", "psm1", "mk", "make", "am", "ac", "in",
        // 证书/密钥等 PEM 文本（只读查看，不做解密）
        "pem", "crt", "cer", "csr", "key", "pub",
    )

    /** 没有扩展名或点号开头的常见文本文件；全部小写比较。 */
    private val textNames = setOf(
        "dockerfile", "containerfile", "makefile", "gnumakefile", "cmakelists.txt", "rakefile", "gemfile", "vagrantfile",
        "jenkinsfile", "procfile", "brewfile", "podfile", "fastfile", "justfile", "taskfile", "license", "licence",
        "notice", "copying", "authors", "readme", "changelog", "changes", "todo", "version", "go.mod", "go.sum",
        "cargo.lock", "cargo.toml", "package.json", "package-lock.json", "pnpm-lock.yaml", "yarn.lock", "tsconfig.json",
        ".gitignore", ".gitattributes", ".gitmodules", ".editorconfig", ".npmrc", ".yarnrc", ".babelrc", ".env",
        ".bashrc", ".zshrc", ".profile", ".bash_profile", ".zprofile", ".inputrc", ".wgetrc", ".curlrc",
    )

    /** 已知媒体扩展名；不依赖系统 MIME 表，缺少 MIME 时仍能进入对应查看器。 */
    private val imageExtensions = setOf(
        "png", "jpg", "jpeg", "jpe", "jfif", "gif", "webp", "bmp", "dib", "heic", "heif", "avif",
        "ico", "cur", "tif", "tiff", "wbmp",
    )
    private val audioExtensions = setOf(
        "mp3", "m4a", "aac", "flac", "wav", "wave", "ogg", "oga", "opus", "amr", "mid", "midi",
        "mka", "wma", "aiff", "aif",
    )
    private val videoExtensions = setOf(
        // 注意：.ts/.mts 都是 TypeScript 的文本扩展名，不放进视频集合，避免与 MPEG-TS 冲突。
        "mp4", "m4v", "mkv", "webm", "mov", "qt", "avi", "3gp", "3gpp", "3g2", "mpg", "mpeg", "m2v",
        "m2ts", "wmv", "flv", "ogv",
    )

    /** 明确按二进制/归档/旧版 Office 处理，禁止再做“看起来像文本”的内容探测。 */
    private val binaryExtensions = setOf(
        "doc", "xls", "xlsm", "xlsb", "ppt", "pptx", "pptm", "pps", "ppsx", "ppsm", "odt", "ods", "odp", "odg",
        "pages", "numbers", "wpd",
        "zip", "tar", "gz", "tgz", "bz2", "tbz2", "xz", "txz", "7z", "rar", "jar", "war", "ear", "apk", "aab",
        "iso", "dmg", "cab", "lz", "lzma", "zst",
        "exe", "dll", "so", "dylib", "bin", "class", "pyc", "pyo", "o", "obj", "a", "lib", "deb", "rpm", "msi",
        "wasm", "elf", "dat", "db", "sqlite", "sqlite3", "mdb",
        "ttf", "otf", "woff", "woff2", "ttc", "eot",
        "onnx", "pt", "pth", "safetensors", "gguf", "p12", "pfx", "jks", "keystore",
    )

    /** 非 PDF/DOCX/XLSX 的显式类别；这些类型不参与文本内容探测。 */
    private val explicitKinds: Map<String, FileKind> = buildMap {
        put("pdf", FileKind.PDF)
        put("docx", FileKind.DOCX)
        put("doc", FileKind.DOC)
        put("xlsx", FileKind.XLSX)
    }

    private val textLikeMimeTypes = setOf(
        "application/json", "application/ld+json", "application/xml", "application/xhtml+xml",
        "application/yaml", "application/x-yaml", "application/toml", "application/javascript",
        "application/x-javascript", "application/ecmascript", "application/sql", "application/graphql",
        "application/x-sh", "application/x-shellscript", "application/x-ndjson", "application/x-httpd-php",
        "application/x-perl", "application/x-python", "application/x-ruby", "application/rtf",
        "application/x-subrip", "application/x-tex", "application/x-latex", "application/x-httpd-php-source",
        "application/vnd.apple.mpegurl", "application/x-ipynb+json",
    )

    /** 当前文件类型；先看扩展名/文件名，再看 MIME，最后才由调用方决定是否内容探测。 */
    fun kind(name: String, mime: String?): FileKind {
        val lowerName = name.substringAfterLast('/').lowercase(Locale.ROOT)
        val extension = extension(lowerName)
        explicitKinds[extension]?.let { return it }
        if (extension in binaryExtensions) return FileKind.OTHER
        // 文本扩展名先于媒体 MIME（.ts 冲突）；媒体扩展名再于文本判断，避免 PNG/MP4 落进 OTHER。
        if (extension in textExtensions || lowerName in textNames || isDotConfig(lowerName)) return FileKind.TEXT
        if (extension in imageExtensions) return FileKind.IMAGE
        if (extension in audioExtensions) return FileKind.AUDIO
        if (extension in videoExtensions) return FileKind.VIDEO

        val type = normalizeMime(mime) ?: return FileKind.OTHER
        return when {
            type.startsWith("text/") -> FileKind.TEXT
            type.startsWith("image/") -> FileKind.IMAGE
            type.startsWith("audio/") -> FileKind.AUDIO
            type.startsWith("video/") -> FileKind.VIDEO
            type in textLikeMimeTypes || type.endsWith("+json") || type.endsWith("+xml") || type.endsWith("+yaml") -> FileKind.TEXT
            else -> FileKind.OTHER
        }
    }

    /** 目录中的扩展名（小写）；点号开头、无扩展名或末尾是点号时返回空串。 */
    fun extension(name: String): String {
        val base = name.substringAfterLast('/')
        val dot = base.lastIndexOf('.')
        return if (dot <= 0 || dot == base.lastIndex) "" else base.substring(dot + 1).lowercase(Locale.ROOT)
    }

    /** 已知扩展名的规范 MIME；未知返回 `null`，由调用方回退系统 MIME 表。 */
    fun mime(name: String): String? = mimeByExtension[extension(name)]

    /** 是否为明确二进制/归档/旧版 Office 扩展名；用于跳过昂贵且易误判的内容探测。 */
    fun isBinaryExtension(name: String): Boolean = extension(name) in binaryExtensions

    /** 是否为本目录认作文本的文件；只看文件名/扩展名，不做内容读取。 */
    fun isTextName(name: String): Boolean {
        val lowerName = name.substringAfterLast('/').lowercase(Locale.ROOT)
        val extension = extension(lowerName)
        return extension in textExtensions || lowerName in textNames || isDotConfig(lowerName)
    }

    fun normalizeMime(mime: String?): String? = mime?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)?.ifEmpty { null }

    /** 点号开头的配置文本文档，例如 `.env`、`.env.local`、`.npmrc`。 */
    private fun isDotConfig(lowerName: String): Boolean =
        lowerName.startsWith(".env") || (lowerName.startsWith(".") && !lowerName.drop(1).contains('.'))

    private val mimeByExtension: Map<String, String> = buildMap {
        // 文档
        put("pdf", "application/pdf")
        put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        put("doc", "application/msword")
        put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        put("xls", "application/vnd.ms-excel")
        put("xlsm", "application/vnd.ms-excel.sheet.macroEnabled.12")
        put("ppt", "application/vnd.ms-powerpoint")
        put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation")
        put("odt", "application/vnd.oasis.opendocument.text")
        put("ods", "application/vnd.oasis.opendocument.spreadsheet")
        put("odp", "application/vnd.oasis.opendocument.presentation")
        // 文本 / 数据
        put("txt", "text/plain"); put("text", "text/plain"); put("log", "text/plain"); put("logs", "text/plain")
        put("out", "text/plain"); put("err", "text/plain"); put("env", "text/plain"); put("ini", "text/plain")
        put("cfg", "text/plain"); put("conf", "text/plain"); put("config", "text/plain"); put("properties", "text/plain")
        put("md", "text/markdown"); put("markdown", "text/markdown"); put("mdx", "text/markdown")
        put("csv", "text/csv"); put("tsv", "text/tab-separated-values"); put("srt", "application/x-subrip")
        put("vtt", "text/vtt"); put("diff", "text/x-diff"); put("patch", "text/x-diff"); put("rtf", "application/rtf")
        put("ics", "text/calendar")
        put("json", "application/json"); put("jsonl", "application/x-ndjson"); put("ndjson", "application/x-ndjson")
        put("json5", "application/json"); put("jsonc", "application/json"); put("ipynb", "application/x-ipynb+json")
        put("xml", "application/xml"); put("xsl", "application/xml"); put("xslt", "application/xml")
        put("xsd", "application/xml"); put("dtd", "application/xml-dtd"); put("plist", "application/xml")
        put("yaml", "application/yaml"); put("yml", "application/yaml"); put("toml", "application/toml")
        put("lock", "text/plain"); put("sum", "text/plain"); put("mod", "text/plain")
        // Web / 样式 / 查询
        put("html", "text/html"); put("htm", "text/html"); put("xhtml", "application/xhtml+xml"); put("shtml", "text/html")
        put("css", "text/css"); put("scss", "text/x-scss"); put("sass", "text/x-sass"); put("less", "text/x-less")
        put("sql", "application/sql"); put("graphql", "application/graphql"); put("gql", "application/graphql")
        put("svg", "image/svg+xml")
        // 源码
        put("c", "text/x-csrc"); put("h", "text/x-chdr")
        put("cc", "text/x-c++src"); put("cpp", "text/x-c++src"); put("cxx", "text/x-c++src"); put("c++", "text/x-c++src")
        put("hh", "text/x-c++src"); put("hpp", "text/x-c++src"); put("hxx", "text/x-c++src"); put("h++", "text/x-c++src")
        put("m", "text/x-objective-c"); put("mm", "text/x-objective-c++"); put("cu", "text/x-cuda"); put("cuh", "text/x-cuda")
        put("java", "text/x-java"); put("kt", "text/x-kotlin"); put("kts", "text/x-kotlin"); put("gradle", "text/x-gradle")
        put("groovy", "text/x-groovy"); put("clj", "text/x-clojure"); put("scala", "text/x-scala"); put("cs", "text/x-csharp")
        put("py", "text/x-python"); put("pyw", "text/x-python"); put("pyi", "text/x-python")
        put("js", "text/javascript"); put("mjs", "text/javascript"); put("cjs", "text/javascript"); put("jsx", "text/jsx")
        put("ts", "text/typescript"); put("tsx", "text/tsx"); put("mts", "text/typescript"); put("cts", "text/typescript")
        put("go", "text/x-go"); put("rs", "text/rust"); put("rb", "text/x-ruby"); put("php", "text/x-php")
        put("pl", "text/x-perl"); put("pm", "text/x-perl"); put("lua", "text/x-lua"); put("r", "text/x-r")
        put("swift", "text/x-swift"); put("dart", "text/x-dart"); put("sc", "text/x-scala")
        put("sh", "text/x-shellscript"); put("bash", "text/x-shellscript"); put("zsh", "text/x-shellscript")
        put("fish", "text/x-shellscript"); put("ksh", "text/x-shellscript")
        put("bat", "text/x-batch"); put("cmd", "text/x-batch"); put("ps1", "text/x-powershell")
        put("asm", "text/x-asm"); put("s", "text/x-asm"); put("v", "text/x-verilog"); put("sv", "text/x-systemverilog")
        put("vhd", "text/x-vhdl"); put("vhdl", "text/x-vhdl"); put("proto", "text/x-protobuf"); put("tex", "text/x-tex")
        // 图片
        put("png", "image/png"); put("jpg", "image/jpeg"); put("jpeg", "image/jpeg"); put("jpe", "image/jpeg")
        put("jfif", "image/jpeg"); put("gif", "image/gif"); put("webp", "image/webp"); put("bmp", "image/bmp")
        put("dib", "image/bmp"); put("heic", "image/heic"); put("heif", "image/heif"); put("avif", "image/avif")
        put("ico", "image/x-icon"); put("cur", "image/x-icon"); put("tif", "image/tiff"); put("tiff", "image/tiff")
        put("wbmp", "image/vnd.wap.wbmp")
        // 音频
        put("mp3", "audio/mpeg"); put("m4a", "audio/mp4"); put("aac", "audio/aac"); put("flac", "audio/flac")
        put("wav", "audio/wav"); put("wave", "audio/wav"); put("ogg", "audio/ogg"); put("oga", "audio/ogg")
        put("opus", "audio/ogg"); put("amr", "audio/amr"); put("mid", "audio/midi"); put("midi", "audio/midi")
        put("mka", "audio/x-matroska"); put("wma", "audio/x-ms-wma"); put("aiff", "audio/aiff"); put("aif", "audio/aiff")
        // 视频
        put("mp4", "video/mp4"); put("m4v", "video/mp4"); put("mkv", "video/x-matroska"); put("webm", "video/webm")
        put("mov", "video/quicktime"); put("qt", "video/quicktime"); put("avi", "video/x-msvideo")
        put("3gp", "video/3gpp"); put("3gpp", "video/3gpp"); put("3g2", "video/3gpp2"); put("mpg", "video/mpeg")
        put("mpeg", "video/mpeg"); put("m2v", "video/mpeg"); put("m2ts", "video/mp2t")
        put("wmv", "video/x-ms-wmv"); put("flv", "video/x-flv"); put("ogv", "video/ogg")
        // 归档 / 二进制 / 字体
        put("zip", "application/zip"); put("tar", "application/x-tar"); put("gz", "application/gzip")
        put("tgz", "application/gzip"); put("bz2", "application/x-bzip2"); put("xz", "application/x-xz")
        put("7z", "application/x-7z-compressed"); put("rar", "application/vnd.rar"); put("jar", "application/java-archive")
        put("apk", "application/vnd.android.package-archive"); put("aab", "application/octet-stream")
        put("exe", "application/vnd.microsoft.portable-executable"); put("dll", "application/x-msdownload")
        put("so", "application/x-sharedlib"); put("dylib", "application/x-mach-binary"); put("class", "application/java-vm")
        put("o", "application/x-object"); put("a", "application/x-archive"); put("deb", "application/vnd.debian.binary-package")
        put("rpm", "application/x-rpm"); put("iso", "application/x-iso9660-image"); put("dmg", "application/x-apple-diskimage")
        put("ttf", "font/ttf"); put("otf", "font/otf"); put("woff", "font/woff"); put("woff2", "font/woff2")
    }
}
