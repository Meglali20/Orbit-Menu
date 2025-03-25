package com.oussamameg.orbitmenu.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.opengl.GLES30
import android.util.Log
import org.joml.Vector3f
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.random.Random
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt

private const val TAG = "GLHelper"
class GLHelper {
    companion object {
        fun createShader(type: Int, source: String): Int {
            val shader = GLES30.glCreateShader(type)
            if (shader == 0) {
                Log.e(TAG, "Could not create shader")
                return 0
            }

            GLES30.glShaderSource(shader, source)
            GLES30.glCompileShader(shader)

            val compiled = IntArray(1)
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)

            if (compiled[0] == 0) {
                val infoLog = GLES30.glGetShaderInfoLog(shader)
                Log.e(TAG, "Shader compilation failed: $infoLog")
                GLES30.glDeleteShader(shader)
                return 0
            }

            return shader
        }

        fun createProgram(
            vertexShaderString: String,
            fragmentShaderString: String
        ): Int {
            val vertexShader = createShader(GLES30.GL_VERTEX_SHADER, vertexShaderString)
            val fragmentShader = createShader(GLES30.GL_FRAGMENT_SHADER, fragmentShaderString)

            if (vertexShader == 0 || fragmentShader == 0) {
                return 0
            }

            val program = GLES30.glCreateProgram()
            if (program == 0) {
                Log.e(TAG, "Could not create program")
                GLES30.glDeleteShader(vertexShader)
                GLES30.glDeleteShader(fragmentShader)
                return 0
            }

            GLES30.glAttachShader(program, vertexShader)
            GLES30.glAttachShader(program, fragmentShader)


            GLES30.glLinkProgram(program)

            val linked = IntArray(1)
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linked, 0)

            //GLES30.glDeleteShader(vertexShader)
            //GLES30.glDeleteShader(fragmentShader)

            if (linked[0] == 0) {
                val infoLog = GLES30.glGetProgramInfoLog(program)
                Log.e(TAG, "Program linking failed: $infoLog")
                GLES30.glDeleteProgram(program)
                return 0
            }

            return program
        }

        fun makeVertexArray(
            bufLocNumElmPairs: List<Triple<Int, Int, Int>>,
            indices: ShortArray? = null
        ): Int {
            val vaArray = IntArray(1)
            GLES30.glGenVertexArrays(1, vaArray, 0)
            val va = vaArray[0]
            if (va == 0) return 0

            GLES30.glBindVertexArray(va)

            for ((buffer, loc, numElem) in bufLocNumElmPairs) {
                if (loc == -1) continue
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffer)
                GLES30.glEnableVertexAttribArray(loc)
                GLES30.glVertexAttribPointer(loc, numElem, GLES30.GL_FLOAT, false, 0, 0)
            }

            if (indices != null) {
                val indexBuffer = IntArray(1)
                GLES30.glGenBuffers(1, indexBuffer, 0)
                if (indexBuffer[0] != 0) {
                    // Convert the ShortArray to a direct ByteBuffer
                    val bb = ByteBuffer.allocateDirect(indices.size * 2)  // 2 bytes per short
                    bb.order(ByteOrder.nativeOrder())
                    val shortBuffer = bb.asShortBuffer()
                    shortBuffer.put(indices)
                    shortBuffer.position(0)

                    GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, indexBuffer[0])
                    GLES30.glBufferData(
                        GLES30.GL_ELEMENT_ARRAY_BUFFER,
                        indices.size * 2,  // size in bytes
                        shortBuffer,
                        GLES30.GL_STATIC_DRAW
                    )
                }
            }

            GLES30.glBindVertexArray(0)
            return va
        }

        fun makeBuffer(
            sizeOrData: Any,
            usage: Int
        ): Int {
            val buffers = IntArray(1)
            GLES30.glGenBuffers(1, buffers, 0)
            val buffer = buffers[0]

            if (buffer == 0) {
                throw IllegalStateException("Failed to create OpenGL buffer.")
            }

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffer)

            when (sizeOrData) {
                is Int -> {
                    GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, sizeOrData, null, usage)
                }
                is FloatArray -> {
                    val bb = ByteBuffer.allocateDirect(sizeOrData.size * 4)  // 4 bytes per float
                    bb.order(ByteOrder.nativeOrder())
                    val floatBuffer = bb.asFloatBuffer()
                    floatBuffer.put(sizeOrData)
                    floatBuffer.position(0)

                    GLES30.glBufferData(
                        GLES30.GL_ARRAY_BUFFER,
                        sizeOrData.size * 4,  // size in bytes
                        floatBuffer,
                        usage
                    )
                }
                else -> {
                    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
                    throw IllegalArgumentException("Unsupported data type for buffer data")
                }
            }

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
            return buffer
        }

        fun makeBuffer(sizeInBytes: Int, usage: Int): Int {
            val buffers = IntArray(1)
            GLES30.glGenBuffers(1, buffers, 0)
            val buffer = buffers[0]
            if (buffer == 0) throw RuntimeException("Failed to create buffer")

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffer)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, sizeInBytes, null, usage)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
            checkGLError("makeBuffer")
            return buffer
        }

        fun checkGLError(tag: String) {
            var error: Int
            while (GLES30.glGetError().also { error = it } != GLES30.GL_NO_ERROR) {
                Log.e(tag, "glError: $error")
            }
        }

        fun createAndSetupTexture(
            minFilter: Int = GLES30.GL_NEAREST,
            magFilter: Int = GLES30.GL_NEAREST,
            wrapS: Int = GLES30.GL_CLAMP_TO_EDGE,
            wrapT: Int = GLES30.GL_CLAMP_TO_EDGE
        ): Int {
            val textures = IntArray(1)
            GLES30.glGenTextures(1, textures, 0)
            val texture = textures[0]

            if (texture == 0) {
                Log.e(TAG, "Could not create texture")
                return 0
            }

            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, wrapS)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, wrapT)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, minFilter)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, magFilter)

            return texture
        }

        fun floatArrayToBuffer(array: FloatArray): FloatBuffer {
            return ByteBuffer.allocateDirect(array.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(array)
                    position(0)
                }
        }

        fun shortArrayToBuffer(array: ShortArray): ShortBuffer {
            return ByteBuffer.allocateDirect(array.size * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
                .apply {
                    put(array)
                    position(0)
                }
        }

        fun loadFromAsset(context: Context, fileName: String): String {
            val assetManager = context.assets
            try {
                val inputStream = assetManager.open(fileName)
                val bufferedReader = BufferedReader(InputStreamReader(inputStream))
                val stringBuilder = StringBuilder()
                var line: String?
                do {
                    line = bufferedReader.readLine()
                    if (line != null) {
                        stringBuilder.append(line)
                        stringBuilder.append("\n")
                    }
                } while (line != null)
                bufferedReader.close()
                inputStream.close()
                return stringBuilder.toString()
            } catch (e: Exception) {
                e.printStackTrace()
                return ""
            }
        }

        fun generateRandomBitmap(width: Int, height: Int): Bitmap {
            val bitmap = createBitmap(width, height)
            val canvas = Canvas(bitmap)
            val paint = Paint()
            paint.color = Color.rgb(Random.Default.nextInt(256), Random.Default.nextInt(256), Random.Default.nextInt(256))
            for (x in 0 until width) {
                for (y in 0 until height) {
                    //paint.color = Color.rgb(Random.nextInt(256), Random.nextInt(256), Random.nextInt(256))
                    canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
                }
            }

            return bitmap
        }

        fun createDefaultBackgroundBitmap(): Bitmap {
            val width = 1024
            val height = 1024
            val bitmap = createBitmap(width, height)
            val canvas = Canvas(bitmap)

            val paint = Paint()
            val gradient = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf("#000033".toColorInt(), "#000011".toColorInt()),
                null,
                Shader.TileMode.CLAMP
            )
            paint.shader = gradient

            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

            val starPaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }

            for (i in 0 until 200) {
                val x = Random.Default.nextFloat() * width
                val y = Random.Default.nextFloat() * height
                val size = Random.Default.nextFloat() * 3f + 1f
                val alpha = (Random.Default.nextFloat() * 200 + 55).toInt()

                starPaint.alpha = alpha
                canvas.drawCircle(x, y, size, starPaint)
            }

            return bitmap
        }

        fun intColorToVector3f(color: Int): Vector3f {
            val r = ((color shr 16) and 0xFF) / 255f
            val g = ((color shr 8) and 0xFF) / 255f
            val b = (color and 0xFF) / 255f
            return Vector3f(r, g, b)
        }

    }
}