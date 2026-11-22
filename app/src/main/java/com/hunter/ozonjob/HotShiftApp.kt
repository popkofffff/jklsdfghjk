package com.hunter.ozonjob

import android.app.Application
import com.hunter.ozonjob.booking.HuntEngine
import com.hunter.ozonjob.data.OzonJobRepository
import com.hunter.ozonjob.data.prefs.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Простой ServiceLocator: DI-фреймворк здесь избыточен. */
object Locator {
    lateinit var tokenStore: TokenStore
    lateinit var repository: OzonJobRepository
    lateinit var huntEngine: HuntEngine

    fun init(app: Application) {
        tokenStore = TokenStore(app)
        repository = OzonJobRepository(tokenStore)
        huntEngine = HuntEngine(CoroutineScope(SupervisorJob() + Dispatchers.Default), repository)
    }
}

class HotShiftApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Locator.init(this)
    }
}
