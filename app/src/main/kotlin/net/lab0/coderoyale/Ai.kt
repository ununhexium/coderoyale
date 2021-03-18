import java.util.*
import kotlin.math.sqrt

/**
 * Auto-generated code below aims at helping you parse
 * the standard input according to the problem statement.
 */

fun debug(any: Any?) {
  System.err.println(any.toString())
}

// constants

// sites

const val EMPTY_STRUCTURE_TYPE = -1
const val BARRACKS_STRUCTURE_TYPE = 2

const val NO_OWNER = -1
const val ENEMY_OWNER = 1

// units

const val FRIENDLY_OWNER = 0

const val QUEEN_TYPE = -1
const val KNIGHT_TYPE = 0
const val ARCHER_TYPE = 1

const val KNIGHT_COST = 80
const val ARCHER_COST = 100

inline class TouchedSite(val siteId: Int) {
  val touches: Boolean
    get() = siteId != -1
}

data class MapSite(val siteId: Int, val position: Position, val radius: Int)

data class Battlefield(
  val mapSites: List<MapSite>,
  val gold: Int,
  val touchedSite: TouchedSite,
  val sites: List<Site>,
  val units: List<Soldier>
) {
  // gold

  val canBuildKnight: Boolean
    get() = gold >= KNIGHT_COST

  val canBuildArcher: Boolean
    get() = gold >= ARCHER_COST


  // sites

  val mySites: List<Site>
    get() = sites.filter { it.isFriendly }

  val iHaveBarracks: Boolean
    get() = myBarracks.isNotEmpty()

  val myBarracks: List<Site>
    get() = mySites.filter { it.isBarracks }

  val touchedSiteAsSite: Site
    get() = sites.first { it.siteId == touchedSite.siteId }

  /**
   * Sites that noone owns. Lazy
   */
  val emptySites: List<MapSite> by lazy {
    val emptySites = sites.filter { it.isEmpty }.map { it.siteId }
    mapSites.filter {
      it.siteId in emptySites
    }
  }

  // units

  val queen: Soldier
    get() = units.first { it.isQueen }
}

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
    get() = structureType == BARRACKS_STRUCTURE_TYPE

  val isEmpty: Boolean
    get() {
      return structureType == EMPTY_STRUCTURE_TYPE
    }

  val isFriendly: Boolean
    get() = owner == FRIENDLY_OWNER

  val isEnemy: Boolean
    get() = owner == ENEMY_OWNER

  val isNotOwned: Boolean
    get() = owner == NO_OWNER
}

data class Soldier(
  val position: Position,
  val owner: Int,
  val unitType: Int,
  val health: Int
) {
  val isQueen: Boolean
    get() = unitType == QUEEN_TYPE

  val isKnight: Boolean
    get() = unitType == KNIGHT_TYPE

  val isArcher: Boolean
    get() = unitType == ARCHER_TYPE
}

sealed class Action(val command: String) {
  object Wait : Action("WAIT")
  data class Move(val position: Position) : Action("MOVE ${position.x} ${position.y}") {
    constructor(x: Int, y: Int) : this(Position(x, y))
  }

  data class BuildKnightBarracks(val siteId: Int) :
    Action("BUILD $siteId BARRACKS-KNIGHT")

  data class BuildArcherBarracks(val siteId: Int) : Action("BUILD $siteId BARRACKS-ARCHER")
}

data class Position(val x: Int, val y: Int) {
  // euclidean distance, ignore obstacles
  fun distanceTo(other: Position): Double {
    val xDiff = other.x - this.x
    val yDiff = other.y - this.y
    val xSqr = xDiff * xDiff
    val ySqr = yDiff * yDiff

    // not float as it's double internally
    // and x86 uses 80 bits floating point anyway
    return sqrt((xSqr + ySqr).toDouble())
  }
}

sealed class Training(val command: String) {
  object None : Training("TRAIN")
  class AtLocation(locationIds: List<Int>) :
    Training("TRAIN " + locationIds.joinToString(" ") { it.toString() }) {
    constructor(vararg locationIds: Int) : this(locationIds.toList())
  }
}

fun playTurn(action: Action, train: Training): List<String> {
  val out = listOf(action.command, train.command)

  out.forEach { println(it) }

  // for tests
  return out
}

fun main(args: Array<String>) {
  val input = Scanner(System.`in`)
  val numSites = input.nextInt()

  val existingSites = (0 until numSites).map {
    val siteId = input.nextInt()
    val x = input.nextInt()
    val y = input.nextInt()
    val radius = input.nextInt()

    MapSite(siteId, Position(x, y), radius)
  }

  val turns = mutableListOf<Battlefield>()

  // game loop
  while (true) {
    val gold = input.nextInt()
    val touchedSite = input.nextInt() // -1 if none

    val sites = parseSites(numSites, input)
    val numUnits = input.nextInt()
    val units = parseUnits(numUnits, input)

    val battlefield = Battlefield(existingSites, gold, TouchedSite(touchedSite), sites, units)
    // remember past states
    turns.add(battlefield)

    val commands = thinkVeryHard(turns, battlefield)

    playTurn(commands.first, commands.second)
  }
}

fun thinkVeryHard(
  turns: MutableList<Battlefield>,
  battlefield: Battlefield
): Pair<Action, Training> {
  val queen = battlefield.queen

  // take empty site if next to it
  val action = if (battlefield.touchedSite.touches && battlefield.touchedSiteAsSite.isEmpty) {
    Action.BuildKnightBarracks(battlefield.touchedSite.siteId)
  } else {
    // get the nearest unowned site
    // TODO: travelling salesman problem?
    val nearestSite = battlefield.emptySites.minBy {
      it.position.distanceTo(queen.position)
    }?.position

    debug("Nearest empty site $nearestSite")

    if (nearestSite != null) {
      Action.Move(nearestSite)
    } else {
      // return to starting location for safety
      Action.Move(turns.first().queen.position)
    }
  }

  val build = if (battlefield.canBuildKnight && battlefield.iHaveBarracks) {
    Training.AtLocation(battlefield.myBarracks.first().siteId)
  } else {
    Training.None
  }

  return action to build
}

fun parseSites(numSites: Int, input: Scanner): List<Site> {
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
  return sites
}

private fun parseUnits(
  numUnits: Int,
  input: Scanner
): List<Soldier> {
  val units = (0 until numUnits).map {
    val x = input.nextInt()
    val y = input.nextInt()
    val owner = input.nextInt()
    val unitType = input.nextInt() // -1 = QUEEN, 0 = KNIGHT, 1 = ARCHER
    val health = input.nextInt()

    Soldier(Position(x, y), owner, unitType, health)
  }
  return units
}
