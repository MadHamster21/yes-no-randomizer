package com.sblashkov.yesnorandomizer.ui

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.os.Handler
import android.os.HandlerThread
import android.view.TextureView

/** Compose the GL result inside the app window, avoiding SurfaceView resize seams. */
class DiceTextureView(
  context: Context,
  initialColors: DiceColors,
  initialRotationX: Float = 0f,
  initialRotationY: Float = 0f
) : TextureView(context), TextureView.SurfaceTextureListener {
  private val thread = HandlerThread("AnswerDiceGL").apply { start() }
  private val handler = Handler(thread.looper)
  private val renderer = DiceRenderer(context, initialColors, initialRotationX, initialRotationY)

  // UI-thread ownership. A destroyed texture is released by the GL thread only
  // after its EGL surface is gone; TextureView must not release it concurrently.
  private var attachedTexture: SurfaceTexture? = null

  @Volatile
  private var closed = false

  // Everything below is accessed only on the GL thread.
  private var texture: SurfaceTexture? = null
  private var resumed = false
  private var surfaceWidth = 0
  private var surfaceHeight = 0
  private var display = EGL14.EGL_NO_DISPLAY
  private var eglContext = EGL14.EGL_NO_CONTEXT
  private var eglSurface = EGL14.EGL_NO_SURFACE
  private val drawFrame = Runnable { drawFrame() }

  init {
    isOpaque = true // Every frame includes the same opaque background as Compose.
    surfaceTextureListener = this
  }

  fun onResume() = enqueue { resumed = true; requestFrame() }

  fun onPause() = enqueue {
    resumed = false
    handler.removeCallbacks(drawFrame)
    releaseEgl()
  }

  fun updateColors(colors: DiceColors) = enqueue {
    renderer.setDiceColors(colors)
    requestFrame()
  }

  fun updateRotation(x: Float, y: Float) = enqueue {
    renderer.updateRotation(x, y)
    requestFrame()
  }

  /** Called when Compose permanently removes this view (including PiP entry). */
  fun close() {
    if (closed) return
    closed = true
    handler.post {
      resumed = false
      handler.removeCallbacks(drawFrame)
      releaseEgl()
    }
    // If still attached, onSurfaceTextureDestroyed finishes texture ownership
    // before quitting. Otherwise all pending cleanup can drain immediately.
    if (attachedTexture == null) thread.quitSafely()
  }

  override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
    attachedTexture = surface
    enqueue {
      texture = surface
      surfaceWidth = width
      surfaceHeight = height
      requestFrame()
    }
  }

  override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
    enqueue {
      surfaceWidth = width
      surfaceHeight = height
      requestFrame()
    }
  }

  override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
    attachedTexture = null
    val queued = handler.post {
      if (texture === surface) {
        handler.removeCallbacks(drawFrame)
        releaseEgl()
        texture = null
      }
      surface.release()
      if (closed) thread.quitSafely()
    }
    // A closed, never-rendered view may already have stopped its thread.
    return !queued
  }

  override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

  private fun enqueue(action: () -> Unit) {
    if (!closed) handler.post { if (!closed) action() }
  }

  private fun requestFrame() {
    // Coalesce rotation/color updates; do not render continuously while idle.
    handler.removeCallbacks(drawFrame)
    handler.post(drawFrame)
  }

  private fun drawFrame() {
    val target = texture ?: return
    if (closed || !resumed || surfaceWidth <= 0 || surfaceHeight <= 0) return
    if (eglContext == EGL14.EGL_NO_CONTEXT) createEgl(target)
    renderer.onSurfaceChanged(null, surfaceWidth, surfaceHeight)
    renderer.onDrawFrame(null)
    if (!EGL14.eglSwapBuffers(display, eglSurface)) {
      // Recreate lost EGL resources on the next update/resume, rather than
      // spinning a render loop while the window is being removed.
      releaseEgl()
    }
  }

  private fun createEgl(target: SurfaceTexture) {
    try {
      display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
      check(display != EGL14.EGL_NO_DISPLAY)
      val version = IntArray(2)
      check(EGL14.eglInitialize(display, version, 0, version, 1))
      val configs = arrayOfNulls<EGLConfig>(1)
      val count = IntArray(1)
      check(
        EGL14.eglChooseConfig(
          display, intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_DEPTH_SIZE, 16, EGL14.EGL_NONE
          ), 0, configs, 0, 1, count, 0
        ) && count[0] > 0
      )
      val config = checkNotNull(configs[0])
      eglContext = EGL14.eglCreateContext(
        display, config, EGL14.EGL_NO_CONTEXT,
        intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
      )
      check(eglContext != EGL14.EGL_NO_CONTEXT)
      eglSurface = EGL14.eglCreateWindowSurface(
        display, config, target,
        intArrayOf(EGL14.EGL_NONE), 0
      )
      check(eglSurface != EGL14.EGL_NO_SURFACE)
      check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext))
      renderer.onSurfaceCreated(null, null)
    } catch (error: RuntimeException) {
      releaseEgl()
      throw error
    }
  }

  private fun releaseEgl() {
    if (display == EGL14.EGL_NO_DISPLAY) return
    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
    if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
    if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext)
    EGL14.eglTerminate(display)
    EGL14.eglReleaseThread()
    display = EGL14.EGL_NO_DISPLAY
    eglSurface = EGL14.EGL_NO_SURFACE
    eglContext = EGL14.EGL_NO_CONTEXT
  }
}
