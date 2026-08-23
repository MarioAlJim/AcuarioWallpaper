package com.teamwolf.acuariowallpaper.core

/**
 * Read-only view of the wallpaper's persisted settings, as seen by the renderer.
 *
 * Add one `fun get...(): T` per new knob here, mirroring it in [ConfigManager] and
 * [AcuarioConfigStore].
 */
interface ConfigProvider {
    fun getAcuarioTheme(): Int // 0: Acuario (contained, warmer light), 1: Mar abierto (deeper, colder light)
}
