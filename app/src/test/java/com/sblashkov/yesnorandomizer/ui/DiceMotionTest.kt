package com.sblashkov.yesnorandomizer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DiceMotionTest {
  private fun sameOrientation(expected: DiceOrientation, actual: DiceOrientation) {
    assertEquals(1f, abs(expected.dot(actual)), 0.0001f)
  }

  @Test
  fun everyCanonicalLandingFacesTheCameraAndHasAnOrthonormalMatrix() {
    for (face in DiceFace.entries) {
      assertEquals(face, face.orientation.nearestFace())
      val m = face.orientation.matrix()
      assertEquals(-1f, m[2] * face.normalX + m[6] * face.normalY + m[10] * face.normalZ, 0.0001f)
      for (a in 0..2) {
        for (b in 0..2) {
          val dot = (0..2).sumOf { (m[a * 4 + it] * m[b * 4 + it]).toDouble() }
          assertEquals(if (a == b) 1.0 else 0.0, dot, 0.0001)
        }
      }
    }
  }

  @Test
  fun gentleTurnsLetTheUserChooseFacesWithoutAnyRandomness() {
    val start = DiceFace.BACK.orientation
    assertEquals(DiceFace.LEFT, DiceFling(start.dragged(-90f, 0f), 0f, 0f).landing)
    assertEquals(DiceFace.RIGHT, DiceFling(start.dragged(90f, 0f), 0f, 0f).landing)
    assertEquals(DiceFace.TOP, DiceFling(start.dragged(0f, 90f), 0f, 0f).landing)
    assertEquals(DiceFace.BOTTOM, DiceFling(start.dragged(0f, -90f), 0f, 0f).landing)
  }

  @Test
  fun screenDirectionDoesNotReverseAfterTheCubeTurnsOver() {
    val turn = DiceFace.BACK.orientation.dragged(25f, -15f)
    // A screen-space turn must be the same pre-multiplication, independent of
    // the cube's current local axes (including upside-down orientations).
    for (face in DiceFace.entries) {
      sameOrientation(turn * face.orientation, face.orientation.dragged(25f, -15f))
    }
  }

  @Test
  fun circularAndReversedDragsStayFiniteAndDoNotAccumulateScale() {
    var orientation = DiceFace.BACK.orientation
    repeat(1000) {
      orientation = orientation.dragged(3f, 2f).dragged(-3f, -2f)
    }
    sameOrientation(DiceFace.BACK.orientation, orientation)
    repeat(1000) {
      orientation = orientation.dragged(3f, 0f).dragged(0f, 3f)
        .dragged(-3f, 0f).dragged(0f, -3f)
    }
    assertEquals(1f, orientation.dot(orientation), 0.0001f)
    assertTrue(orientation.matrix().all { it.isFinite() })
  }

  @Test
  fun momentumDependsOnRecentVelocityAndRetainsItsDirection() {
    val start = DiceFace.BACK.orientation
    val fling = DiceFling(start, 400f, 150f)
    sameOrientation(start, fling.orientationAt(0f))
    val early = fling.orientationAt(0.1f)
    assertTrue(early.dot(start) < 0.9999f)
    val opposite = DiceFling(start, -400f, -150f).orientationAt(0.1f)
    sameOrientation(start, opposite * early)
    assertEquals(fling.orientationAt(1f).nearestFace(), fling.landing)
    repeat(20) { assertEquals(fling.landing, DiceFling(start, 400f, 150f).landing) }
  }

  @Test
  fun theSameOrientationCanLandOnDifferentAnswersWithDifferentReleaseMomentum() {
    val start = DiceFace.BACK.orientation
    assertEquals(DiceFace.BACK.answer, DiceFling(start, 0f, 0f).landing.answer)
    assertEquals(DiceFace.LEFT.answer, DiceFling(start, -500f, 0f).landing.answer)
    assertTrue(DiceFace.BACK.answer != DiceFace.LEFT.answer)
  }

  @Test
  fun strongSwipesReachThreeSecondsWhileGentleReleasesStayQuick() {
    for (speed in listOf(0f, 20f, 40f, 200f, 1000f, 100000f, Float.MAX_VALUE)) {
      val fling = DiceFling(DiceFace.BACK.orientation, speed, -speed)
      assertTrue(fling.durationMillis in 0..DiceFling.MAX_COAST_MILLIS)
      assertTrue(fling.durationMillis + DiceFling.SETTLE_MILLIS <= 3000)
      assertTrue(fling.orientationAt(1f).matrix().all { it.isFinite() })
    }
    val capped = DiceFling(DiceFace.BACK.orientation, 2160f, 0f)
    val excessive = DiceFling(DiceFace.BACK.orientation, 100000f, 0f)
    sameOrientation(capped.orientationAt(1f), excessive.orientationAt(1f))
    assertEquals(3000, capped.durationMillis + DiceFling.SETTLE_MILLIS)
    val gentle = DiceFling(DiceFace.BACK.orientation, 200f, 0f)
    val medium = DiceFling(DiceFace.BACK.orientation, 1000f, 0f)
    val strong = DiceFling(DiceFace.BACK.orientation, 1800f, 0f)
    assertTrue(gentle.durationMillis + DiceFling.SETTLE_MILLIS < 700)
    assertTrue(medium.durationMillis > gentle.durationMillis)
    assertTrue(strong.durationMillis > medium.durationMillis)
    assertTrue(strong.durationMillis + DiceFling.SETTLE_MILLIS >= 2700)
  }

  @Test
  fun pausingBeforeReleaseHasNoCoastAndKeepsTheVisibleFace() {
    val orientation = DiceFace.BACK.orientation.dragged(-85f, 5f)
    val fling = DiceFling(orientation, 0f, 0f)
    assertEquals(0, fling.durationMillis)
    assertEquals(orientation.nearestFace(), fling.landing)
    sameOrientation(orientation, fling.orientationAt(1f))
  }

  @Test
  fun uprightCorrectionTakesTheShortPathAndEndsAtTheExactCanonicalPose() {
    for (face in DiceFace.entries) {
      val start = face.orientation.dragged(10f, -5f)
      sameOrientation(start, start.towards(face.orientation, 0f))
      sameOrientation(face.orientation, start.towards(face.orientation, 1f))
      val oppositeSign = DiceOrientation(-start.x, -start.y, -start.z, -start.w)
      sameOrientation(start, start.towards(oppositeSign, 0.5f))
    }
  }
}
