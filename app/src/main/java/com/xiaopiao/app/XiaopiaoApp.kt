package com.xiaopiao.app

import android.app.Application
import com.xiaopiao.app.work.BudgetNotifier
import com.xiaopiao.app.work.BudgetWatchScheduler
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
        // 花一笔就会跨档：冷启动时先补一次检查，之后由 15 分钟一次的后台任务接上。
        appScope.launch { runCatching { BudgetNotifier.check(this@XiaopiaoApp) } }
    }
}
