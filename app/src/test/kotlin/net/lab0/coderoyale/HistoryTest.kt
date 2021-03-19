package net.lab0.coderoyale

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class HistoryTest {
  @Test
  fun `can compute gold gain on first turn`() {
    // given
    val game = buildGame { }

    // then
    assertThat(game.lastTurnGoldGain).isEqualTo(0)
  }

  @Test
  fun `can compute last turns gold gain when no unit produced`() {
    // given
    val game = buildGame {
      history {
        addTurn {
          battlefield {
            gold(0)
          }
        }
      }

      battlefield {
        gold(10)
      }
    }

    // when
    val goldGains = game.lastTurnGoldGain

    // then
    assertThat(goldGains).isEqualTo(10)
  }

  @Test
  fun `can compute last turns gold gain when unit produced`() {
    // given

    // when

    // then

  }
}