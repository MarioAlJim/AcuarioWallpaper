package com.teamwolf.acuariowallpaper.core

/**
 * Read-only view of the wallpaper's persisted settings, as seen by the renderer.
 *
 * This is intentionally empty for now — it's the seam the Acuario renderer and its
 * settings screen will read through once the first configurable knobs (fish density,
 * bubble frequency, background, etc.) are designed. Add one `fun get...(): T` per
 * setting here, mirroring it in [ConfigManager] and [AcuarioConfigStore].
 */
interface ConfigProvider
