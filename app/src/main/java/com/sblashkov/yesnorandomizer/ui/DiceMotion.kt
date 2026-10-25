package com.sblashkov.yesnorandomizer.ui

import androidx.annotation.StringRes
import com.sblashkov.yesnorandomizer.R
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Order matches the geometry and texture atlas. The camera looks toward +Z.
internal enum class DiceFace(
  @param:StringRes val answer: Int,
  val rotationX: Float,
  val rotationY: Float,
  val normalX: Float,
  val normalY: Float,
  val normalZ: Float
) {
  FRONT(R.string.yes_value, 0f, 180f, 0f, 0f, 1f),
  BACK(R.string.no_value, 0f, 0f, 0f, 0f, -1f),
  TOP(R.string.yes_value, -90f, 0f, 0f, 1f, 0f),
  BOTTOM(R.string.no_value, 90f, 0f, 0f, -1f, 0f),
  LEFT(R.string.yes_value, 0f, -90f, -1f, 0f, 0f),
  RIGHT(R.string.no_value, 0f, 90f, 1f, 0f, 0f);

  val orientation: DiceOrientation
    get() = DiceOrientation.fromEuler(rotationX, rotationY)
}

/** Unit quaternion; incremental screen-axis turns avoid Euler-axis reversals. */
internal data class DiceOrientation(
  val x: Float,
  val y: Float,
  val z: Float,
  val w: Float
) {
  operator fun times(other: DiceOrientation): DiceOrientation = DiceOrientation(
    w * other.x + x * other.w + y * other.z - z * other.y,
    w * other.y - x * other.z + y * other.w + z * other.x,
    w * other.z + x * other.y - y * other.x + z * other.w,
    w * other.w - x * other.x - y * other.y - z * other.z
  ).normalized()

  private fun normalized(): DiceOrientation {
    val length = sqrt(x * x + y * y + z * z + w * w)
    return DiceOrientation(x / length, y / length, z / length, w / length)
  }

  private fun inverse() = DiceOrientation(-x, -y, -z, w)

  fun dot(other: DiceOrientation) = x * other.x + y * other.y + z * other.z + w * other.w

  /** Positive horizontal/vertical movement follows a right/down finger movement. */
  fun dragged(horizontalDegrees: Float, verticalDegrees: Float): DiceOrientation {
    val angle = hypot(horizontalDegrees, verticalDegrees)
    if (angle == 0f) return this
    val screenTurn = axisAngle(-verticalDegrees / angle, horizontalDegrees / angle, 0f, angle)
    // The renderer applies the resting tilt afterward; undo it around each turn
    // so dragging still follows screen axes after arbitrary circular gestures.
    return RESTING_TILT.inverse() * screenTurn * RESTING_TILT * this
  }

  fun nearestFace(): DiceFace {
    val matrix = (RESTING_TILT * this).matrix()
    return DiceFace.entries.minBy { face ->
      matrix[2] * face.normalX + matrix[6] * face.normalY + matrix[10] * face.normalZ
    }
  }

  fun towards(target: DiceOrientation, fraction: Float): DiceOrientation {
    val t = fraction.coerceIn(0f, 1f)
    var end = target
    var cosine = dot(end)
    // q and -q describe the same orientation. Choose the shorter correction.
    if (cosine < 0f) {
      end = DiceOrientation(-end.x, -end.y, -end.z, -end.w)
      cosine = -cosine
    }
    val angle = acos(cosine.coerceIn(-1f, 1f))
    val a: Float
    val b: Float
    if (cosine > 0.9995f) {
      a = 1f - t
      b = t
    } else {
      val denominator = sin(angle)
      a = sin((1f - t) * angle) / denominator
      b = sin(t * angle) / denominator
    }
    return DiceOrientation(
      a * x + b * end.x, a * y + b * end.y,
      a * z + b * end.z, a * w + b * end.w
    ).normalized()
  }

  /** OpenGL column-major rotation matrix. */
  fun matrix(): FloatArray = floatArrayOf(
    1f - 2f * (y * y + z * z), 2f * (x * y + z * w), 2f * (x * z - y * w), 0f,
    2f * (x * y - z * w), 1f - 2f * (x * x + z * z), 2f * (y * z + x * w), 0f,
    2f * (x * z + y * w), 2f * (y * z - x * w), 1f - 2f * (x * x + y * y), 0f,
    0f, 0f, 0f, 1f
  )

  companion object {
    fun fromEuler(x: Float, y: Float): DiceOrientation =
      axisAngle(1f, 0f, 0f, x) * axisAngle(0f, 1f, 0f, y)

    private fun axisAngle(x: Float, y: Float, z: Float, degrees: Float): DiceOrientation {
      val halfAngle = Math.toRadians(degrees.toDouble()).toFloat() / 2f
      val sine = sin(halfAngle)
      return DiceOrientation(x * sine, y * sine, z * sine, cos(halfAngle))
    }

    val RESTING_TILT = fromEuler(15f, -15f)
  }
}

/** Deterministic momentum: no random face selection and no unbounded spin time. */
internal class DiceFling(
  private val start: DiceOrientation,
  horizontalVelocity: Float,
  verticalVelocity: Float
) {
  private val horizontal = horizontalVelocity.takeIf { it.isFinite() } ?: 0f
  private val vertical = verticalVelocity.takeIf { it.isFinite() } ?: 0f
  private val rawSpeed = hypot(horizontal.toDouble(), vertical.toDouble())
  private val speed = rawSpeed.coerceAtMost(MAX_SPEED.toDouble()).toFloat()
  private val velocityX = if (rawSpeed == 0.0) 0f else (horizontal / rawSpeed * speed).toFloat()
  private val velocityY = if (rawSpeed == 0.0) 0f else (vertical / rawSpeed * speed).toFloat()

  val durationMillis = if (speed <= STOP_SPEED) 0 else
    (ln(speed / STOP_SPEED) / FRICTION * 1000f).roundToInt().coerceAtMost(MAX_COAST_MILLIS)

  fun orientationAt(fraction: Float): DiceOrientation {
    val seconds = durationMillis / 1000f * fraction.coerceIn(0f, 1f)
    val distanceScale = (1f - exp(-FRICTION * seconds)) / FRICTION
    return start.dragged(velocityX * distanceScale, velocityY * distanceScale)
  }

  val landing: DiceFace = orientationAt(1f).nearestFace()

  companion object {
    const val MAX_COAST_MILLIS = 1450
    const val SETTLE_MILLIS = 240
    private const val MAX_SPEED = 2160f
    private const val STOP_SPEED = 40f
    private const val FRICTION = 4f
  }
}
