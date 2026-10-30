package com.xiaopiao.app

import android.app.Application
import androidx.core.app.NotificationManagerCompat
import com.xiaopiao.app.capture.CaptureStatus
import com.xiaopiao.app.work.BudgetNotifier
import com.xiaopiao.app.work.BudgetWatchScheduler
import com.xiaopiao.app.work.CaptureWatchScheduler
import com.xiaopiao.app.work.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class XiaopiaoApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderScheduler.ensureScheduled(this)
        BudgetWatchScheduler.ensure(this)
        // 通知读取是系统绑定的服务：重装 / 被冻结后可能悄悄掉线，冷启动先自检一次。
        CaptureStatus.attach(this)
        CaptureWatchScheduler.ensure(this)
        if (NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)) {
            CaptureWatchScheduler.repairSoon(this, delayMs = 10_000)
        }
        // 花一笔就会跨档：冷启动时先补一次检查，之后由 15 分钟一次的后台任务接上。
        appScope.launch { runCatching { BudgetNotifier.check(this@XiaopiaoApp) } }
    }
}
