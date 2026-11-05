package com.tanuki

import com.tanuki.storage.SharedStoragePaths
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** 手机工具路径生成不依赖界面标签、卷顺序或 Android 权限环境。 */
class SharedStoragePathsTest {
    private val primary = File("/storage/emulated/0")

    @Test fun primaryAndRemovableVolumesHaveStableDistinctGuestRoots() {
        val card = File("/storage/ABCD-1234")
        val usb = File("/storage/EF00-5678")
        assertEquals("/phone/primary", SharedStoragePaths.guestRoot(primary, primary))
        val first = SharedStoragePaths.guestRoot(card, primary)
        assertTrue(first.matches(Regex("/phone/volume-[a-f0-9]{16}")))
        assertEquals(first, SharedStoragePaths.guestRoot(File("/storage/ABCD-1234/./"), primary))
        assertNotEquals(first, SharedStoragePaths.guestRoot(usb, primary))
        assertEquals("$first/Download/中文 文件.txt", SharedStoragePaths.guestPath(card, primary, "Download/中文 文件.txt"))
    }

    @Test fun guestRelativePathCannotEscapeOrBecomeABindArgument() {
        for (tail in listOf("../private", "Download/../../private", "/data/user/0", "a\\b", "a//b", "a/./b", "nul\u0000")) {
            try { SharedStoragePaths.guestPath(primary, primary, tail); fail("应拒绝不安全的相对路径") }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals("/phone/primary", SharedStoragePaths.guestPath(primary, primary, ""))
        try { SharedStoragePaths.guestRoot(File("relative"), primary); fail("宿主根必须是绝对路径") }
        catch (_: IllegalArgumentException) { }
    }
}
