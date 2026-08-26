package com.teamwolf.acuariowallpaper.core

/**
 * Read-only view of the wallpaper's persisted settings, as seen by the renderer.
 *
 * Add one `fun get...(): T` per new knob here, mirroring it in [ConfigManager] and
 * [AcuarioConfigStore].
 */
interface ConfigProvider {
    fun getAcuarioTheme(): Int // 0: Acuario (contained, warmer light), 1: Mar abierto (deeper, colder light)
    fun getTurtleCount(): Int  // 0-5
    fun getBubbleCount(): Int  // 4-45 ambient bubbles on screen at once
    fun getFishCount(): Int    // 0-8
    fun getMantaCount(): Int   // 0-4
    fun getSeahorseCount(): Int   // 0-4
    fun getJellyfishCount(): Int  // 0-6
    fun getPlantDensity(): Int // 0-24 anchored plants (kelp + anemones) on the tank floor
    fun getDayNightCycleDuration(): Int // duration of day/night cycle in seconds (60, 180, 600, or -1 for real-time)
    fun getCustomShallowColor(): Int   // Custom top gradient color
    fun getCustomDeepColor(): Int      // Custom bottom gradient color
    fun getSubmarineColor(): Int       // 0: Amarillo, 1: Rojo, 2: Azul, 3: Verde, 4: Rosa, 5: Naranja
}
