package com.teamwolf.acuariowallpaper

import android.content.Context
import com.teamwolf.acuariowallpaper.acuario.AcuarioRenderer
import com.teamwolf.acuariowallpaper.core.ConfigProvider
import com.teamwolf.acuariowallpaper.core.GLRenderer
import com.teamwolf.acuariowallpaper.core.WallpaperRendererFactory

class AppWallpaperRendererFactory(private val configProvider: ConfigProvider) : WallpaperRendererFactory {
    override fun createRenderer(context: Context): GLRenderer {
        return AcuarioRenderer(context, configProvider)
    }
}
