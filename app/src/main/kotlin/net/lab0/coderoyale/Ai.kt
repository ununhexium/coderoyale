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
  val memory: Memory,
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

  val friendlySites: List<Site>
    get() = sites.filter { it.isFriendly }

  val enemySites: List<Site>
    get() = sites.filter { it.isEnemy }

  val friendlyBarracks: List<Site>
    get() = friendlySites.filter { it.isBarracks }

  val friendlyStables: List<Site>
    get() = friendlyBarracks.filter { memory.isStable(it.siteId) }

  val friendlyArcheries: List<Site>
    get() = friendlyBarracks.filter { memory.isArchery(it.siteId) }

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

  val friendlyArmy: List<Soldier> by lazy {
    units.filter { it.isFriendly }
  }

  val enemyArmy: List<Soldier> by lazy {
    units.filter { it.isEnemy }
  }

  val friendlyQueen: Soldier by lazy {
    friendlyArmy.first { it.isQueen }
  }

  val enemyQueen: Soldier by lazy {
    enemyArmy.first { it.isQueen }
  }

  val friendlyKnights: List<Soldier> by lazy {
    friendlyArmy.filter { it.isKnight }
  }

  val friendlyArchers: List<Soldier> by lazy {
    friendlyArmy.filter { it.isArcher }
  }
}

data class Site(
  val siteId: Int,
  val mapSite: MapSite,
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

  val isFriendly: Boolean
    get() = owner == FRIENDLY_OWNER

  val isEnemy: Boolean
    get() = owner == FRIENDLY_OWNER
}

sealed class Action(val command: String) {
  object Wait : Action("WAIT")
  data class Move(val position: Position) : Action("MOVE ${position.x} ${position.y}") {
    constructor(x: Int, y: Int) : this(Position(x, y))
  }

  data class BuildStable(val siteId: Int) :
    Action("BUILD $siteId BARRACKS-KNIGHT")

  data class BuildArchery(val siteId: Int) : Action("BUILD $siteId BARRACKS-ARCHER")
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
    constructor(
      locationId: Int,
      vararg moreLocationIds: Int
    ) : this(listOf(locationId) + moreLocationIds.toList())
  }
}

class Memory(
  // Map<SiteId, BarracksType>
  private val currentBarracks: MutableMap<Int, BarrackType> = mutableMapOf()
) {
  enum class BarrackType {
    STABLE,
    ARCHERY
  }

  /**
   * Remember a site as a stable
   */
  fun setStable(siteId: Int) {
    currentBarracks[siteId] = BarrackType.STABLE
  }

  fun isStable(siteId: Int): Boolean {
    return currentBarracks[siteId] == BarrackType.STABLE
  }

  /**
   * Remember a site as an archery
   */
  fun setArchery(siteId: Int) {
    currentBarracks[siteId] = BarrackType.ARCHERY
  }

  fun isArchery(siteId: Int): Boolean {
    return currentBarracks[siteId] == BarrackType.ARCHERY
  }

  /**
   * Deletes a site from the memory list
   */
  fun remove(siteId: Int) {
    currentBarracks.remove(siteId)
  }
}

fun playTurn(action: Action, train: Training): List<String> {
  val out = listOf(action.command, train.command)

  out.forEach { println(it) }

  // for tests
  return out
}

fun parseSites(mapSites: List<MapSite>, numSites: Int, input: Scanner): List<Site> {
  return (0 until numSites).map {
    val siteId = input.nextInt()
    val ignore1 = input.nextInt() // used in future leagues
    val ignore2 = input.nextInt() // used in future leagues
    val structureType = input.nextInt() // -1 = No structure, 2 = Barracks
    val owner = input.nextInt() // -1 = No structure, 0 = Friendly, 1 = Enemy
    val param1 = input.nextInt()
    val param2 = input.nextInt()

    Site(
      siteId,
      mapSites.first { it.siteId == siteId },
      ignore1,
      ignore2,
      structureType,
      owner,
      param1,
      param2
    )
  }
}

private fun parseUnits(
  numUnits: Int,
  input: Scanner
): List<Soldier> {
  return (0 until numUnits).map {
    val x = input.nextInt()
    val y = input.nextInt()
    val owner = input.nextInt()
    val unitType = input.nextInt() // -1 = QUEEN, 0 = KNIGHT, 1 = ARCHER
    val health = input.nextInt()

    Soldier(Position(x, y), owner, unitType, health)
  }
}

fun main(args: Array<String>) {
  val input = Scanner(System.`in`)
  val numSites = input.nextInt()

  val mapSites = (0 until numSites).map {
    val siteId = input.nextInt()
    val x = input.nextInt()
    val y = input.nextInt()
    val radius = input.nextInt()

    MapSite(siteId, Position(x, y), radius)
  }

  val turns = mutableListOf<Battlefield>()
  val memory = Memory()

  // game loop
  while (true) {
    val gold = input.nextInt()
    val touchedSite = input.nextInt() // -1 if none

    val sites = parseSites(mapSites, numSites, input)
    val numUnits = input.nextInt()
    val units = parseUnits(numUnits, input)

    // FIXME: ugly: memory is mutable and global to all turns
    val battlefield = Battlefield(memory, mapSites, gold, TouchedSite(touchedSite), sites, units)
    // remember past states
    turns.add(battlefield)

    postParse(battlefield)

    val commands = thinkVeryHard(turns, battlefield)

    playTurn(commands.first, commands.second)
  }
}

fun postParse(battlefield: Battlefield) {
  // refresh the site with potential enemy changes -> remove them from the known barracks
  battlefield.enemySites.forEach {
    battlefield.memory.remove(it.siteId)
  }
}


interface QueenStrategy {
  fun getAction(
    turns: MutableList<Battlefield>,
    battlefield: Battlefield
  ): Action
}

interface TrainingStrategy {
  fun getTraining(
    turns: MutableList<Battlefield>,
    battlefield: Battlefield
  ): Training
}

interface Strategy {
  val queen: QueenStrategy
  val training: TrainingStrategy

  fun result(
    turns: MutableList<Battlefield>,
    battlefield: Battlefield
  ): Pair<Action, Training>
}

class StrategyComposer(
  override val queen: QueenStrategy,
  override val training: TrainingStrategy


) : Strategy {
  override fun result(
    turns: MutableList<Battlefield>,
    battlefield: Battlefield
  ) =
    queen.getAction(turns, battlefield) to training.getTraining(turns, battlefield)
}

// QUEEN STRATEGIES

object TakeNextEmptySite : QueenStrategy {
  override fun getAction(
    turns: MutableList<Battlefield>,
    battlefield: Battlefield
  ): Action {
    val queen = battlefield.friendlyQueen

    // take empty site if next to it
    return if (battlefield.touchedSite.touches && battlefield.touchedSiteAsSite.isEmpty) {
      if (battlefield.friendlyBarracks.count() % 2 == 0) {
        battlefield.memory.setStable(battlefield.touchedSite.siteId)
        Action.BuildStable(battlefield.touchedSite.siteId)
      } else {
        battlefield.memory.setArchery(battlefield.touchedSite.siteId)
        Action.BuildArchery(battlefield.touchedSite.siteId)
      }
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
        Action.Move(turns.first().friendlyQueen.position)
      }
    }
  }
}

/**
 * Put the queen in a safe-ish place
 */
object Fallback : QueenStrategy {
  override fun getAction(
    turns: MutableList<Battlefield>,
    battlefield: Battlefield
  ): Action {
    return Action.Move(turns.first().friendlyQueen.position)
  }
}

object TakeThenFallback : QueenStrategy {
  override fun getAction(turns: MutableList<Battlefield>, battlefield: Battlefield): Action {
    return if (battlefield.friendlyQueen.health < turns.first().friendlyQueen.health / 2) {
      Fallback.getAction(turns, battlefield)
    } else {
      TakeNextEmptySite.getAction(turns, battlefield)
    }
  }
}

// BUILDING STRATEGIES

object BuildKnights : TrainingStrategy {
  override fun getTraining(turns: MutableList<Battlefield>, battlefield: Battlefield): Training {
    // try to find barracks with that type of unit
    val barracksClosestToEnemyQueen = battlefield.friendlyStables.minBy {
      it.mapSite.position.distanceTo(battlefield.enemyQueen.position)
    }?.siteId

    return if (barracksClosestToEnemyQueen != null) {
      Training.AtLocation(barracksClosestToEnemyQueen)
    } else {
      Training.None
    }
  }
}

object BuildArchers : TrainingStrategy {
  override fun getTraining(turns: MutableList<Battlefield>, battlefield: Battlefield): Training {
    // try to find barracks with that type of unit
    val barracksClosestToFriendlyQueen = battlefield.friendlyArcheries.minBy {
      it.mapSite.position.distanceTo(battlefield.friendlyQueen.position)
    }?.siteId

    return if (barracksClosestToFriendlyQueen != null) {
      Training.AtLocation(barracksClosestToFriendlyQueen)
    } else {
      Training.None
    }
  }
}

class BalancedTrainingStrategy(val maxKnightToArcherRatio:Float) : TrainingStrategy {
  override fun getTraining(
    turns: MutableList<Battlefield>,
    battlefield: Battlefield
  ): Training {
    // try to balance archers and knights when possible
    val knightCount = battlefield.friendlyKnights.size
    val archerCount = battlefield.friendlyArchers.size
    debug("Barracks ${battlefield.memory}")
    val ratio = knightCount / archerCount.toDouble()
    return if (ratio < maxKnightToArcherRatio) {
      debug("Build knight knights=$knightCount, archers=$archerCount")
      BuildKnights.getTraining(turns, battlefield)
    } else {
      debug("Build archer knights=$knightCount, archers=$archerCount")
      BuildArchers.getTraining(turns, battlefield)
    }
  }
}

val strategy = StrategyComposer(
  TakeThenFallback,
  BalancedTrainingStrategy(3f)
)

fun thinkVeryHard(
  turns: MutableList<Battlefield>,
  battlefield: Battlefield
): Pair<Action, Training> {
  return strategy.result(turns, battlefield)
}
