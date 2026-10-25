package com.sblashkov.yesnorandomizer.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker

/** Own drags starting on the die; leave taps and scrolling elsewhere alone. */
internal fun Modifier.diceGestures(state: AnswerDiceState): Modifier = pointerInput(state) {
  awaitEachGesture {
    val down = awaitFirstDown()
    val tracker = VelocityTracker()
    tracker.addPosition(down.uptimeMillis, down.position)
    val degreesPerPixel = 180f / minOf(size.width, size.height).coerceAtLeast(1)
    var held = state.catchIfMoving()
    var accumulated = Offset.Zero
    if (held) down.consume()
    try {
      while (true) {
        val event = awaitPointerEvent()
        // Track the original finger only. Additional fingers do not pinch,
        // twist, or take over when that finger lifts.
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        if (change.isConsumed) break
        tracker.addPosition(change.uptimeMillis, change.position)
        val delta = change.positionChange()
        if (held) {
          state.dragBy(delta.x * degreesPerPixel, delta.y * degreesPerPixel)
          change.consume()
        } else if (change.pressed) {
          accumulated += delta
          val distance = accumulated.getDistance()
          if (distance > viewConfiguration.touchSlop) {
            val movement = accumulated * ((distance - viewConfiguration.touchSlop) / distance)
            state.beginDrag()
            held = true
            state.dragBy(movement.x * degreesPerPixel, movement.y * degreesPerPixel)
            change.consume()
          }
        }
        if (change.changedToUpIgnoreConsumed()) {
          if (held) {
            val velocity = tracker.calculateVelocity()
            state.release(velocity.x * degreesPerPixel, velocity.y * degreesPerPixel)
            held = false
          }
          break
        }
      }
    } finally {
      // Pointer cancellation, navigation, or a lost gesture must not strand the
      // UI in "rolling". Cancellation settles without adding invented momentum.
      if (held) state.release(0f, 0f)
    }
  }
}
