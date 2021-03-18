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
}