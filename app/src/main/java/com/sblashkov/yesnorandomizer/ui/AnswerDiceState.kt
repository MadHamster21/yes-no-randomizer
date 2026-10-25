package com.sblashkov.yesnorandomizer.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.sblashkov.yesnorandomizer.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun rememberAnswerDiceState(
  @StringRes initialAnswer: Int = if (Random.nextBoolean()) R.string.yes_value else R.string.no_value
): AnswerDiceState {
  val scope = rememberCoroutineScope()
  return rememberSaveable(
    saver = Saver(
    save = { if (it.answerForRestore == R.string.yes_value) 1 else 0 },
    restore = {
      AnswerDiceState(
        when (it) {
          1 -> R.string.yes_value
          0 -> R.string.no_value
          else -> initialAnswer // Compatibility with the old undecided sentinel.
        }, scope
      )
    }
  )) { AnswerDiceState(initialAnswer, scope) }
}

class AnswerDiceState internal constructor(
  @StringRes initialAnswer: Int,
  private val scope: CoroutineScope
) {
  private val initialFace =
    if (initialAnswer == R.string.yes_value) DiceFace.FRONT else DiceFace.BACK

  @get:StringRes
  var answer by mutableIntStateOf(initialAnswer)
    internal set

  // Repeated Yes/No landings still animate the floating answer.
  var answerGeneration by mutableIntStateOf(0)
    internal set

  var isRolling by mutableStateOf(false)
    private set

  internal var orientation by mutableStateOf(initialFace.orientation)
    private set

  internal var rotationXDegrees by mutableFloatStateOf(normalizeDegrees(initialFace.rotationX))
    private set
  internal var rotationYDegrees by mutableFloatStateOf(normalizeDegrees(initialFace.rotationY))
    private set
  internal var landingFeedback by mutableIntStateOf(0)
    private set

  private var motion: Job? = null
  private var motionGeneration = 0
  private var dragging = false
  private var pendingFace: DiceFace? = null

  // Restore a definite upright result, including recreation while held/coasting.
  internal val answerForRestore: Int
    get() = pendingFace?.answer ?: if (dragging) orientation.nearestFace().answer else answer

  fun rollTo(@StringRes selectedAnswer: Int, animate: Boolean = true) {
    if (isRolling) return
    val target = DiceFace.entries.filter { it.answer == selectedAnswer }.random()
    val generation = cancelMotion()
    pendingFace = target
    isRolling = true
    // Button outcomes are selected in advance, preserving the existing saved-
    // state contract. Gesture outcomes are committed only after they land.
    answer = selectedAnswer
    if (!animate) {
      finish(target, feedback = false)
      return
    }
    val startX = rotationXDegrees
    val startY = rotationYDegrees
    val deltaX = 360f + normalizeDegrees(target.rotationX - startX)
    val deltaY = 360f + normalizeDegrees(target.rotationY - startY)
    motion = scope.launch {
      animateProgress(3000, generation, smooth = true) { progress ->
        rotationXDegrees = startX + deltaX * progress
        rotationYDegrees = startY + deltaY * progress
        orientation = DiceOrientation.fromEuler(rotationXDegrees, rotationYDegrees)
      }
      if (generation == motionGeneration) finish(target, feedback = true)
    }
  }

  internal fun catchIfMoving(): Boolean {
    if (!isRolling) return false
    beginDrag()
    return true
  }

  internal fun beginDrag() {
    cancelMotion()
    pendingFace = null
    dragging = true
    isRolling = true
  }

  internal fun dragBy(horizontalDegrees: Float, verticalDegrees: Float) {
    if (dragging) orientation = orientation.dragged(horizontalDegrees, verticalDegrees)
  }

  internal fun release(horizontalVelocity: Float, verticalVelocity: Float) {
    if (!dragging) return
    val fling = DiceFling(orientation, horizontalVelocity, verticalVelocity)
    val generation = cancelMotion()
    dragging = false
    pendingFace = fling.landing
    motion = scope.launch {
      if (fling.durationMillis > 0) {
        animateProgress(fling.durationMillis, generation) { orientation = fling.orientationAt(it) }
      }
      val start = orientation
      animateProgress(DiceFling.SETTLE_MILLIS, generation, smooth = true) {
        orientation = start.towards(fling.landing.orientation, it)
      }
      if (generation == motionGeneration) finish(fling.landing, feedback = true)
    }
  }

  /** Do not leave a held or half-animated die behind when Android hides the UI. */
  internal fun settleForBackground() {
    if (!isRolling) return
    val face = pendingFace ?: orientation.nearestFace()
    cancelMotion()
    finish(face, feedback = false)
  }

  private fun cancelMotion(): Int {
    motionGeneration++
    motion?.cancel()
    motion = null
    return motionGeneration
  }

  private suspend fun animateProgress(
    duration: Int,
    generation: Int,
    smooth: Boolean = false,
    update: (Float) -> Unit
  ) {
    // Compose's animation clock also honors Android's animator duration scale,
    // including "Remove animations". Physics chooses the same result either way.
    Animatable(0f).animateTo(
      1f, tween(
        duration,
        easing = if (smooth) FastOutSlowInEasing else LinearEasing
      )
    ) {
      if (generation == motionGeneration) update(value)
    }
  }

  private fun finish(face: DiceFace, feedback: Boolean) {
    orientation = face.orientation
    rotationXDegrees = normalizeDegrees(face.rotationX)
    rotationYDegrees = normalizeDegrees(face.rotationY)
    answer = face.answer
    answerGeneration++
    dragging = false
    pendingFace = null
    isRolling = false
    motion = null
    if (feedback) landingFeedback++
  }
}

private fun normalizeDegrees(degrees: Float) = ((degrees % 360f) + 360f) % 360f
