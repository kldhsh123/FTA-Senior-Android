package com.fta.senior

import android.content.pm.PackageManager
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fta.senior.privileged.PrivilegedService
import com.fta.senior.privileged.SystemBinderAccess
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ShizukuBridgeTest {
    /** Requires Shizuku running and FTA already authorized; never force-stops an app. */
    @Test fun queryRunningAppsWithoutStartingUserService() {
        val received = CountDownLatch(1)
        val listener = Shizuku.OnBinderReceivedListener { received.countDown() }
        Shizuku.addBinderReceivedListenerSticky(listener)
        try {
            assumeTrue("Start Shizuku and authorize FTA before this test", received.await(10, TimeUnit.SECONDS))
            assumeTrue(Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
            val access = SystemBinderAccess.connect()
            val result = JSONObject(PrivilegedService(access).snapshot(Process.myUid() / 100000))
            assertTrue(result.optString("message"), result.getBoolean("ok"))
            assertTrue("System process list must include FTA", (0 until result.getJSONArray("apps").length()).any {
                result.getJSONArray("apps").getJSONObject(it).getString("packageName") == "com.fta.senior"
            })
        } finally { Shizuku.removeBinderReceivedListener(listener) }
    }
}
