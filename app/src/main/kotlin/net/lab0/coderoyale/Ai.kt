import java.util.*

/**
 * Auto-generated code below aims at helping you parse
 * the standard input according to the problem statement.
 */

fun debug(any: Any?) {
  System.err.println(any.toString())
}

inline class TouchedSite(val touchedSite: Int) {
  val touches: Boolean
    get() = touchedSite != -1
}

data class BuildingGround(val siteId: Int, val x: Int, val y: Int, val radius: Int)

data class Battlefield(
  val gold: Int,
  val touchedSite: TouchedSite,
  val sites: List<Site>,
  val units: List<Soldier>
)

data class Site(
  val siteId: Int,
  val ignore1: Int,
  val ignore2: Int,
  val structureType: Int,
  val owner: Int,
  val param1: Int,
  val param2: Int
) {
  val isBarracks: Boolean
    get() = structureType == 2

  val isEmpty: Boolean
    get() = structureType == -1

  val isFriendly: Boolean
    get() = owner == 0

  val isEnemy: Boolean
    get() = owner == 1
}

data class Soldier(
  val x: Int,
  val y: Int,
  val owner: Int,
  val unitType: Int,
  val health: Int
) {
  val isQueen: Boolean
    get() = unitType == -1

  val isKnight: Boolean
    get() = unitType == 0

  val isArcher: Boolean
    get() = unitType == 1
}

sealed class Action(val command: String) {
  object Wait : Action("WAIT")
  data class Move(val position: Position) : Action("MOVE ${position.x} ${position.y}")
  data class BuildKnightBarracks(val siteId: Int) : Action("BUILD $siteId BARRACKS-KNIGHT")
  data class BuildArcherBarracks(val siteId: Int) : Action("BUILD $siteId BARRACKS-ARCHER")
}

data class Position(val x: Int, val y: Int)

sealed class TrainSoldiers(val command: String) {
  object None : TrainSoldiers("TRAIN")
  class AtLocation(locations: List<Int>) :
    TrainSoldiers("TRAIN " + locations.joinToString(" ") { it.toString() })
}

fun playTurn(action: Action, train: TrainSoldiers): List<String> {
  val out = listOf(action.command, train.command)

  out.forEach { println(it) }

  // for tests
  return out
}

fun main(args: Array<String>) {
  val input = Scanner(System.`in`)
  val numSites = input.nextInt()

  val sites = (0 until numSites).map {
    val siteId = input.nextInt()
    val x = input.nextInt()
    val y = input.nextInt()
    val radius = input.nextInt()

    BuildingGround(siteId, x, y, radius)
  }

  val turns = mutableListOf<Battlefield>()

  // game loop
  while (true) {
    val gold = input.nextInt()
    val touchedSite = input.nextInt() // -1 if none

    val sites = (0 until numSites).map {
      val siteId = input.nextInt()
      val ignore1 = input.nextInt() // used in future leagues
      val ignore2 = input.nextInt() // used in future leagues
      val structureType = input.nextInt() // -1 = No structure, 2 = Barracks
      val owner = input.nextInt() // -1 = No structure, 0 = Friendly, 1 = Enemy
      val param1 = input.nextInt()
      val param2 = input.nextInt()

      Site(siteId, ignore1, ignore2, structureType, owner, param1, param2)
    }

    val numUnits = input.nextInt()
    val units = (0 until numUnits).map {
      val x = input.nextInt()
      val y = input.nextInt()
      val owner = input.nextInt()
      val unitType = input.nextInt() // -1 = QUEEN, 0 = KNIGHT, 1 = ARCHER
      val health = input.nextInt()

      Soldier(x, y, owner, unitType, health)
    }


    val battlefield = Battlefield(gold, TouchedSite(touchedSite), sites, units)

    // remember past states
    turns.add(battlefield)

    println("WAIT")
    println("TRAIN")
  }
}
