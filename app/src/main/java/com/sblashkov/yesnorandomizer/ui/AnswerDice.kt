package com.sblashkov.yesnorandomizer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import com.sblashkov.yesnorandomizer.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.random.Random

@Composable
fun rememberAnswerDiceState(
  @StringRes
  initialAnswer: Int = R.string.answer_no_decision
): AnswerDiceState {
  val scope = rememberCoroutineScope()
  val rotationX = remember { Animatable(0f) }
  val rotationY = remember { Animatable(0f) }

  return remember(scope, rotationX, rotationY) {
    AnswerDiceState(
      initialAnswer = initialAnswer,
      rotationX = rotationX,
      rotationY = rotationY,
      scope = scope
    )
  }
}

class AnswerDiceState internal constructor(
  @StringRes
  initialAnswer: Int,
  private val rotationX: Animatable<Float, AnimationVector1D>,
  private val rotationY: Animatable<Float, AnimationVector1D>,
  private val scope: CoroutineScope
) {
  @get:StringRes
  var answer by mutableIntStateOf(initialAnswer)
    internal set

  var isRolling by mutableStateOf(false)
    private set

  internal val rotationXDegrees: Float
    get() = rotationX.value

  internal val rotationYDegrees: Float
    get() = rotationY.value

  fun rollTo(@StringRes selectedAnswer: Int) {
    if (isRolling) return

    val possibleTargets = listOf(
      Target(0f, 0f),      // Front (Wait -> Yes)
      Target(0f, 180f),    // Back (No)
      Target(-90f, 0f),    // Top (Yes)
      Target(90f, 0f),     // Bottom (No)
      Target(0f, 90f),     // Left (Yes)
      Target(0f, -90f)     // Right (No)
    ).filter { target ->
      val answerAtTarget = when {
        target.rotationX == 0f && target.rotationY == 0f -> R.string.yes_value
        target.rotationX == 0f && target.rotationY == 180f -> R.string.no_value
        target.rotationX == -90f -> R.string.yes_value
        target.rotationX == 90f -> R.string.no_value
        target.rotationY == 90f -> R.string.yes_value
        target.rotationY == -90f -> R.string.no_value
        else -> R.string.yes_value
      }
      answerAtTarget == selectedAnswer
    }

    val target = possibleTargets.random()
    val targetY = rotationY.value +
        (Random.nextInt(5, 9) * FULL_ROTATION) +
        degreesUntil(rotationY.value, target.rotationY)
    val targetX = rotationX.value +
        (Random.nextInt(5, 9) * FULL_ROTATION) +
        degreesUntil(rotationX.value, target.rotationX)

    isRolling = true
    answer = selectedAnswer

    scope.launch {
      try {
        val rollSpec = tween<Float>(
          durationMillis = ROLL_DURATION_MILLIS,
          easing = FastOutSlowInEasing
        )
        val yAnimation = launch { rotationY.animateTo(targetY, rollSpec) }
        val xAnimation = launch { rotationX.animateTo(targetX, rollSpec) }

        yAnimation.join()
        xAnimation.join()

        rotationY.snapTo(normalizeDegrees(target.rotationY))
        rotationX.snapTo(normalizeDegrees(target.rotationX))
      } finally {
        isRolling = false
      }
    }
  }

  private data class Target(val rotationX: Float, val rotationY: Float)
}

@Composable
fun AnswerDice(
  state: AnswerDiceState,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val colorScheme = MaterialTheme.colorScheme
  val diceColors = remember(colorScheme, state.answer) {
    DiceColors(
      background = colorScheme.background.toArgb(),
      // Use Primary and Tertiary for maximum vibrancy
      yesColor = colorScheme.primary.toArgb(),
      onYesColor = colorScheme.onPrimary.toArgb(),
      noColor = colorScheme.tertiary.toArgb(),
      onNoColor = colorScheme.onTertiary.toArgb(),
      isInitialState = state.answer == R.string.answer_no_decision
    )
  }

  val glSurfaceView = remember {
    DiceGLSurfaceView(context)
  }

  // Explicitly update colors whenever the color scheme changes
  LaunchedEffect(diceColors) {
    glSurfaceView.updateColors(diceColors)
  }

  LaunchedEffect(state.rotationXDegrees, state.rotationYDegrees) {
    glSurfaceView.renderer.updateRotation(state.rotationXDegrees, state.rotationYDegrees)
    glSurfaceView.requestRender()
  }

  Box(
    modifier = modifier.size(300.dp),
    contentAlignment = Alignment.Center
  ) {
    AndroidView(
      factory = { glSurfaceView },
      modifier = Modifier.fillMaxSize()
    )
  }
}

class DiceGLSurfaceView(context: Context) : GLSurfaceView(context) {
  val renderer: DiceRenderer = DiceRenderer(context)

  init {
    setEGLContextClientVersion(2)
    setEGLConfigChooser(8, 8, 8, 8, 16, 0)
    setRenderer(renderer)
    renderMode = RENDERMODE_WHEN_DIRTY
  }

  fun updateColors(colors: DiceColors) {
    queueEvent {
      renderer.setDiceColors(colors)
      // Trigger a manual render to show the change immediately
      requestRender()
    }
  }
}

data class DiceColors(
  val background: Int,
  val yesColor: Int,
  val onYesColor: Int,
  val noColor: Int,
  val onNoColor: Int,
  val isInitialState: Boolean = true
)

class DiceRenderer(private val context: Context) : GLSurfaceView.Renderer {
  private var rotationX: Float = 0f
  private var rotationY: Float = 0f
  private var diceColors: DiceColors = DiceColors(
    0, 0, 0, 0, 0 // Initialize with zeros to force first update
  )

  private lateinit var cube: Cube

  private val vPMatrix = FloatArray(16)
  private val projectionMatrix = FloatArray(16)
  private val viewMatrix = FloatArray(16)
  private val rotationMatrix = FloatArray(16)

  fun updateRotation(x: Float, y: Float) {
    rotationX = x
    rotationY = y
  }

  fun setDiceColors(colors: DiceColors) {
    diceColors = colors
    if (::cube.isInitialized) {
      cube.updateColors(colors)
    }
  }

  override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
    updateClearColor()
    GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    GLES20.glDepthFunc(GLES20.GL_LEQUAL)
    GLES20.glEnable(GLES20.GL_CULL_FACE)
    GLES20.glCullFace(GLES20.GL_BACK)
    GLES20.glEnable(GLES20.GL_BLEND)
    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    cube = Cube(context, diceColors)
  }

  private fun updateClearColor() {
    val r = Color.red(diceColors.background) / 255f
    val g = Color.green(diceColors.background) / 255f
    val b = Color.blue(diceColors.background) / 255f
    val a = Color.alpha(diceColors.background) / 255f
    GLES20.glClearColor(r, g, b, a)
  }

  override fun onDrawFrame(gl: GL10?) {
    updateClearColor()
    val scratch = FloatArray(16)
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

    Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, -4.5f, 0f, 0f, 0f, 0f, 1.0f, 0.0f)
    Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

    Matrix.setIdentityM(rotationMatrix, 0)
    Matrix.rotateM(rotationMatrix, 0, rotationX, 1f, 0f, 0f)
    Matrix.rotateM(rotationMatrix, 0, rotationY, 0f, 1f, 0f)

    Matrix.multiplyMM(scratch, 0, vPMatrix, 0, rotationMatrix, 0)

    cube.draw(scratch, rotationMatrix)
  }

  override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
    GLES20.glViewport(0, 0, width, height)
    val ratio: Float = width.toFloat() / height.toFloat()
    Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 2f, 12f)
  }
}

class Cube(private val context: Context, private var diceColors: DiceColors) {
  private val vertexShaderCode =
    "uniform mat4 uVPMatrix;" +
        "uniform mat4 uRotationMatrix;" +
        "attribute vec4 vPosition;" +
        "attribute vec3 vNormal;" +
        "attribute vec2 vTexCoord;" +
        "varying vec2 _vTexCoord;" +
        "varying float _vLight;" +
        "void main() {" +
        "  gl_Position = uVPMatrix * vPosition;" +
        "  _vTexCoord = vTexCoord;" +
        "  vec3 transformedNormal = normalize(vec3(uRotationMatrix * vec4(vNormal, 0.0)));" +
        "  vec3 lightDir = normalize(vec3(0.5, 0.5, 1.0));" +
        "  _vLight = max(dot(transformedNormal, lightDir), 0.0) * 0.2 + 0.8;" +
        "}"

  private val fragmentShaderCode =
    "precision mediump float;" +
        "uniform sampler2D uTexture;" +
        "varying vec2 _vTexCoord;" +
        "varying float _vLight;" +
        "void main() {" +
        "  vec4 color = texture2D(uTexture, _vTexCoord);" +
        "  gl_FragColor = vec4(color.rgb * _vLight, color.a);" +
        "}"

  private var program: Int = 0
  private var textureId: Int = 0

  private val vertexBuffer: FloatBuffer
  private val normalBuffer: FloatBuffer
  private val texCoordBuffer: FloatBuffer
  private val indexBuffer: ShortBuffer

  private val vertices = floatArrayOf(
    // Front
    -1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f,
    // Back
    1.0f, 1.0f, -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, -1.0f, -1.0f, 1.0f, -1.0f, -1.0f,
    // Top
    -1.0f, 1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f, 1.0f, 1.0f, -1.0f, 1.0f, 1.0f,
    // Bottom
    -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f, 1.0f, -1.0f, -1.0f, -1.0f, -1.0f, -1.0f,
    // Left
    -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, -1.0f,
    // Right
    1.0f, 1.0f, 1.0f, 1.0f, 1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, -1.0f, 1.0f
  )

  private val normals = floatArrayOf(
    // Front
    0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f,
    // Back
    0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f,
    // Top
    0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f,
    // Bottom
    0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f,
    // Left
    -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f,
    // Right
    1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f
  )

  private val texCoords = floatArrayOf(
    // Front (1-Yes: 0.0-0.33, 0.0-0.5) - Fine
    0.0f, 0.0f, 0.33f, 0.0f, 0.33f, 0.5f, 0.0f, 0.5f,
    // Back (2-No: 0.33-0.66, 0.0-0.5) - Rotate 180 (User said 180)
    0.33f, 0.0f, 0.66f, 0.0f, 0.66f, 0.5f, 0.33f, 0.5f,
    // Top (3-Yes: 0.66-1.0, 0.0-0.5) - Rotate 180
    1.0f, 0.5f, 0.66f, 0.5f, 0.66f, 0.0f, 1.0f, 0.0f,
    // Bottom (4-No: 0.0-0.33, 0.5-1.0) - Restored and rotated CCW
    0.33f, 1.0f, 0.0f, 1.0f, 0.0f, 0.5f, 0.33f, 0.5f,
    // Left (5-Yes: 0.33-0.66, 0.5-1.0) - Rotate right 90 (CW)
    0.33f, 0.5f, 0.66f, 0.5f, 0.66f, 1.0f, 0.33f, 1.0f,
    // Right (6-No: 0.66-1.0, 0.5-1.0) - Rotate left 90 (CCW)
    0.66f, 0.5f, 1.0f, 0.5f, 1.0f, 1.0f, 0.66f, 1.0f,
  )

  private val indices = shortArrayOf(
    0, 3, 2, 0, 2, 1,       // front
    4, 7, 6, 4, 6, 5,       // back
    8, 11, 10, 8, 10, 9,    // top
    12, 15, 14, 12, 14, 13, // bottom
    16, 19, 18, 16, 18, 17, // left
    20, 23, 22, 20, 22, 21  // right
  )

  init {
    vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4).run {
      order(ByteOrder.nativeOrder())
      asFloatBuffer().apply {
        put(vertices)
        position(0)
      }
    }

    normalBuffer = ByteBuffer.allocateDirect(normals.size * 4).run {
      order(ByteOrder.nativeOrder())
      asFloatBuffer().apply {
        put(normals)
        position(0)
      }
    }

    texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4).run {
      order(ByteOrder.nativeOrder())
      asFloatBuffer().apply {
        put(texCoords)
        position(0)
      }
    }

    indexBuffer = ByteBuffer.allocateDirect(indices.size * 2).run {
      order(ByteOrder.nativeOrder())
      asShortBuffer().apply {
        put(indices)
        position(0)
      }
    }

    val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
    val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

    program = GLES20.glCreateProgram().also {
      GLES20.glAttachShader(it, vertexShader)
      GLES20.glAttachShader(it, fragmentShader)
      GLES20.glLinkProgram(it)
    }

    textureId = generateTexture()
  }

  fun updateColors(colors: DiceColors) {
    diceColors = colors
    // When updating colors (re-generating texture), check if we should keep waitText
    // The generateTexture now uses answer status to decide first face
    GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
    textureId = generateTexture()
  }

  private fun generateTexture(): Int {
    val size = 512
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      textAlign = Paint.Align.CENTER
      isFakeBoldText = true
      typeface = Typeface.SANS_SERIF
    }

    val yesColor = diceColors.yesColor
    val onYesColor = diceColors.onYesColor
    val noColor = diceColors.noColor
    val onNoColor = diceColors.onNoColor

    val yesText = context.getString(R.string.yes_value).uppercase()
    val noText = context.getString(R.string.no_value).uppercase()
    val waitText = context.getString(R.string.answer_no_decision)

    val cellW = size / 3f
    val cellH = size / 2f
    val margin = 8f
    val cornerRadius = 30f
    val horizontalPadding = 20f

    // Fill with transparent color first
    canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

    // Draw 6 cells with rounded corners
    // If we are in initial state, the second face shows "..."
    val secondFaceText = if (diceColors.isInitialState) waitText else noText

    val faces = listOf(
      Triple(yesText, yesColor, onYesColor to 0),
      Triple(secondFaceText, noColor, onNoColor to 1),
      Triple(yesText, yesColor, onYesColor to 2),
      Triple(noText, noColor, onNoColor to 3),
      Triple(yesText, yesColor, onYesColor to 4),
      Triple(noText, noColor, onNoColor to 5)
    )

    faces.forEach { (text, color, theme) ->
      val (textColor, index) = theme
      val col = index % 3
      val row = index / 3
      val left = col * cellW
      val top = row * cellH

      val rect = RectF(left + margin, top + margin, left + cellW - margin, top + cellH - margin)

      // Background with subtle gradient for depth
      // Use a slightly darker version for the gradient end to give a 3D feel
      val endColor = Color.argb(
        Color.alpha(color),
        (Color.red(color) * 0.8f).toInt(),
        (Color.green(color) * 0.8f).toInt(),
        (Color.blue(color) * 0.8f).toInt()
      )

      paint.shader = LinearGradient(
        rect.left, rect.top, rect.right, rect.bottom,
        color, endColor, Shader.TileMode.CLAMP
      )
      paint.style = Paint.Style.FILL
      canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
      paint.shader = null

      // Border
      paint.style = Paint.Style.STROKE
      paint.strokeWidth = 8f
      paint.color = Color.argb(60, 0, 0, 0)
      canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

      // Dynamic Text Scaling
      paint.style = Paint.Style.FILL
      paint.color = textColor

      val maxTextWidth = rect.width() - horizontalPadding
      var targetTextSize = 85f // Start with a large size
      paint.textSize = targetTextSize

      // Shrink text size until it fits the cell width
      while (paint.measureText(text) > maxTextWidth && targetTextSize > 20f) {
        targetTextSize -= 2f
        paint.textSize = targetTextSize
      }

      // Perfectly center text vertically
      val textBounds = android.graphics.Rect()
      paint.getTextBounds(text, 0, text.length, textBounds)
      val centerY = rect.centerY() - textBounds.centerY()

      canvas.drawText(text, rect.centerX(), centerY, paint)
    }

    val textures = IntArray(1)
    GLES20.glGenTextures(1, textures, 0)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])

    GLES20.glTexParameteri(
      GLES20.GL_TEXTURE_2D,
      GLES20.GL_TEXTURE_MIN_FILTER,
      GLES20.GL_LINEAR_MIPMAP_LINEAR
    )
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
    GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)

    bitmap.recycle()

    return textures[0]
  }

  fun draw(vPMatrix: FloatArray, rotationMatrix: FloatArray) {
    GLES20.glUseProgram(program)

    val positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
    GLES20.glEnableVertexAttribArray(positionHandle)
    GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

    val normalHandle = GLES20.glGetAttribLocation(program, "vNormal")
    GLES20.glEnableVertexAttribArray(normalHandle)
    GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, normalBuffer)

    val texCoordHandle = GLES20.glGetAttribLocation(program, "vTexCoord")
    GLES20.glEnableVertexAttribArray(texCoordHandle)
    GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

    val vPMatrixHandle = GLES20.glGetUniformLocation(program, "uVPMatrix")
    GLES20.glUniformMatrix4fv(vPMatrixHandle, 1, false, vPMatrix, 0)

    val rotationMatrixHandle = GLES20.glGetUniformLocation(program, "uRotationMatrix")
    GLES20.glUniformMatrix4fv(rotationMatrixHandle, 1, false, rotationMatrix, 0)

    val textureHandle = GLES20.glGetUniformLocation(program, "uTexture")
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
    GLES20.glUniform1i(textureHandle, 0)

    GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.size, GLES20.GL_UNSIGNED_SHORT, indexBuffer)

    GLES20.glDisableVertexAttribArray(positionHandle)
    GLES20.glDisableVertexAttribArray(normalHandle)
    GLES20.glDisableVertexAttribArray(texCoordHandle)
  }

  private fun loadShader(type: Int, shaderCode: String): Int {
    return GLES20.glCreateShader(type).also { shader ->
      GLES20.glShaderSource(shader, shaderCode)
      GLES20.glCompileShader(shader)
    }
  }
}

private fun degreesUntil(currentDegrees: Float, targetDegrees: Float): Float {
  return normalizeDegrees(targetDegrees - normalizeDegrees(currentDegrees))
}

private fun normalizeDegrees(degrees: Float): Float {
  return ((degrees % FULL_ROTATION) + FULL_ROTATION) % FULL_ROTATION
}

private const val FULL_ROTATION = 360f
private const val ROLL_DURATION_MILLIS = 3000
