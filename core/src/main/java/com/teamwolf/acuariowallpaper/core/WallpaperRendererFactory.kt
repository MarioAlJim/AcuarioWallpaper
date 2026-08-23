package com.teamwolf.acuariowallpaper.core

import android.content.Context

interface WallpaperRendererFactory {
    fun createRenderer(context: Context): GLRenderer
}
