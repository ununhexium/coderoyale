package net.lab0.coderoyale

import Action
import Battlefield
import EMPTY_STRUCTURE_TYPE
import FRIENDLY_OWNER
import MapSite
import Memory
import NO_OWNER
import Position
import QUEEN_TYPE
import Site
import Soldier
import TouchedSite
import Training
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import playTurn

internal class AiTest {
  @Test
  fun `can wait`() {
    // when
    val out = playTurn(Action.Wait, Training.None)

    // then
    assertThat(out[0]).isEqualTo("WAIT")
    assertThat(out[1]).isEqualTo("TRAIN")
  }

  @Test
  fun `can move`() {
    // when
    val out = playTurn(Action.Move(12, 116), Training.None)

    // then
    assertThat(out[0]).isEqualTo("MOVE 12 116")
    assertThat(out[1]).isEqualTo("TRAIN")
  }

  @Test
  fun `can build knights barracks`() {
    // when
    val out = playTurn(Action.BuildStable(116), Training.None)

    // then
    assertThat(out[0]).isEqualTo("BUILD 116 BARRACKS-KNIGHT")
    assertThat(out[1]).isEqualTo("TRAIN")
  }

  @Test
  fun `can build archer barracks`() {
    // when
    val out = playTurn(Action.BuildArchery(116), Training.None)

    // then
    assertThat(out[0]).isEqualTo("BUILD 116 BARRACKS-ARCHER")
    assertThat(out[1]).isEqualTo("TRAIN")
  }

  @Test
  fun `can train at 3 sites`() {
    // when
    val out = playTurn(Action.Wait, Training.AtLocation(1, 10, 116))

    // then
    assertThat(out[0]).isEqualTo("WAIT")
    assertThat(out[1]).isEqualTo("TRAIN 1 10 116")
  }

  @Test
  fun `can do nothing`() {
    // when
    val out = playTurn(Action.Wait, Training.None)

    // then
    assertThat(out[0]).isEqualTo("WAIT")
    assertThat(out[1]).isEqualTo("TRAIN")
  }

  @Test
  fun `can find the empty sites`() {
    // given
    val mapSite1 = MapSite(1, Position(0, 0), 5)
    val mapSite2 = MapSite(2, Position(25, 25), 5)
    val mapSites = listOf(mapSite1, mapSite2)
    val sites = mapSites.map {
      Site(it.siteId, it, 0, 0, EMPTY_STRUCTURE_TYPE, NO_OWNER, 0, 0)
    }
    val queen = Soldier(Position(5, 5), FRIENDLY_OWNER, QUEEN_TYPE, 100)
    val battlefield = Battlefield(Memory(), mapSites, 0, TouchedSite(0), sites, listOf(queen))

    // when
    val emptySites = battlefield.emptySites

    // then
    assertThat(emptySites).containsExactly(mapSite1, mapSite2)
  }
}