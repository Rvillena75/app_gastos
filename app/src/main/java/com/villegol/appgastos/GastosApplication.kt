package com.villegol.appgastos

import android.app.Application
import com.villegol.appgastos.data.AppContainer

class GastosApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
