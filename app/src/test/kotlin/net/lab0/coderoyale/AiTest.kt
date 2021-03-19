package net.lab0.coderoyale

import QueenAction
import TrainingAction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class AiTest {
  @Test
  fun `can wait`() {
    // when
    val game = buildGame {}
    game.playTurn(buildBattlefield { })
    val out = game.lastOutput

    // then
    assertThat(out[0]).isEqualTo("WAIT")
    assertThat(out[1]).isEqualTo("TRAIN")
  }

  @Test
  fun `can move`() {
    // when
    val game = buildGame {
      it.strategy {
        it.decision {
          it.intent(QueenAction.Move(12, 116))
        }
      }
    }
    game.playTurn(buildBattlefield { })
    val out = game.lastOutput

    // then
    assertThat(out[0]).isEqualTo("MOVE 12 116")
    assertThat(out[1]).isEqualTo("TRAIN")
  }

  @Test
  fun `can build knights barracks`() {
    // when
    val game = buildGame {
      it.strategy {
        it.decision {
          it.intent(QueenAction.BuildStable(116))
        }
      }
    }
    game.playTurn(buildBattlefield { })
    val out = game.lastOutput

    // then
    assertThat(out[0]).isEqualTo("BUILD 116 BARRACKS-KNIGHT")
    assertThat(out[1]).isEqualTo("TRAIN")
  }

  @Test
  fun `can build archer barracks`() {
    // when
    val game = buildGame {
      it.strategy {
        it.decision {
          it.intent(QueenAction.BuildArchery(116))
        }
      }
    }
    game.playTurn(buildBattlefield { })
    val out = game.lastOutput

    // then
    assertThat(out[0]).isEqualTo("BUILD 116 BARRACKS-ARCHER")
    assertThat(out[1]).isEqualTo("TRAIN")
  }

  @Test
  fun `can train at 3 sites`() {
    // when
    val game = buildGame {
      it.strategy {
        it.decision {
          it.training(TrainingAction.AtLocation(1, 10, 116))
        }
      }
    }
    game.playTurn(buildBattlefield { })
    val out = game.lastOutput

    // then
    assertThat(out[0]).isEqualTo("WAIT")
    assertThat(out[1]).isEqualTo("TRAIN 1 10 116")
  }
}