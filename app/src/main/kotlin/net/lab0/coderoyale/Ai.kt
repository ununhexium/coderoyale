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

// mining

const val MAX_GOLD_MINE_RATE = 5

// sites

const val EMPTY_STRUCTURE_TYPE = -1
const val GOLD_MINE_TYPE = 0
const val TOWER_TYPE = 1
const val BARRACKS_TYPE = 2

// sites and units

const val NO_OWNER = -1
const val FRIENDLY_OWNER = 0
const val ENEMY_OWNER = 1

// units

const val QUEEN_TYPE = -1
const val KNIGHT_TYPE = 0
const val ARCHER_TYPE = 1
const val GIANT_TYPE = 2

const val KNIGHT_COST = 80
const val ARCHER_COST = 100
const val GIANT_COST = 140

const val KNIGHT_GROUP_SIZE = 4
const val ARCHER_GROUP_SIZE = 2
const val GIANT_GROUP_SIZE = 1

const val QUEEN_RADIUS = 30
const val QUEEN_SPEED = 60
const val QUEEN_START_HP = 100

const val CREEP_DECAY_RATE = 1

inline class TouchedSite(val siteId: Int) {
  val touches: Boolean
    get() = siteId != -1
}

data class MapSite(val siteId: Int, val position: Position, val radius: Int)

// TODO param 1 and 2: is that info available for my units or also for enemy units?
sealed class Site(
  val siteId: Int,
  val mapSite: MapSite,
  val gold: Int,
  val maxMineSize: Int,
  val structureType: Int,
  val owner: Int,
  val param1: Int,
  val param2: Int
) {

  // structure type
  val isEmpty: Boolean = structureType == EMPTY_STRUCTURE_TYPE
  val isGoldMine: Boolean = structureType == GOLD_MINE_TYPE
  val isTower: Boolean = structureType == TOWER_TYPE
  val isBarracks: Boolean = structureType == BARRACKS_TYPE

  // owner
  val isFriendly: Boolean = owner == FRIENDLY_OWNER
  val isEnemy: Boolean = owner == ENEMY_OWNER
  val isNotOwned: Boolean = owner == NO_OWNER

  override fun equals(other: Any?): Boolean =
    other is Site && this.siteId == other.siteId

  override fun hashCode() = siteId

  class Empty(
    siteId: Int,
    mapSite: MapSite,
    gold: Int,
    maxMineSize: Int,
    structureType: Int,
    owner: Int,
    param1: Int,
    param2: Int
  ) : Site(siteId, mapSite, gold, maxMineSize, structureType, owner, param1, param2)

  class GoldMine(
    siteId: Int,
    mapSite: MapSite,
    gold: Int,
    maxMineSize: Int,
    structureType: Int,
    owner: Int,
    param1: Int,
    param2: Int
  ) : Site(siteId, mapSite, gold, maxMineSize, structureType, owner, param1, param2) {
    /**
     * the income rate ranging from 1 to 5 (or -1 if enemy)
     */
    val remainingGold = param1
  }

  class Tower(
    siteId: Int,
    mapSite: MapSite,
    gold: Int,
    maxMineSize: Int,
    structureType: Int,
    owner: Int,
    param1: Int,
    param2: Int
  ) : Site(siteId, mapSite, gold, maxMineSize, structureType, owner, param1, param2) {
    val remainingHP = param1

    /**
     * the attack radius measured from its center
     */
    val attackRadius = param2
  }

  class Barracks(
    siteId: Int,
    mapSite: MapSite,
    gold: Int,
    maxMineSize: Int,
    structureType: Int,
    owner: Int,
    param1: Int,
    param2: Int
  ) : Site(siteId, mapSite, gold, maxMineSize, structureType, owner, param1, param2) {
    /**
     * the number of turns before a new set of creeps can be trained
     * (if 0, then training may be started this turn)
     */
    val turnsUntilFree = param1
    val canTrainNow = turnsUntilFree == 0

    /**
     * the creep type: 0 for KNIGHT, 1 for ARCHER, 2 for GIANT
     */
    val creepType = param2
  }
}

data class ParsedSites(
  val goldMines: List<Site.GoldMine>,
  val towers: List<Site.Tower>,
  val stables: List<Site.Barracks>,
  val archeries: List<Site.Barracks>,
  val phlegra: List<Site.Barracks>
) {
  // aggregates
  val barracks = stables + archeries + phlegra
  val allOwned: List<Site> = goldMines + towers + barracks
}

data class Sites(
  val emptySites: List<Site.Empty>,

  val friendly: ParsedSites,
  val enemy: ParsedSites
) {
  val all = friendly.allOwned + enemy.allOwned
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

  data class BuildTower(val siteId: Int) : Action("BUILD $siteId TOWER")

  data class BuildMine(val siteId: Int) : Action("BUILD $siteId MINE")
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

enum class BarrackType {
  STABLE,
  ARCHERY
}

class Memory(
  // Map<SiteId, BarracksType>
  private val currentBarracks: MutableMap<Int, BarrackType> = mutableMapOf()
) {

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

data class Battlefield(
  val memory: Memory,
  val mapSites: List<MapSite>,
  val gold: Int,
  val touchedSite: TouchedSite,
  val sites: Sites,
  val units: List<Soldier>
) {

  // gold
  val canBuildKnight: Boolean = gold >= KNIGHT_COST
  val canBuildArcher: Boolean = gold >= ARCHER_COST
  val canBuildGiant: Boolean = gold >= GIANT_COST

  // sites
  val touchedSiteAsSite: Site
    get() = sites.all.first { it.siteId == touchedSite.siteId }

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

fun playTurn(action: Action, train: Training): List<String> {
  val out = listOf(action.command, train.command)

  out.forEach { println(it) }

  // for tests
  return out
}

fun parseSites(mapSites: List<MapSite>, numSites: Int, input: Scanner): Sites {

  // pre sort the sites so it's done once for all the strategies (no recompute on each get())
  // avoid time loss on functional style .filter{}.map{} if done later
  // no time problem yet, just anticipating based on previously played games
  // it makes this block repetitive but I thing it's worth it

  val emptySites = mutableListOf<Site.Empty>()

  data class ParsingSites(
    val goldMines: MutableList<Site.GoldMine> = mutableListOf(),
    val towers: MutableList<Site.Tower> = mutableListOf(),
    val stables: MutableList<Site.Barracks> = mutableListOf(),
    val archeries: MutableList<Site.Barracks> = mutableListOf(),
    val phlegra: MutableList<Site.Barracks> = mutableListOf()
  )

  val ownedSites = listOf(ParsingSites(), ParsingSites()) // 0: friendly 1:enemy

  (0 until numSites).forEach {
    val siteId = input.nextInt()
    val gold = input.nextInt()
    val maxMineSize = input.nextInt()
    val structureType = input.nextInt()
    val owner = input.nextInt()

    /**
     * When no structure: -1
     * When goldmine: the income rate ranging from 1 to 5 (or -1 if enemy)
     * When tower: the remaining HP
     * When barracks, the number of turns before a new set of creeps can be trained (if 0, then training may be started this turn)
     */
    val param1 = input.nextInt()

    val param2 = input.nextInt()

    when (structureType) {

      EMPTY_STRUCTURE_TYPE -> {
        val empty = Site.Empty(
          siteId,
          mapSites.first { it.siteId == siteId },
          gold,
          maxMineSize,
          structureType,
          owner,
          param1,
          param2
        )

        emptySites.add(empty)
      }

      GOLD_MINE_TYPE -> {
        val mine = Site.GoldMine(
          siteId,
          mapSites.first { it.siteId == siteId },
          gold,
          maxMineSize,
          structureType,
          owner,
          param1,
          param2
        )

        ownedSites[owner].goldMines.add(mine)
      }

      TOWER_TYPE -> {
        val tower = Site.Tower(
          siteId,
          mapSites.first { it.siteId == siteId },
          gold,
          maxMineSize,
          structureType,
          owner,
          param1,
          param2
        )

        ownedSites[owner].towers.add(tower)
      }

      BARRACKS_TYPE -> {
        val barracks = Site.Barracks(
          siteId,
          mapSites.first { it.siteId == siteId },
          gold,
          maxMineSize,
          structureType,
          owner,
          param1,
          param2
        )

        when (param2) {
          KNIGHT_TYPE -> {
            ownedSites[owner].stables.add(barracks)
          }

          ARCHER_TYPE -> {
            ownedSites[owner].archeries.add(barracks)
          }

          GIANT_TYPE -> {
            ownedSites[owner].phlegra.add(barracks)
          }
        }
      }
      else -> throw IllegalStateException("What structure type is $structureType?")
    }
  }

  return Sites(
    emptySites,
    ParsedSites(
      ownedSites[0].goldMines,
      ownedSites[0].towers,
      ownedSites[0].stables,
      ownedSites[0].archeries,
      ownedSites[0].phlegra
    ),
    ParsedSites(
      ownedSites[1].goldMines,
      ownedSites[1].towers,
      ownedSites[1].stables,
      ownedSites[1].archeries,
      ownedSites[1].phlegra
    )
  )
}

private fun parseUnits(
  numUnits: Int,
  input: Scanner
): List<Soldier> {
  return (0 until numUnits).map {
    val x = input.nextInt()
    val y = input.nextInt()
    val owner = input.nextInt()
    val unitType = input.nextInt()
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
    val touchedSite = input.nextInt()

    val sites = parseSites(mapSites, numSites, input)
    val numUnits = input.nextInt()
    val units = parseUnits(numUnits, input)

    // FIXME: ugly: memory is mutable and global to all turns
    val battlefield = Battlefield(memory, mapSites, gold, TouchedSite(touchedSite), sites, units)
    // remember past states
    turns.add(battlefield)

    val commands = thinkVeryHard(turns, battlefield)

    playTurn(commands.first, commands.second)
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
      if (battlefield.sites.friendly.barracks.count() % 2 == 0) {
        battlefield.memory.setStable(battlefield.touchedSite.siteId)
        Action.BuildStable(battlefield.touchedSite.siteId)
      } else {
        battlefield.memory.setArchery(battlefield.touchedSite.siteId)
        Action.BuildArchery(battlefield.touchedSite.siteId)
      }
    } else {
      // get the nearest unowned site
      // TODO: travelling salesman problem?
      val nearestSite = battlefield.sites.emptySites.minBy {
        it.mapSite.position.distanceTo(queen.position)
      }?.mapSite?.position

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
    val barracksClosestToEnemyQueen = battlefield.sites.friendly.stables.minBy {
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
    val barracksClosestToFriendlyQueen = battlefield.sites.friendly.archeries.minBy {
      it.mapSite.position.distanceTo(battlefield.friendlyQueen.position)
    }?.siteId

    return if (barracksClosestToFriendlyQueen != null) {
      Training.AtLocation(barracksClosestToFriendlyQueen)
    } else {
      Training.None
    }
  }
}

class BalancedTrainingStrategy(val maxKnightToArcherRatio: Float) : TrainingStrategy {
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
