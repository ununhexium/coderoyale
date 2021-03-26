import Output.Intent
import Output.Intent.BuildArchery
import Output.Intent.BuildMine
import Output.Intent.BuildStable
import Output.Intent.BuildTower
import Output.Intent.Move
import Output.Intent.Wait
import Output.TrainingAction
import Output.TrainingAction.NoTraining
import Site.Tower
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The upper part of the code is just declarations and boring parsing,
 *
 * strategies start at the STRATEGIES comment
 */

/**

Ignored rules

If both Queens try simultaneously to build a structure on the same building site, then only one will be built:

On even-numbered turns (this includes the first turn: turn 0), player 2 gets to build
On odd-numbered turns, player 1 gets to build

 */

fun debug(any: Any?) {
  System.err.println(any.toString())
}

///////////////
// CONSTANTS //
///////////////

object C {

  object Battlefield {
    const val MIN_WIDTH = 0
    const val MAX_WIDTH = 1920
    const val MIN_HEIGHT = 0
    const val MAX_HEIGHT = 1000
  }


  // sites

  object Sites {
    const val MIN_RADIUS = 60
    const val MAX_RADIUS = 110

    /**
     * Sites whose center is located within 500 units of the map's center are given
     * a bonus of 50 gold and +1 to maximum mining rate.
     *
     * Sites whose center is located within 200 units of the map's center are given
     * a further bonus of 50 gold and +1 to maximum mining rate.
     */
    const val FIRST_BONUS_RADIUS = 500
    const val SECOND_BONUS_RADIUS = 200


    const val EMPTY_STRUCTURE_TYPE = -1

    object Mine {
      const val TYPE = 0

      const val MIN_GOLD_MINE_RATE = 1
      const val MAX_GOLD_MINE_RATE = 5 // 3 +1 +1 (2 bonuses for closer to the center)
    }

    object Tower {
      const val TYPE = 1

      /**
       * Each tower has a number of HP (param1) that determines its effective attack radius (param2).
       * Each HP allows the tower to cover 1000 square-units (a square unit is not very much!),
       * not including the area of the site on which it sits.
       */
      const val AREA_PER_HP = 1000

      /**
       * When an existing tower is grown, it receives 100 additional HP.
       */
      const val HP_PER_GROW = 100

      const val MAX_HP = 800

      /**
       * A Queen can slightly outpace a single GIANT by building at the same time. (+100 VS -80)
       */
      const val TOWER_HP_DECAY = 4
    }

    object Barracks {
      const val TYPE = 2
    }
  }

  // sites and units

  const val NO_OWNER = -1
  const val FRIENDLY_OWNER = 0
  const val ENEMY_OWNER = 1

  // units

  object U {

    const val CREEP_DECAY_RATE = 1

    object QueenData : UnitData {
      override val type = -1
      override val speed = 60

      /**
       * Doc says 200, game says 90 🤷
       */
      override val hp = 90
      override val radius = 30
    }

    object KnightData : BuildableUnitData {
      override val cost = 80
      override val number = 4
      override val range = 0
      override val trainingTime = 5

      override val type = 0
      override val speed = 100
      override val hp = 25
      override val radius = 20

      val queenDamage = 1
    }

    object ArcherData : BuildableUnitData {
      override val cost = 100
      override val number = 2
      override val range = 200
      override val trainingTime = 8

      override val type = 1
      override val speed = 75
      override val hp = 45
      override val radius = 25

      val giantDamage = 10
      val creepDamage = 2
    }

    object GiantData : BuildableUnitData {
      override val cost = 140
      override val number = 1
      override val range = 0
      override val trainingTime = 10

      override val type = 2
      override val speed = 50
      override val hp = 200
      override val radius = 40

      val towerDamage = 80
    }
  }
}

interface UnitData {
  val type: Int
  val speed: Int
  val hp: Int
  val radius: Int
}

interface BuildableUnitData : UnitData {
  val cost: Int

  /**
   * Produced amount for 1 batch of production
   */
  val number: Int
  val range: Int
  val trainingTime: Int
}

/////////////////////
// Game Structures //
/////////////////////

inline class TouchedSite(val siteId: SiteId) {
  val touches: Boolean
    get() = siteId.value != -1
}

inline class SiteId(val value: Int)

class MapSite(val id: SiteId, val position: PositionImpl, val radius: Int) : Position by position

// TODO param 1 and 2: is that info available for my units or also for enemy units?
sealed class Site(
  val site: MapSite,
  val gold: Int,
  val maxIncomeRateSize: Int,
  val structureType: Int,
  val owner: Int,
  val param1: Int,
  val param2: Int
) : Position by site.position {

  // structure type
  val isEmpty: Boolean = structureType == C.Sites.EMPTY_STRUCTURE_TYPE
  val isGoldMine: Boolean = structureType == C.Sites.Mine.TYPE
  val isTower: Boolean = structureType == C.Sites.Tower.TYPE
  val isBarracks: Boolean = structureType == C.Sites.Barracks.TYPE

  // owner
  val isFriendly: Boolean = owner == C.FRIENDLY_OWNER
  val isEnemy: Boolean = owner == C.ENEMY_OWNER
  val isNotOwned: Boolean = owner == C.NO_OWNER

  class Empty(
    mapSite: MapSite,
    gold: Int,
    maxMineSize: Int,
    structureType: Int,
    owner: Int,
    param1: Int,
    param2: Int
  ) :
    Site(mapSite, gold, maxMineSize, structureType, owner, param1, param2)

  class GoldMine(
    mapSite: MapSite,
    gold: Int,
    maxMineSize: Int,
    structureType: Int,
    owner: Int,
    param1: Int,
    param2: Int
  ) : Site(mapSite, gold, maxMineSize, structureType, owner, param1, param2) {
    /**
     * the income rate ranging from 1 to 5 (or -1 if enemy)
     */
    val incomeRate = param1
  }

  class Tower(
    mapSite: MapSite,
    gold: Int,
    maxMineSize: Int,
    structureType: Int,
    owner: Int,
    param1: Int,
    param2: Int
  ) : Site(mapSite, gold, maxMineSize, structureType, owner, param1, param2) {
    val remainingHP = param1

    /**
     * the attack radius measured from its center
     *
     * The attack radius is calculated every turn according to the tower's HP
     * according to the formula: attackRadius = sqrt((hp * 1000 + siteArea) / PI) --
     * this is measured from the site's center.
     *
     * This is so that PI * attackRadius^2 = hp * 1000 + siteArea.
     *
     * When a tower is first built, it receives 200 HP.
     * This corresponds to a variable attack range: longer attack range for a smaller radius site.
     */
    val attackRadius = param2
  }

  class Barracks(
    mapSite: MapSite,
    gold: Int,
    maxMineSize: Int,
    structureType: Int,
    owner: Int,
    param1: Int,
    param2: Int
  ) : Site(mapSite, gold, maxMineSize, structureType, owner, param1, param2) {
    /**
     * the number of turns before a new set of creeps can be trained
     * (if 0, then training may be started this turn)
     */
    val turnsUntilFree = param1
    val isAvailable = turnsUntilFree == 0
    val isBusy = turnsUntilFree != 0

    /**
     * the creep type: 0 for KNIGHT, 1 for ARCHER, 2 for GIANT
     */
    val creepType = param2
  }
}

data class PlayerSites(
  val goldMines: List<Site.GoldMine>,
  val towers: List<Tower>,
  val stables: List<Site.Barracks>,
  val archeries: List<Site.Barracks>,
  val phlegra: List<Site.Barracks>
) {
  constructor(parsing: SitesParsing) : this(
    parsing.goldMines,
    parsing.towers,
    parsing.stables,
    parsing.archeries,
    parsing.phlegra
  )

  // aggregates
  val barracks = stables + archeries + phlegra
  val allOwned = goldMines + towers + barracks

  fun isInTowerRange(position: Position) =
    towers.any { it.site.position.distanceTo(position) < it.attackRadius }

  fun isAcrossTowerRange(queen: Position, position: Position): Boolean =
    towers.any { it.site.position.distanceToLine(queen, position) < it.attackRadius }

  fun isNotAcrossTowerRange(queen: Position, position: Position): Boolean =
    !isAcrossTowerRange(queen, position)

  fun isNotInTowerRange(position: Position) = !isInTowerRange(position)
}

data class Sites(
  val emptySites: List<Site.Empty>,

  val friendly: PlayerSites,
  val enemy: PlayerSites
) {
  val all = friendly.allOwned + enemy.allOwned + emptySites

  /**
   * Get the empty site
   */
  fun nearestEmptySite(position: PositionImpl): Site.Empty? {
    return emptySites.minBy {
      it.site.position.distanceTo(position)
    }
  }
}

sealed class Soldier(
  val position: PositionImpl,
  val owner: Int,
  val unitType: Int,
  val health: Int
) : Position by position {

  class Queen(position: PositionImpl, owner: Int, unitType: Int, health: Int) :
    Soldier(position, owner, unitType, health)

  class Knight(position: PositionImpl, owner: Int, unitType: Int, health: Int) :
    Soldier(position, owner, unitType, health)

  class Archer(position: PositionImpl, owner: Int, unitType: Int, health: Int) :
    Soldier(position, owner, unitType, health)

  class Giant(position: PositionImpl, owner: Int, unitType: Int, health: Int) :
    Soldier(position, owner, unitType, health)

  val radius = when (unitType) {
    C.U.QueenData.type -> C.U.QueenData.radius
    C.U.KnightData.type -> C.U.KnightData.radius
    C.U.ArcherData.type -> C.U.ArcherData.radius
    C.U.GiantData.type -> C.U.GiantData.radius
    else -> throw  IllegalStateException("What is a unit with type $unitType?")
  }

  val speed = when (unitType) {
    C.U.QueenData.type -> C.U.QueenData.speed
    C.U.KnightData.type -> C.U.KnightData.speed
    C.U.ArcherData.type -> C.U.ArcherData.speed
    C.U.GiantData.type -> C.U.GiantData.speed
    else -> throw  IllegalStateException("What is a unit with type $unitType?")
  }

  fun isTouching(other: Soldier) =
    this.position.distanceTo(other.position) - this.radius - other.radius < 5
}

data class PlayerSoldiers(
  val queen: Soldier.Queen,
  val knights: List<Soldier.Knight>,
  val archers: List<Soldier.Archer>,
  val giants: List<Soldier.Giant>
) {
  constructor(
    parsing: SoldiersParsing
  ) : this(
    queen = parsing.queen,
    knights = parsing.knights,
    archers = parsing.archers,
    giants = parsing.giants
  )

  // aggregates
  val all: List<Soldier> = knights + archers + giants + queen
}

data class Soldiers(
  val friendly: PlayerSoldiers,
  val enemy: PlayerSoldiers
) {
  val myQueen: Soldier.Queen = friendly.queen
}

interface Position {
  val x: Int
  val y: Int

  fun distanceTo(other: Position): Double
  fun distanceToLine(p1: Position, p2: Position): Double
}

data class PositionImpl(override val x: Int, override val y: Int) : Position {
  companion object {
    val ORIGIN: Position = PositionImpl(0, 0)
  }

  // euclidean distance, ignore obstacles
  override fun distanceTo(other: Position): Double {
    val xDiff = other.x - this.x
    val yDiff = other.y - this.y
    val xSqr = xDiff * xDiff
    val ySqr = yDiff * yDiff

    // not float as it's double internally
    // and x86 uses 80 bits floating point anyway
    return sqrt((xSqr + ySqr).toDouble())
  }

  override fun distanceToLine(p1: Position, p2: Position): Double {
    val p0 = this
    val x21diff = (p2.x - p1.x).toDouble()
    val y21diff = (p2.y - p1.y).toDouble()

    return abs(
      x21diff * (p1.y - p0.y) - (p1.x - p0.x) * y21diff
    ) / sqrt(x21diff * x21diff + y21diff * y21diff)
  }
}


sealed class Output(val command: String) {

  sealed class Intent(command: String) : Output(command) {

    object Wait : Intent("WAIT")
    class Move(val position: PositionImpl) : Intent("MOVE ${position.x} ${position.y}") {
      constructor(x: Int, y: Int) : this(PositionImpl(x, y))
    }

    class BuildStable(siteId: SiteId) : Intent("BUILD ${siteId.value} BARRACKS-KNIGHT")
    class BuildArchery(siteId: SiteId) : Intent("BUILD ${siteId.value} BARRACKS-ARCHER")
    class BuildPhlegra(siteId: SiteId) : Intent("BUILD ${siteId.value} BARRACKS-GIANT")

    class BuildTower(siteId: SiteId) : Intent("BUILD ${siteId.value} TOWER")
    class BuildMine(siteId: SiteId) : Intent("BUILD ${siteId.value} MINE")
  }

  sealed class TrainingAction(command: String) : Output(command) {
    object NoTraining : TrainingAction("TRAIN")
    class AtLocation(locationIds: List<SiteId>) :
      TrainingAction("TRAIN " + locationIds.joinToString(" ") { it.value.toString() }) {
      constructor(locationId: SiteId) :
          this(listOf(locationId))
    }
  }
}

data class Battlefield(
  val gold: Int,
  val touchedSite: TouchedSite,
  val sites: Sites,
  val units: Soldiers
) {

  // gold
  val canBuildKnight: Boolean = gold >= C.U.KnightData.cost
  val canBuildArcher: Boolean = gold >= C.U.ArcherData.cost
  val canBuildGiant: Boolean = gold >= C.U.GiantData.cost

  /**
   * Friendly gold mines rate
   */
  val goldMinesRate: Int
    get() = sites.friendly.goldMines.sumBy { it.incomeRate }

  // sites
  val touchedSiteAsSite: Site
    get() = sites.all.first { it.site.id == touchedSite.siteId }

  // UTILS

  // closest to friendly queen's position
  val nearestEmpty
    get() = sites.nearestEmptySite(myQueen.position)

  // shortcut
  val myQueen
    get() = units.friendly.queen
}

////////////////
// Extensions //
////////////////


fun <E : Site> List<E>.nearestTo(target: Position) =
  this.minBy { it.site.position.distanceTo(target) }

fun List<MapSite>.nearestTo(target: Position) =
  this.minBy { it.position.distanceTo(target) }


////////////////
// STRATEGIES //
////////////////

typealias FunctionalStrategy<T> = (game: Game) -> T?
typealias FallbackStrategy2<T> = (game: Game) -> T

fun <S> tryInOrder(
  s1: FunctionalStrategy<S>,
  s2: FunctionalStrategy<S>
): FunctionalStrategy<S> = { s1(it) ?: s2(it) }

fun <S> fallbackWith(
  s1: FunctionalStrategy<S>,
  s2: FallbackStrategy2<S>
): FallbackStrategy2<S> = { s1(it) ?: s2(it) }

interface Strategy<S> where S : Any? {
  val name: String
  fun done(game: Game): Boolean = false
  fun play(game: Game): S
}

interface FallbackStrategy<S> : Strategy<S> where S : Any {
  override fun play(game: Game): S
}

typealias MetaStrategy = FallbackStrategy<GameStrategy>
typealias GameStrategy = Strategy<Decision>
typealias IntentStrategy = Strategy<Intent?>
typealias FallbackIntentStrategy = FallbackStrategy<Intent>
typealias TrainingStrategy = Strategy<TrainingAction?>
typealias FallbackTrainingStrategy = FallbackStrategy<TrainingAction>

class StrategySequence<S>(val first: Strategy<S>, val next: Strategy<S>) : Strategy<S> {
  override val name: String
    get() = "Sequence {${first.name} -> ${next.name}}"

  override fun done(game: Game) = first.done(game) && next.done(game)

  override fun play(game: Game) =
    if (!first.done(game)) first.play(game) else next.play(game)
}

class StrategyAlternative<S>(val first: Strategy<S>, val next: Strategy<S>) : Strategy<S> {
  override val name: String
    get() = "{${first.name} else {${next.name}}"

  override fun done(game: Game) = first.done(game) || next.done(game)

  override fun play(game: Game): S {
    return if (!first.done(game)) {
      val firstStrategy = first.play(game)
      return if (firstStrategy != null) {
        firstStrategy
      } else {
        debug("${first.name} failed. Fallback to ${next.name}")
        return next.play(game)
      }
    } else next.play(game)
  }
}

class StrategyFallback<S>(val first: Strategy<S?>, val next: FallbackStrategy<S>) :
  FallbackStrategy<S> where S : Any {
  override val name: String
    get() = "${first.name} fallback with {${next.name}}"

  override fun done(game: Game) = first.done(game)

  override fun play(game: Game): S {
    return if (!first.done(game)) {
      val firstStrategy = first.play(game)
      return if (firstStrategy != null) {
        firstStrategy
      } else {
        debug("${first.name} failed. Fallback to ${next.name}")
        return next.play(game)
      }
    } else next.play(game)
  }
}

fun <S> Strategy<S>.then(next: Strategy<S>): Strategy<S> =
  StrategySequence(this, next)

fun <S> Strategy<S>.orElse(next: Strategy<S>): Strategy<S> =
  StrategyAlternative(this, next)

fun <S> skip(): Strategy<S> where S : Any? {
  return object : Strategy<S> {
    override val name: String
      get() = "Skip"

    override fun done(game: Game) = true
    override fun play(game: Game) =
      throw IllegalStateException("Can't be called. This is the skip strategy")
  }
}

fun <S> Strategy<S?>.fallbackWith(fallback: FallbackStrategy<S>): FallbackStrategy<S> where S : Any =
  StrategyFallback(this, fallback)

fun FallbackIntentStrategy.andTrain(training: FallbackTrainingStrategy): GameStrategy =
  EitherDone(this, training)

class EitherDone(
  val intent: FallbackIntentStrategy,
  val training: FallbackTrainingStrategy
) : GameStrategy {
  override val name: String
    get() = "Either done{ ${intent.name} || ${training.name} }"

  override fun done(game: Game) =
    intent.done(game) || training.done(game)

  override fun play(game: Game) =
    Decision(intent.play(game), training.play(game))
}

class SimpleStrategy(val decision: Decision) : GameStrategy {
  override val name: String
    get() = "Simple decision"

  override fun play(game: Game) = decision
}

class Decision(
  val intent: Intent,
  val training: TrainingAction
) {
  companion object {
    val NoOp = Decision(Wait, NoTraining)
  }
}

//////////
// Game //
//////////

class Turn(
  val battlefield: Battlefield,
  val strategy: GameStrategy,
  val decision: Decision
)

class History(private val _turns: MutableList<Turn> = mutableListOf()) {
  val turns: List<Turn>
    get() = _turns

  fun addTurn(turn: Turn) =
    _turns.add(turn)

  val currentTurn
    get() = turns.lastIndex
}

/**
 * Warning: all mutable!
 */
class Memory(

)

class Game(
  val sites: List<MapSite>,
  private var internalMetaStrategy: MetaStrategy,
  val memory: Memory = Memory(),
  val history: History = History(),
  private var currentStrategy: GameStrategy = SimpleStrategy(Decision.NoOp),
  battlefield: Battlefield? = null
) {
  private lateinit var currentBattlefield: Battlefield

  // uses the first battlefield
  val initialPosition: PositionImpl
    get() = history.turns.first().battlefield.myQueen.position

  init {
    battlefield?.let { currentBattlefield = it }
  }

  val battlefield
    get() = currentBattlefield

  val strategy
    get() = currentStrategy

  val meta: MetaStrategy
    get() = internalMetaStrategy

  val lastOutput
    get() = out


  private var out: List<String> = listOf()

  fun playTurn(battlefield: Battlefield): List<String> {
    postParsing()

    currentBattlefield = battlefield
    currentStrategy = meta.play(this)

    val decision = currentStrategy.play(this) ?: Decision.NoOp

    if (decision == Decision.NoOp) debug("WW: used fallback noop")

    val intent = if (decision.intent != null) {
      decision.intent
    } else {
      debug("WW: no intent from $meta")
      Wait
    }

    val training = if (decision.training != null) {
      decision.training
    } else {
      debug("WW: no training")
      NoTraining
    }

    out = listOf(intent.command, training.command)

    out.forEach { println(it) }

    history.addTurn(Turn(currentBattlefield, currentStrategy, decision))

    // for tests
    return out
  }

  /**
   * Computes things that can be infered from the parsed data
   */
  private fun postParsing() {
    if (history.turns.isNotEmpty()) {
      diffUpdate()
    }
  }

  private fun diffUpdate() {
  }
}

/////////////
// Parsing //
/////////////

class SitesParsing(
  val goldMines: MutableList<Site.GoldMine> = mutableListOf(),
  val towers: MutableList<Tower> = mutableListOf(),
  val stables: MutableList<Site.Barracks> = mutableListOf(),
  val archeries: MutableList<Site.Barracks> = mutableListOf(),
  val phlegra: MutableList<Site.Barracks> = mutableListOf()
)

data class SoldiersParsing(
  val knights: MutableList<Soldier.Knight> = mutableListOf(),
  val archers: MutableList<Soldier.Archer> = mutableListOf(),
  val giants: MutableList<Soldier.Giant> = mutableListOf()
) {
  lateinit var queen: Soldier.Queen
}

fun parseSites(mapSites: List<MapSite>, numSites: Int, input: Scanner): Sites {

  // pre sort the sites so it's done once for all the strategies (no recompute on each get())
  // avoid time loss on functional style .filter{}.map{} if done later
  // no time problem yet, just anticipating based on previously played games
  // it makes this block repetitive but I thing it's worth it

  val emptySites = mutableListOf<Site.Empty>()

  val ownedSites = listOf(SitesParsing(), SitesParsing()) // 0: friendly 1:enemy

  (0 until numSites).forEach { _ ->
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

      C.Sites.EMPTY_STRUCTURE_TYPE -> {
        val empty = Site.Empty(
          mapSites.first { it.id.value == siteId },
          gold,
          maxMineSize,
          structureType,
          owner,
          param1,
          param2
        )
        emptySites.add(empty)
      }

      C.Sites.Mine.TYPE -> {
        val mine = Site.GoldMine(
          mapSites.first { it.id.value == siteId },
          gold,
          maxMineSize,
          structureType,
          owner,
          param1,
          param2
        )

        ownedSites[owner].goldMines.add(mine)
      }

      C.Sites.Tower.TYPE -> {
        val tower = Tower(
          mapSites.first { it.id.value == siteId },
          gold,
          maxMineSize,
          structureType,
          owner,
          param1,
          param2
        )

        ownedSites[owner].towers.add(tower)
      }

      C.Sites.Barracks.TYPE -> {
        val barracks = Site.Barracks(
          mapSites.first { it.id.value == siteId },
          gold,
          maxMineSize,
          structureType,
          owner,
          param1,
          param2
        )

        when (param2) {
          C.U.KnightData.type -> {
            ownedSites[owner].stables.add(barracks)
          }

          C.U.ArcherData.type -> {
            ownedSites[owner].archeries.add(barracks)
          }

          C.U.GiantData.type -> {
            ownedSites[owner].phlegra.add(barracks)
          }
        }
      }
      else -> throw IllegalStateException("What structure type is $structureType?")
    }
  }

  return Sites(
    emptySites,
    friendly = PlayerSites(ownedSites[C.FRIENDLY_OWNER]),
    enemy = PlayerSites(ownedSites[C.ENEMY_OWNER])
  )
}

private fun parseUnits(
  numUnits: Int,
  input: Scanner
): Soldiers {

  val parsing = listOf(SoldiersParsing(), SoldiersParsing())

  (0 until numUnits).forEach {
    val x = input.nextInt()
    val y = input.nextInt()
    val owner = input.nextInt()
    val unitType = input.nextInt()
    val health = input.nextInt()

    val soldierGroup = parsing[owner]

    when (unitType) {
      C.U.QueenData.type -> {
        soldierGroup.queen = Soldier.Queen(PositionImpl(x, y), owner, unitType, health)
      }
      C.U.KnightData.type -> {
        soldierGroup.knights.add(Soldier.Knight(PositionImpl(x, y), owner, unitType, health))
      }
      C.U.ArcherData.type -> {
        soldierGroup.archers.add(Soldier.Archer(PositionImpl(x, y), owner, unitType, health))
      }
      C.U.GiantData.type -> {
        soldierGroup.giants.add(Soldier.Giant(PositionImpl(x, y), owner, unitType, health))
      }
      else -> throw IllegalStateException("No unit type like $unitType")
    }
  }

  return Soldiers(
    friendly = PlayerSoldiers(parsing[C.FRIENDLY_OWNER]),
    enemy = PlayerSoldiers(parsing[C.ENEMY_OWNER])
  )
}


/**
 * STRATEGIES
 *
 * ```
.#####
#     # ##### #####    ##   ##### ######  ####  # ######  ####
#         #   #    #  #  #    #   #      #    # # #      #
.#####    #   #    # #    #   #   #####  #      # #####   ####
......#   #   #####  ######   #   #      #  ### # #           #
#     #   #   #   #  #    #   #   #      #    # # #      #    #
.#####    #   #    # #    #   #   ######  ####  # ######  ####
 *```
 */

//////////////////////
// QUEEN STRATEGIES //
//////////////////////

object WaitIntent : FallbackIntentStrategy {
  override val name: String
    get() = "No Intent fallback"

  override fun play(game: Game): Wait {
    debug("WW: used fallback intent")
    return Wait
  }
}

object TakeNextEmptySite : IntentStrategy {
  override val name: String
    get() = "Take next empty site"

  override fun play(game: Game): Intent {
    val queen = game.battlefield.units.friendly.queen
    val battlefield = game.battlefield
    val history = game.history

    // take empty site if next to it
    return if (battlefield.touchedSite.touches && battlefield.touchedSiteAsSite.isEmpty) {
      if (battlefield.sites.friendly.barracks.count() % 2 == 0) {
        BuildStable(battlefield.touchedSite.siteId)
      } else {
        BuildArchery(battlefield.touchedSite.siteId)
      }
    } else {
      // get the nearest unowned site
      // TODO: travelling salesman problem?
      val nearestSite = battlefield.sites.emptySites.minBy {
        it.site.position.distanceTo(queen.position)
      }?.site?.position

      debug("Nearest empty site $nearestSite")

      if (nearestSite != null) {
        Move(nearestSite)
      } else {
        // return to starting location for safety
        Move(
          history.turns.firstOrNull()?.battlefield?.units?.friendly?.queen?.position
            ?: game.battlefield.units.friendly.queen.position // 1st turn case
        )
      }
    }
  }
}

object FallbackToOrigin : IntentStrategy {
  override val name: String
    get() = "Fallback to origin"

  override fun play(game: Game): Intent {
    val history = game.history
    return Move(history.turns.first().battlefield.units.friendly.queen.position)
  }
}

object TakeThenFallback : IntentStrategy {
  override val name: String
    get() = "Take then fallback"

  override fun play(game: Game): Intent {
    val battlefield = game.battlefield
    val history = game.history
    val firstBattlefield = history.turns.firstOrNull()?.battlefield ?: battlefield
    return if (battlefield.units.friendly.queen.health < firstBattlefield.units.friendly.queen.health / 2) {
      FallbackToOrigin.play(game)
    } else {
      TakeNextEmptySite.play(game)
    }
  }
}

class OutmineAndDefend(val goldRateTarget: Int) : IntentStrategy {
  override val name: String
    get() = "Outmine and defend"

  override fun play(game: Game): Intent {
    debug("prio 1: get gold")

    val battlefield = game.battlefield

    return if (battlefield.goldMinesRate < goldRateTarget) {
      val existingGoldMine = battlefield.sites.friendly.goldMines.filter {
        it.maxIncomeRateSize != it.incomeRate
      }.minBy {
        it.site.position.distanceTo(battlefield.units.friendly.queen.position)
      }

      val nearestEmptySite = battlefield.sites.nearestEmptySite(
        battlefield.units.myQueen.position
      )

      val targetGoldMine = existingGoldMine ?: nearestEmptySite

      when {
        // no target -> implement fallback
        targetGoldMine == null -> {
          debug("No gold mine to build")
          Wait
        }

        else -> BuildMine(targetGoldMine.site.id)
      }
    } else {
      debug("prio 2 build")
      // TODO prio 0: giants to kill towers
      // 1. archers
      if (battlefield.sites.friendly.archeries.isEmpty()) {
        val nextArchery = battlefield.sites.nearestEmptySite(battlefield.units.myQueen.position)

        if (nextArchery == null) {
          debug("Found no site to build a new archery")
          Wait
        } else {
          BuildArchery(nextArchery.site.id)
        }
      }
      // 2. towers
      else if (battlefield.sites.friendly.towers.isEmpty()) {
        // TODO: improve tower location selection, choose in the middle?
        val nextTower = battlefield.sites.nearestEmptySite(battlefield.units.myQueen.position)

        if (nextTower == null) {
          debug("Found no site to build a new tower")
          Wait
        } else {
          BuildTower(nextTower.site.id)
        }
      }
      // 3. knights
      else {
        val nextStable = battlefield.sites.nearestEmptySite(battlefield.units.myQueen.position)

        if (nextStable == null) {
          debug("Found no site to build a new stable")
          Wait
        } else {
          BuildStable(nextStable.site.id)
        }
      }
    }
  }
}

abstract class SingleTurnOrder : IntentStrategy {
  private var _done = false

  override fun done(game: Game) = _done

  override fun play(game: Game): Intent? {
    _done = true
    return playOneTurn(game)
  }

  abstract fun playOneTurn(game: Game): Intent?
}

object BuildSingleStableOnSafeNearest : SingleTurnOrder() {
  override val name: String
    get() = "Build single stable on safe nearest"

  override fun done(game: Game) = game.battlefield.sites.friendly.stables.isNotEmpty()

  override fun playOneTurn(game: Game): Intent? {
    return game.battlefield.sites.emptySites.filter {
      game.battlefield.sites.enemy.isInTowerRange(it.site)
    }.nearestTo(game.battlefield.myQueen)?.let {
      BuildStable(it.site.id)
    }
  }
}

class ExtendMines(val targetGoldRate: Int = Int.MAX_VALUE) : IntentStrategy {
  override val name: String
    get() = "Extend mines"

  override fun done(game: Game) =
    game.battlefield.goldMinesRate >= targetGoldRate

  override fun play(game: Game): Intent? {
    val toUpgrade = game.battlefield.sites.friendly.goldMines
      .filter { mine ->
        game.battlefield.units.enemy.all.none { it.distanceTo(mine) < it.speed }
      }
      .firstOrNull {
        it.incomeRate < it.maxIncomeRateSize
      } ?: game.battlefield.sites.emptySites.filter {
      game.battlefield.sites.enemy.isNotAcrossTowerRange(game.battlefield.myQueen, it)
    }.sortedBy {
      it.distanceTo(game.battlefield.myQueen)
    }.firstOrNull()

    if (toUpgrade == null) {
      debug("WW: didn't find a site to build a mine")
      return null
    }

    return BuildMine(toUpgrade.site.id)
  }
}

class PrepareAttack(val stableCount: Int = 0) : IntentStrategy {
  override val name: String
    get() = "Prepare attack"

  override fun done(game: Game): Boolean {
    return game.battlefield.sites.friendly.stables.size >= stableCount
  }

  override fun play(game: Game): Intent {
    // get a stable
    val stableTarget = game.battlefield.sites.emptySites
      .nearestTo(game.battlefield.myQueen.position)!!

    return BuildStable(stableTarget.site.id)
  }
}

object PrepareArcheryDefense : IntentStrategy {
  override val name: String
    get() = "Prepare archery defense"

  override fun done(game: Game): Boolean {
    return game.battlefield.sites.friendly.archeries.isNotEmpty()
  }

  override fun play(game: Game): Intent? {
    if (game.battlefield.sites.friendly.archeries.isEmpty()) {
      // get an archery
      val archeryTarget = game.battlefield.sites.emptySites
        .nearestTo(game.battlefield.myQueen.position)!!

      return BuildArchery(archeryTarget.site.id)
    }

    debug("Didn't find any way to defend?")
    return null
  }
}

object TowerSpam : IntentStrategy {
  override val name: String
    get() = "Tower spam"

  override fun play(game: Game): Intent? {
    game.battlefield.sites.emptySites.nearestTo(game.battlefield.myQueen.position)?.let {
      return BuildTower(it.site.id)
    }

    debug("No empty site left?")
    return null
  }
}

class TowerDefense(val towerCount: Int = 3) : IntentStrategy {
  override val name: String
    get() = "Tower defense(${towerCount})"

  private var computedIntent: IntentStrategy? = null

  override fun play(game: Game): Intent? {
    val state = computedIntent
    if (state == null) {
      computedIntent = game.battlefield.sites.emptySites
        .sortedBy { it.distanceTo(game.battlefield.myQueen) }
        .take(towerCount)
        .map { MaximizeTowerHp(it.site.id) as IntentStrategy }
        .fold(skip()) { acc, e ->
          acc.then(e)
        }
    }

    val state2 = computedIntent
    return if (state2 != null) {
      return state2.play(game)
    } else null
  }
}

class MaximizeTowerHp(
  val siteId: SiteId,
  val targetHp: Int = C.Sites.Tower.MAX_HP - C.Sites.Tower.TOWER_HP_DECAY
) : IntentStrategy {
  override val name: String
    get() = "Max tower(${siteId}@${targetHp}hp)"

  override fun done(game: Game): Boolean {
    val remainingHP = game.battlefield.sites.friendly.towers.firstOrNull {
      it.site.id == siteId
    }?.remainingHP

    return remainingHP != null && remainingHP >= targetHp
  }

  override fun play(game: Game) = BuildTower(siteId)
}

/**
 * Strictly follows a build order
 */
class BuildOrder : IntentStrategy {
  override val name: String
    get() = "Build order"

  private var _done = false

  override fun done(game: Game) = _done

  override fun play(game: Game): Intent {
    val battlefield = game.battlefield

    val buildOnNearestEmptySite: ((SiteId) -> Intent) -> Intent = { builder ->
      battlefield.sites.emptySites.nearestTo(game.battlefield.myQueen)
        ?.let { builder(it.site.id) }!!
    }

    return when {
      battlefield.sites.friendly.archeries.isEmpty() -> {
        buildOnNearestEmptySite(::BuildArchery)
      }
      battlefield.sites.friendly.goldMines.isEmpty() -> {
        buildOnNearestEmptySite(::BuildMine)
      }
      battlefield.sites.friendly.towers.isEmpty() -> {
        buildOnNearestEmptySite(::BuildTower)
      }
      battlefield.sites.friendly.stables.isEmpty() -> {
        buildOnNearestEmptySite(::BuildStable)
      }
      // TODO optimise, save 1 turn
      else -> {
        if (_done) throw IllegalStateException("The build order strategy is done.")
        else {
          _done = true
          Wait
        }
      }
    }
  }
}

/////////////////////////
// TRAINING STRATEGIES //
/////////////////////////

object TrainNothing : FallbackTrainingStrategy {
  override val name: String
    get() = "No training strategy"

  override fun play(game: Game): NoTraining {
    debug("WW: used fallback training")
    return NoTraining
  }
}

object BuildKnightsCloserToEnemyQueen : TrainingStrategy {
  override val name: String
    get() = "Build knight closer to enemy queen"

  override fun play(game: Game): TrainingAction {
    // try to find barracks with that type of unit
    val battlefield = game.battlefield

    val barracksClosestToEnemyQueen = battlefield.sites.friendly.stables.minBy {
      it.site.position.distanceTo(battlefield.units.enemy.queen.position)
    }?.site?.id

    return if (barracksClosestToEnemyQueen != null) {
      TrainingAction.AtLocation(barracksClosestToEnemyQueen)
    } else {
      NoTraining
    }
  }
}

object BuildArchers : TrainingStrategy {
  override val name: String
    get() = "Build archers"

  override fun play(game: Game): TrainingAction {
    // try to find barracks with that type of unit
    val barracksClosestToFriendlyQueen = game.battlefield.sites.friendly.archeries.minBy {
      it.site.position.distanceTo(game.battlefield.units.friendly.queen.position)
    }?.site?.id

    return if (barracksClosestToFriendlyQueen != null) {
      TrainingAction.AtLocation(barracksClosestToFriendlyQueen)
    } else {
      NoTraining
    }
  }
}

data class BalancedTrainingStrategy(val maxKnightToArcherRatio: Double) : TrainingStrategy {
  override val name: String
    get() = "Balanced training strategy"

  override fun play(game: Game): TrainingAction {
    val battlefield = game.battlefield

    // try to balance archers and knights when possible
    val knightCount = battlefield.units.friendly.knights.size
    val archerCount = battlefield.units.friendly.archers.size

    return if (archerCount * maxKnightToArcherRatio >= knightCount) {
      debug("Build knight knights=$knightCount, archers=$archerCount")
      BuildKnightsCloserToEnemyQueen.play(game)
    } else {
      debug("Build archer knights=$knightCount, archers=$archerCount")
      BuildArchers.play(game)
    }
  }
}

object TrainDefense : TrainingStrategy {
  override val name: String
    get() = "Train defense"

  override fun done(game: Game) =
    game.battlefield.units.friendly.archers.isNotEmpty()

  override fun play(game: Game): TrainingAction {
    // save money for archers
    if (game.battlefield.gold < C.U.ArcherData.cost) return NoTraining

    // FIXME: improve with multiple site building if necessary
    return game.battlefield.sites.friendly.archeries.firstOrNull { it.isAvailable }?.site?.id?.let {
      TrainingAction.AtLocation(it)
    } ?: NoTraining
  }
}

fun Attack(game: Game): TrainingAction {
  val towersAreCountered = game.battlefield.sites.enemy.towers.isEmpty() ||
      game.battlefield.units.friendly.giants.isNotEmpty() ||
      game.battlefield.sites.friendly.phlegra.any { it.isBusy }

  if (!towersAreCountered) {
    // save money to make a giant
    if (game.battlefield.gold < C.U.GiantData.cost) return NoTraining
  }

  // save money for a knight
  if (game.battlefield.gold < C.U.KnightData.cost) return NoTraining

  // FIXME: improve with multiple site building if necessary
  return game.battlefield.sites.friendly.stables.firstOrNull { it.isAvailable }?.site?.id?.let {
    TrainingAction.AtLocation(it)
  } ?: NoTraining
}

//////////
// META //
//////////


/**
 * Force a specific strategy
 */
class FixedMeta(val fixed: GameStrategy) : MetaStrategy {
  override val name: String
    get() = "Fixed meta"

  override fun play(game: Game): GameStrategy = fixed
}

/**
 * Keep the existing strategy
 */
object StaticMeta : MetaStrategy {
  override val name: String
    get() = "Static meta"

  override fun play(game: Game): GameStrategy = game.strategy
}

object ReactiveMeta : MetaStrategy {
  override val name: String
    get() = "Reactive meta"

  val prepareAttackThenDefend =
    PrepareAttack(1).then(ExtendMines(5)).then(PrepareAttack(2)).then(ExtendMines())

  override fun play(game: Game): GameStrategy {
    val intent = prepareAttackThenDefend

    val training: Strategy<TrainingAction?> = BuildKnightsCloserToEnemyQueen

    return intent.fallbackWith(WaitIntent).andTrain(training.fallbackWith(TrainNothing))
  }
}

//////////
// MAIN //
//////////

fun main(args: Array<String>) {
  val input = Scanner(System.`in`)
  val numSites = input.nextInt()

  val mapSites = (0 until numSites).map {
    val siteId = input.nextInt()
    val x = input.nextInt()
    val y = input.nextInt()
    val radius = input.nextInt()

    MapSite(SiteId(siteId), PositionImpl(x, y), radius)
  }

  val game = Game(
    sites = mapSites,
    internalMetaStrategy = ReactiveMeta
  )

  // game loop
  while (true) {
    val gold = input.nextInt()
    val touchedSite = input.nextInt()

    val sites = parseSites(mapSites, numSites, input)
    val numUnits = input.nextInt()
    val units = parseUnits(numUnits, input)

    val battlefield = Battlefield(gold, TouchedSite(SiteId(touchedSite)), sites, units)

    game.playTurn(battlefield)
  }
}

// TODO: don't build gold mines next to enemies
// TODO: dont rebuild expired gold mines
// send giants when there is a tower
