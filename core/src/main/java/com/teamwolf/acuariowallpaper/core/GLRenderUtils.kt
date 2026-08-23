package com.teamwolf.acuariowallpaper.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLUtils
import java.io.File
import java.io.IOException

/**
 * Shared OpenGL ES helpers used by the effect renderers: shader/program
 * compilation and texture loading from assets or internal storage.
 */
object GLRenderUtils {

    fun readAssetFile(context: Context, path: String): String {
        return try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            throw RuntimeException("Could not open asset file: $path", e)
        }
    }

    fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, shaderCode)
        GLES30.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Error compiling shader ($type): $log")
        }
        return shader
    }

    fun createProgram(vertexCode: String, fragmentCode: String): Int {
        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexCode)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentCode)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw RuntimeException("Error linking program: $log")
        }
        return program
    }

    /**
     * Loads a texture from assets. When [maxSize] > 0, the bitmap is downsampled
     * (via inSampleSize, then an exact scale pass) so its largest dimension does not exceed it.
     */
    fun loadTexture(context: Context, assetPath: String, maxSize: Int = 0): Int {
        val textureIds = IntArray(1)
        GLES30.glGenTextures(1, textureIds, 0)
        if (textureIds[0] == 0) {
            return 0
        }

        try {
            var bitmap: Bitmap? = null
            if (maxSize > 0) {
                val boundsOpts = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                    inScaled = false
                }
                context.assets.open(assetPath).use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOpts)
                }
                val origW = boundsOpts.outWidth
                val origH = boundsOpts.outHeight
                var sampleSize = 1
                while (origW / (sampleSize * 2) >= maxSize && origH / (sampleSize * 2) >= maxSize) {
                    sampleSize *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply {
                    inScaled = false
                    inSampleSize = sampleSize
                }
                context.assets.open(assetPath).use { stream ->
                    bitmap = BitmapFactory.decodeStream(stream, null, decodeOpts)
                }
                bitmap?.let { bmp ->
                    val maxDim = maxOf(bmp.width, bmp.height)
                    if (maxDim > maxSize) {
                        val scale = maxSize.toFloat() / maxDim
                        val newW = (bmp.width * scale).toInt().coerceAtLeast(1)
                        val newH = (bmp.height * scale).toInt().coerceAtLeast(1)
                        val scaled = Bitmap.createScaledBitmap(bmp, newW, newH, true)
                        bmp.recycle()
                        bitmap = scaled
                    }
                }
            } else {
                val options = BitmapFactory.Options().apply {
                    inScaled = false
                }
                context.assets.open(assetPath).use { stream ->
                    bitmap = BitmapFactory.decodeStream(stream, null, options)
                }
            }
            bitmap?.let { bmp ->
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureIds[0])
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
                GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
                GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
                bmp.recycle()
            }
        } catch (e: IOException) {
            GLES30.glDeleteTextures(1, textureIds, 0)
            throw RuntimeException("Could not load texture from asset: $assetPath", e)
        }
        return textureIds[0]
    }

    // Background assets are typically much larger than any phone screen; capping and
    // mipmapping them avoids excess VRAM/bandwidth use and shimmering/aliasing when
    // scaled to fit different aspect ratios.
    private const val DEFAULT_BACKGROUND_MAX_SIZE = 2048

    /**
     * Loads a background-style texture (downsampled to [maxSize], mipmapped, trilinear
     * filtering) from an asset path. Returns the GL texture id paired with the bitmap's
     * aspect ratio (width/height), or null on failure.
     */
    fun loadBackgroundTextureFromAsset(context: Context, assetPath: String, maxSize: Int = DEFAULT_BACKGROUND_MAX_SIZE): Pair<Int, Float>? {
        val textureIds = IntArray(1)
        GLES30.glGenTextures(1, textureIds, 0)
        if (textureIds[0] == 0) return null

        return try {
            val boundsOpts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                inScaled = false
            }
            context.assets.open(assetPath).use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val sampleSize = computeSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, maxSize)
            val decodeOpts = BitmapFactory.Options().apply {
                inScaled = false
                inSampleSize = sampleSize
            }
            val bitmap = context.assets.open(assetPath).use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            }
            if (bitmap != null) {
                val scaled = scaleDownToMax(bitmap, maxSize)
                val aspectRatio = scaled.width.toFloat() / scaled.height.toFloat()
                bindBackgroundTexture(textureIds[0], scaled)
                textureIds[0] to aspectRatio
            } else {
                GLES30.glDeleteTextures(1, textureIds, 0)
                null
            }
        } catch (e: Exception) {
            GLES30.glDeleteTextures(1, textureIds, 0)
            e.printStackTrace()
            null
        }
    }

    /**
     * Loads a background-style texture (downsampled to [maxSize], mipmapped, trilinear
     * filtering) from a file on internal storage. Returns the GL texture id paired with
     * the bitmap's aspect ratio (width/height), or null on failure/missing file.
     */
    fun loadBackgroundTextureFromFile(file: File, maxSize: Int = DEFAULT_BACKGROUND_MAX_SIZE): Pair<Int, Float>? {
        if (!file.exists()) return null

        val textureIds = IntArray(1)
        GLES30.glGenTextures(1, textureIds, 0)
        if (textureIds[0] == 0) return null

        return try {
            val boundsOpts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                inScaled = false
            }
            BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
            val sampleSize = computeSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, maxSize)
            val decodeOpts = BitmapFactory.Options().apply {
                inScaled = false
                inSampleSize = sampleSize
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
            if (bitmap != null) {
                val scaled = scaleDownToMax(bitmap, maxSize)
                val aspectRatio = scaled.width.toFloat() / scaled.height.toFloat()
                bindBackgroundTexture(textureIds[0], scaled)
                textureIds[0] to aspectRatio
            } else {
                GLES30.glDeleteTextures(1, textureIds, 0)
                null
            }
        } catch (e: Exception) {
            GLES30.glDeleteTextures(1, textureIds, 0)
            e.printStackTrace()
            null
        }
    }

    private fun computeSampleSize(origW: Int, origH: Int, maxSize: Int): Int {
        var sampleSize = 1
        while (origW / (sampleSize * 2) >= maxSize && origH / (sampleSize * 2) >= maxSize) {
            sampleSize *= 2
        }
        return sampleSize
    }

    // inSampleSize only guarantees powers-of-2 downsampling, so an exact final scale
    // pass is needed to land at (or under) maxSize on the longest dimension.
    private fun scaleDownToMax(bitmap: Bitmap, maxSize: Int): Bitmap {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        if (maxDim <= maxSize) return bitmap
        val scale = maxSize.toFloat() / maxDim
        val newW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val newH = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        bitmap.recycle()
        return scaled
    }

    private fun bindBackgroundTexture(textureId: Int, bitmap: Bitmap) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        bitmap.recycle()
    }
}
