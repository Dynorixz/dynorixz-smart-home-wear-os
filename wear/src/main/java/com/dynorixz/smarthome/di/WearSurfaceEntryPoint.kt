package com.dynorixz.smarthome.di

import android.content.Context
import com.dynorixz.smarthome.data.local.UserPreferences
import com.dynorixz.smarthome.domain.SmartHomeRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WearSurfaceEntryPoint {
    fun preferences(): UserPreferences
    fun repository(): SmartHomeRepository
}

fun Context.wearSurfaceDependencies(): WearSurfaceEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WearSurfaceEntryPoint::class.java)
