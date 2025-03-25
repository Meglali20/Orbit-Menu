package com.oussamameg.orbitmenu.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.oussamameg.orbitmenu.model.OrbitMenuItem
import com.oussamameg.orbitmenu.listener.OrbitSelectionListener
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay

class OrbitMenuSurfaceView : GLSurfaceView {
    companion object {
        const val DEFAULT_GLOW_COLOR = 0
        const val NO_GLOW = -1
    }

    private val orbitMenuRenderer: OrbitMenuRenderer
    var orbitSelectionListener: OrbitSelectionListener? = null
        set(value) {
            orbitMenuRenderer.orbitSelectionListener = value
        }
    var bgColor: Int = 0
        set(value) {
            orbitMenuRenderer.clearColor = value
        }

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : super(
        context,
        attrs
    ) {
        setEGLContextClientVersion(3)
        setEGLContextFactory(ContextFactory())
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        holder.setFormat(PixelFormat.RGBA_8888)
        setZOrderOnTop(false)
        setBackgroundColor(Color.Transparent.toArgb())
        orbitMenuRenderer = OrbitMenuRenderer(this, context)
        orbitMenuRenderer.orbitSelectionListener = orbitSelectionListener
        setRenderer(orbitMenuRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun snapToImage(index: Int) {
        queueEvent {
            orbitMenuRenderer.snapToImage(index)
        }
    }

    fun immediateSnapToImage(index: Int) {
        queueEvent {
            orbitMenuRenderer.immediateSnapToImage(index)
        }
    }

    fun setOrbitMenuItems(items: List<OrbitMenuItem>, initialSelectedIndex: Int = 0) {
        queueEvent {
            orbitMenuRenderer.setOrbitMenuItems(items, initialSelectedIndex = initialSelectedIndex)
        }
    }

    fun setBackgroundImage(bitmap: Bitmap) {
        queueEvent {
            orbitMenuRenderer.setBackgroundImage(bitmap)
        }
    }

    fun toggleImageSkewing(enable: Boolean) {
        orbitMenuRenderer.toggleImageSkewing(enable)
    }

    fun getImageScreenPosition(width: Int, height: Int): Rect {
        return orbitMenuRenderer.calculateDiskScreenRect(width, height)
    }


    fun paused(isPaused: Boolean) {
        orbitMenuRenderer.isPaused = isPaused
    }


    private class ContextFactory : EGLContextFactory {
        override fun createContext(
            egl: EGL10,
            display: EGLDisplay?,
            eglConfig: EGLConfig?
        ): EGLContext {
            val attribList = intArrayOf(EGL_CONTEXT_CLIENT_VERSION, 3, EGL10.EGL_NONE)
            return egl.eglCreateContext(display, eglConfig, EGL10.EGL_NO_CONTEXT, attribList)
        }

        override fun destroyContext(egl: EGL10, display: EGLDisplay?, context: EGLContext?) {
            egl.eglDestroyContext(display, context)
        }

        companion object {
            private const val EGL_CONTEXT_CLIENT_VERSION = 0x3098
        }

    }
}

