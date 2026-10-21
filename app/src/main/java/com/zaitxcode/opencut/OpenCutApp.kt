package com.zaitxcode.opencut

import android.app.Application
import com.zaitxcode.opencut.core.account.AccountStore
import com.zaitxcode.opencut.core.store.ProjectStore

/** Application entry point; owns the process wide stores. */
class OpenCutApp : Application() {

    lateinit var projectStore: ProjectStore
        private set

    lateinit var accountStore: AccountStore
        private set

    override fun onCreate() {
        super.onCreate()
        projectStore = ProjectStore(this)
        accountStore = AccountStore(this)
    }
}
