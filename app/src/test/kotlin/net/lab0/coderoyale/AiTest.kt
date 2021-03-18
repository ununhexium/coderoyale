package net.lab0.coderoyale

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import playTurn

internal class AiTest {
  @Test
  fun `can wait`() {
    // when
    val out = playTurn(Action.Wait, TrainSoldiers.None)

    // then
    assertThat(out[0]).isEqualTo("WAIT")
    assertThat(out[1]).isEqualTo("TRAIN")
  }

  @Test
  fun `can move`() {
    // when
    val out = playTurn(Action.Move(12, 116), TrainSoldiers.None)

    // then
    assertThat(out[0]).isEqualTo("MOVE 12 116")
    assertThat(out[1]).isEqualTo("TRAIN")
  }

  @Test
  fun `can build knights barracks`() {
    // when
    val out = playTurn(Action.BuildKnightBarracks(116), TrainSoldiers.None)

    // then
    assertThat(out[0]).isEqualTo("BUILD 116 BARRACKS-KNIGHT")
    assertThat(out[1]).isEqualTo("TRAIN")
  }

  @Test
  fun `can build archer barracks`() {
    // when
    val out = playTurn(Action.BuildArcherBarracks(116), TrainSoldiers.None)

    // then
    assertThat(out[0]).isEqualTo("BUILD 116 BARRACKS-ARCHER")
    assertThat(out[1]).isEqualTo("TRAIN")
  }

  @Test
  fun `can train at 3 sites`() {
    // when
    val out = playTurn(Action.Wait, TrainSoldiers.AtLocation(1,10, 116))

    // then
    assertThat(out[0]).isEqualTo("WAIT")
    assertThat(out[1]).isEqualTo("TRAIN 1 10 116")
  }

  @Test
  fun `can do nothing`() {
    // when
    val out = playTurn(Action.Wait, TrainSoldiers.None)

    // then
    assertThat(out[0]).isEqualTo("WAIT")
    assertThat(out[1]).isEqualTo("TRAIN")
  }
}