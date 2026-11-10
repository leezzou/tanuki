package com.tanuki

import android.webkit.WebView
import android.webkit.WebViewClient
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.tanuki.runtime.FileTransfers
import com.tanuki.web.BlobDownloads
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONTokener
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BlobDownloadTest {
    @Test fun blobBytesRequireBoundOriginAndPendingRequest():Unit=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val server=ServerSocket(0,10,java.net.InetAddress.getByName("127.0.0.1"))
        val other=ServerSocket(0,10,java.net.InetAddress.getByName("127.0.0.1"))
        fun serve(socket:ServerSocket)=launch(Dispatchers.IO) {
            while(isActive && !socket.isClosed) {
                val client=try{socket.accept()}catch(_:java.net.SocketException){break}
                client.use { c ->
                    c.soTimeout=3000;val input=c.getInputStream().bufferedReader();while(input.readLine()?.isNotEmpty()==true){}
                    val body="<html><body>download fixture</body></html>".toByteArray()
                    c.getOutputStream().apply{write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nContent-Type: text/html\r\nConnection: close\r\n\r\n".toByteArray());write(body);flush()}
                }
            }
        }
        val first=serve(server);val second=serve(other)
        lateinit var view:WebView;lateinit var bridge:BlobDownloads
        val received=CountDownLatch(1);val loaded=CountDownLatch(1)
        val calls=AtomicInteger();var file:File?=null;var error=""
        instrumentation.runOnMainSync {
            view=WebView(context).apply{settings.javaScriptEnabled=true}
            bridge=BlobDownloads(context,view,{ f,_ ->file=f;calls.incrementAndGet();received.countDown()},error={message->error=message;received.countDown()})
            bridge.bind("http://127.0.0.1:${server.localPort}")
            view.webViewClient=object:WebViewClient(){override fun onPageFinished(v:WebView,url:String){loaded.countDown()}}
            view.loadUrl("http://127.0.0.1:${server.localPort}/")
        }
        try {
            assertTrue(loaded.await(15,TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                view.evaluateJavascript("window.__tanukiFileExport.postMessage(JSON.stringify({nonce:'forged',data:'bm8='})); URL.createObjectURL(new Blob(['Android 上的文件'],{type:'application/octet-stream'}))") { value ->
                    bridge.request(JSONTokener(value).nextValue() as String,"fixture.txt")
                }
            }
            assertTrue(received.await(15,TimeUnit.SECONDS));assertEquals(error,"",error)
            assertEquals(1,calls.get());assertEquals("Android 上的文件",file!!.readText())
            val foreign=CountDownLatch(1);var exposed=""
            instrumentation.runOnMainSync {
                view.webViewClient=object:WebViewClient(){override fun onPageFinished(v:WebView,url:String){v.evaluateJavascript("typeof window.__tanukiFileExport"){exposed=it;foreign.countDown()}}}
                view.loadUrl("http://127.0.0.1:${other.localPort}/")
            }
            assertTrue(foreign.await(15,TimeUnit.SECONDS));assertEquals("\"undefined\"",exposed)
        }finally{
            file?.delete();instrumentation.runOnMainSync{bridge.dispose();view.destroy()}
            server.close();other.close();first.cancelAndJoin();second.cancelAndJoin()
        }
    }

    /** 取消等待中的 Blob：进度立即消失，晚到回包不能触发 ready/系统选择器，且不报错。 */
    @Test fun cancelledPendingBlobNeverOpensExport():Unit=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val server=ServerSocket(0,10,java.net.InetAddress.getByName("127.0.0.1"))
        val serve=launch(Dispatchers.IO) {
            while(isActive && !server.isClosed) {
                val client=try{server.accept()}catch(_:java.net.SocketException){break}
                client.use { c ->
                    c.soTimeout=3000;val input=c.getInputStream().bufferedReader();while(input.readLine()?.isNotEmpty()==true){}
                    val body="<html><body>blob cancel fixture</body></html>".toByteArray()
                    c.getOutputStream().apply{write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nContent-Type: text/html\r\nConnection: close\r\n\r\n".toByteArray());write(body);flush()}
                }
            }
        }
        lateinit var view:WebView;lateinit var bridge:BlobDownloads
        val transfers=FileTransfers { SystemClock.elapsedRealtime() }
        val readyCalls=AtomicInteger();var error=""
        val loaded=CountDownLatch(1);val cancelled=CountDownLatch(1)
        instrumentation.runOnMainSync {
            view=WebView(context).apply{settings.javaScriptEnabled=true}
            bridge=BlobDownloads(context,view,{ _, _ -> readyCalls.incrementAndGet() },progress=transfers,error={message->error=message})
            bridge.bind("http://127.0.0.1:${server.localPort}")
            view.webViewClient=object:WebViewClient(){override fun onPageFinished(v:WebView,url:String){loaded.countDown()}}
            view.loadUrl("http://127.0.0.1:${server.localPort}/")
        }
        try {
            assertTrue(loaded.await(15,TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                view.evaluateJavascript("URL.createObjectURL(new Blob(['pending'],{type:'application/octet-stream'}))") { value ->
                    bridge.request(JSONTokener(value).nextValue() as String,"cancelled.txt")
                    assertEquals(1,transfers.state.value.size)
                    transfers.cancel(transfers.state.value.single().id)
                    cancelled.countDown()
                }
            }
            assertTrue(cancelled.await(5,TimeUnit.SECONDS))
            assertTrue("取消后进度必须立即移除，不能等 30 秒超时",transfers.state.value.isEmpty())
            // 取消后模拟晚到的网页回包；pending 已清空，不能保存或打开导出选择器。
            instrumentation.runOnMainSync {
                view.evaluateJavascript("window.__tanukiFileExport.postMessage(JSON.stringify({nonce:'late',data:'bm8='}))",null)
            }
            SystemClock.sleep(800)
            assertEquals("取消后不能调用 ready 打开系统选择器",0,readyCalls.get())
            assertEquals("取消是用户行为，不应显示错误","",error)
        }finally{
            instrumentation.runOnMainSync{bridge.dispose();view.destroy()}
            server.close();serve.cancelAndJoin()
        }
    }
}
