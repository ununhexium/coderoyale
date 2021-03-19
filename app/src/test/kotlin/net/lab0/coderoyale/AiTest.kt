package net.lab0.coderoyale

import QueenAction
import Battlefield
import Decision
import EMPTY_STRUCTURE_TYPE
import FRIENDLY_OWNER
import MapSite
import Memory
import NO_OWNER
import Position
import QUEEN_TYPE
import Site
import Sites
import Soldier
import TouchedSite
import TrainingAction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import playTurn

internal class AiTest {
  @Test
  fun `can wait`() {
    // when
    val out = playTurn(Decision(QueenAction.Wait, TrainingAction.None))

    // then
    assertThat(out[0]).isEqualTo("WAIT")
    assertThat(out[1]).isEqualTo("TRAIN")
  }

  @Test
  fun `can move`() {
    // when
    val out = playTurn(Decision(QueenAction.Move(12, 116), TrainingAction.None))

    // then
    assertThat(out[0]).isEqualTo("MOVE 12 116")
    assertThat(out[1]).isEqualTo("TRAIN")
  }

  @Test
  fun `can build knights barracks`() {
    // when
    val out = playTurn(Decision(QueenAction.BuildStable(116), TrainingAction.None))

    // then
    assertThat(out[0]).isEqualTo("BUILD 116 BARRACKS-KNIGHT")
    assertThat(out[1]).isEqualTo("TRAIN")
  }

  @Test
  fun `can build archer barracks`() {
    // when
    val out = playTurn(Decision(QueenAction.BuildArchery(116), TrainingAction.None))

    // then
    assertThat(out[0]).isEqualTo("BUILD 116 BARRACKS-ARCHER")
    assertThat(out[1]).isEqualTo("TRAIN")
  }

  @Test
  fun `can train at 3 sites`() {
    // when
    val out = playTurn(Decision(QueenAction.Wait, TrainingAction.AtLocation(1, 10, 116)))

    // then
    assertThat(out[0]).isEqualTo("WAIT")
    assertThat(out[1]).isEqualTo("TRAIN 1 10 116")
  }

  @Test
  fun `can do nothing`() {
    // when
    val out = playTurn(Decision(QueenAction.Wait, TrainingAction.None))

    // then
    assertThat(out[0]).isEqualTo("WAIT")
    assertThat(out[1]).isEqualTo("TRAIN")
  }
}