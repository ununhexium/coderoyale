import Output.Intent
import Output.Intent.BuildArchery
import Output.Intent.BuildMine
import Output.Intent.BuildStable
import Output.Intent.BuildTower
import Output.Intent.Companion.doAt
import Output.Intent.Move
import Output.Intent.Wait
import Output.TrainingAction
import java.util.*
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

object Const {

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

  object Units {

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

inline class TouchedSite(val siteId: Int) {
  val touches: Boolean
    get() = siteId != -1
}

class MapSite(val id: Int, val position: Position, val radius: Int)

// TODO param 1 and 2: is that info available for my units or also for enemy units?
sealed class Site(
  val site: MapSite,
  val gold: Int,
  val maxMineSize: Int,
  val structureType: Int,
  val owner: Int,
  val param1: Int,
  val param2: Int
) {

  // structure type
  val isEmpty: Boolean = structureType == Const.Sites.EMPTY_STRUCTURE_TYPE
  val isGoldMine: Boolean = structureType == Const.Sites.Mine.TYPE
  val isTower: Boolean = structureType == Const.Sites.Tower.TYPE
  val isBarracks: Boolean = structureType == Const.Sites.Barracks.TYPE

  // owner
  val isFriendly: Boolean = owner == Const.FRIENDLY_OWNER
  val isEnemy: Boolean = owner == Const.ENEMY_OWNER
  val isNotOwned: Boolean = owner == Const.NO_OWNER

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
    val canTrainNow = turnsUntilFree == 0

    /**
     * the creep type: 0 for KNIGHT, 1 for ARCHER, 2 for GIANT
     */
    val creepType = param2
  }
}

data class PlayerSites(
  val goldMines: List<Site.GoldMine>,
  val towers: List<Site.Tower>,
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
  fun nearestEmptySite(position: Position): Site.Empty? {
    return emptySites.minBy {
      it.site.position.distanceTo(position)
    }
  }
}

sealed class Soldier(
  val position: Position,
  val owner: Int,
  val unitType: Int,
  val health: Int
) {
  class Queen(position: Position, owner: Int, unitType: Int, health: Int) :
    Soldier(position, owner, unitType, health)

  class Knight(position: Position, owner: Int, unitType: Int, health: Int) :
    Soldier(position, owner, unitType, health)

  class Archer(position: Position, owner: Int, unitType: Int, health: Int) :
    Soldier(position, owner, unitType, health)

  class Giant(position: Position, owner: Int, unitType: Int, health: Int) :
    Soldier(position, owner, unitType, health)

  val radius = when (unitType) {
    Const.Units.QueenData.type -> Const.Units.QueenData.radius
    Const.Units.KnightData.type -> Const.Units.KnightData.radius
    Const.Units.ArcherData.type -> Const.Units.ArcherData.radius
    Const.Units.GiantData.type -> Const.Units.GiantData.radius
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

data class Position(val x: Int, val y: Int) {
  companion object {
    val ORIGIN = Position(0, 0)
  }

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

sealed class Output(val command: String) {

  sealed class Intent(command: String) : Output(command) {

    object Wait : Intent("WAIT")
    class Move(val position: Position) : Intent("MOVE ${position.x} ${position.y}") {
      constructor(x: Int, y: Int) : this(Position(x, y))
    }

    class BuildStable(siteId: Int) : Intent("BUILD $siteId BARRACKS-KNIGHT")
    class BuildArchery(siteId: Int) : Intent("BUILD $siteId BARRACKS-ARCHER")
    class BuildPhlegra(siteId: Int) : Intent("BUILD $siteId BARRACKS-GIANT")

    class BuildTower(siteId: Int) : Intent("BUILD $siteId TOWER")
    class BuildMine(siteId: Int) : Intent("BUILD $siteId MINE")

    companion object {
      /**
       * Do action at the site or move there to do it
       */
      fun doAt(site: MapSite, touchedSite: TouchedSite, intent: Intent): Intent {
        return if (touchedSite.siteId == site.id) intent else Move(site.position)
      }
    }
  }

  sealed class TrainingAction(command: String) : Output(command) {
    object None : TrainingAction("TRAIN")
    class AtLocation(locationIds: List<Int>) :
      TrainingAction("TRAIN " + locationIds.joinToString(" ") { it.toString() }) {
      constructor(locationId: Int, vararg moreLocationIds: Int) :
          this(listOf(locationId) + moreLocationIds.toList())
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
  val canBuildKnight: Boolean = gold >= Const.Units.KnightData.cost
  val canBuildArcher: Boolean = gold >= Const.Units.ArcherData.cost
  val canBuildGiant: Boolean = gold >= Const.Units.GiantData.cost

  /**
   * Friendly gold mines rate
   */
  val goldMinesRate: Int
    get() = sites.friendly.goldMines.sumBy { it.incomeRate }

  // sites
  val touchedSiteAsSite: Site
    get() = sites.all.first { it.site.id == touchedSite.siteId }

  // UTILS

  fun buildOnNearest(what: (siteId: Int) -> Intent): Intent {
    val nearest = nearestEmpty!!.site
    return doAt(nearest, touchedSite, what(nearest.id))
  }

  // closest to friendly queen's position
  val nearestEmpty
    get() = sites.nearestEmptySite(myQueen.position)

  // shortcut
  val myQueen
    get() = units.friendly.queen
}

////////////////
// STRATEGIES //
////////////////

interface Strategy<S> where S : Any? {
  val done: Boolean
    get() = false

  fun play(game: Game): S?
}

interface FallbackStrategy<S> : Strategy<S> where S : Any {
  override fun play(game: Game): S
}

typealias MetaStrategy = FallbackStrategy<GameStrategy>
typealias GameStrategy = Strategy<Decision>

class StrategySequence<S>(val first: Strategy<S>, val next: Strategy<S>) : Strategy<S> {
  override val done
    get() = next.done

  override fun play(game: Game) =
    if (!first.done) first.play(game) else next.play(game)
}

fun <S> Strategy<S>.then(next: Strategy<S>): Strategy<S> =
  StrategySequence(this, next)

interface IntentStrategy : Strategy<Intent> {
  override fun play(game: Game): Intent
}

interface TrainingStrategy : Strategy<TrainingAction> {
  override fun play(game: Game): TrainingAction
}

fun IntentStrategy.and(training: TrainingStrategy): GameStrategy =
  EitherDone(this, training)

class EitherDone(
  val intent: IntentStrategy,
  val training: TrainingStrategy
) : GameStrategy {
  override val done
    get() = intent.done || training.done

  override fun play(game: Game) =
    Decision(intent.play(game), training.play(game))
}

class SimpleStrategy(val decision: Decision) : GameStrategy {
  override fun play(game: Game) = decision
}

class Decision(
  val intent: Intent,
  val training: TrainingAction
) {
  companion object {
    val NoOp = Decision(Wait, TrainingAction.None)
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
}

/**
 * Warning: all mutable!
 */
class Memory(

)

class Game(
  val mapSites: List<MapSite>,
  private var internalMetaStrategy: MetaStrategy,
  val memory: Memory = Memory(),
  val history: History = History(),
  private var currentStrategy: GameStrategy = SimpleStrategy(Decision.NoOp),
  battlefield: Battlefield? = null
) {
  private lateinit var currentBattlefield: Battlefield

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

    out = listOf(decision.intent.command, decision.training.command)

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
  val towers: MutableList<Site.Tower> = mutableListOf(),
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

      Const.Sites.EMPTY_STRUCTURE_TYPE -> {
        val empty = Site.Empty(
          mapSites.first { it.id == siteId },
          gold,
          maxMineSize,
          structureType,
          owner,
          param1,
          param2
        )
        emptySites.add(empty)
      }

      Const.Sites.Mine.TYPE -> {
        val mine = Site.GoldMine(
          mapSites.first { it.id == siteId },
          gold,
          maxMineSize,
          structureType,
          owner,
          param1,
          param2
        )

        ownedSites[owner].goldMines.add(mine)
      }

      Const.Sites.Tower.TYPE -> {
        val tower = Site.Tower(
          mapSites.first { it.id == siteId },
          gold,
          maxMineSize,
          structureType,
          owner,
          param1,
          param2
        )

        ownedSites[owner].towers.add(tower)
      }

      Const.Sites.Barracks.TYPE -> {
        val barracks = Site.Barracks(
          mapSites.first { it.id == siteId },
          gold,
          maxMineSize,
          structureType,
          owner,
          param1,
          param2
        )

        when (param2) {
          Const.Units.KnightData.type -> {
            ownedSites[owner].stables.add(barracks)
          }

          Const.Units.ArcherData.type -> {
            ownedSites[owner].archeries.add(barracks)
          }

          Const.Units.GiantData.type -> {
            ownedSites[owner].phlegra.add(barracks)
          }
        }
      }
      else -> throw IllegalStateException("What structure type is $structureType?")
    }
  }

  return Sites(
    emptySites,
    friendly = PlayerSites(ownedSites[Const.FRIENDLY_OWNER]),
    enemy = PlayerSites(ownedSites[Const.ENEMY_OWNER])
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
      Const.Units.QueenData.type -> {
        soldierGroup.queen = Soldier.Queen(Position(x, y), owner, unitType, health)
      }
      Const.Units.KnightData.type -> {
        soldierGroup.knights.add(Soldier.Knight(Position(x, y), owner, unitType, health))
      }
      Const.Units.ArcherData.type -> {
        soldierGroup.archers.add(Soldier.Archer(Position(x, y), owner, unitType, health))
      }
      Const.Units.GiantData.type -> {
        soldierGroup.giants.add(Soldier.Giant(Position(x, y), owner, unitType, health))
      }
      else -> throw IllegalStateException("No unit type like $unitType")
    }
  }

  return Soldiers(
    friendly = PlayerSoldiers(parsing[Const.FRIENDLY_OWNER]),
    enemy = PlayerSoldiers(parsing[Const.ENEMY_OWNER])
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

//////////
// META //
//////////


/**
 * Force a specific strategy
 */
class FixedMeta(val fixed: GameStrategy) : MetaStrategy {
  override fun play(game: Game): GameStrategy = fixed
}

/**
 * Keep the existing strategy
 */
object StaticMeta : MetaStrategy {
  override fun play(game: Game): GameStrategy = game.strategy
}

//////////////////////
// QUEEN STRATEGIES //
//////////////////////

object TakeNextEmptySite : IntentStrategy {
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
  override fun play(game: Game): Intent {
    val history = game.history
    return Move(history.turns.first().battlefield.units.friendly.queen.position)
  }
}

object TakeThenFallback : IntentStrategy {
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
  override fun play(game: Game): Intent {
    debug("prio 1: get gold")

    val battlefield = game.battlefield

    return if (battlefield.goldMinesRate < goldRateTarget) {
      val existingGoldMine = battlefield.sites.friendly.goldMines.filter {
        it.maxMineSize != it.incomeRate
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

        else -> doAt(
          targetGoldMine.site,
          battlefield.touchedSite,
          BuildMine(targetGoldMine.site.id)
        )
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
          doAt(
            nextArchery.site,
            battlefield.touchedSite,
            BuildArchery(nextArchery.site.id)
          )
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
          doAt(nextTower.site, battlefield.touchedSite, BuildTower(nextTower.site.id))
        }
      }
      // 3. knights
      else {
        val nextStable = battlefield.sites.nearestEmptySite(battlefield.units.myQueen.position)

        if (nextStable == null) {
          debug("Found no site to build a new stable")
          Wait
        } else {
          doAt(nextStable.site, battlefield.touchedSite, BuildStable(nextStable.site.id))
        }
      }
    }
  }
}

/**
 * Strictly follows a build order
 */
class BuildOrder : IntentStrategy {
  private var _done = false

  override val done
    get() = _done

  override fun play(game: Game): Intent {
    val battlefield = game.battlefield
    return when {
      battlefield.sites.friendly.archeries.isEmpty() -> {
        battlefield.buildOnNearest(::BuildArchery)
      }
      battlefield.sites.friendly.goldMines.isEmpty() -> {
        battlefield.buildOnNearest(::BuildMine)
      }
      battlefield.sites.friendly.towers.isEmpty() -> {
        battlefield.buildOnNearest(::BuildTower)
      }
      battlefield.sites.friendly.stables.isEmpty() -> {
        battlefield.buildOnNearest(::BuildStable)
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

object BuildKnightsCloserToEnemyQueen : TrainingStrategy {
  override fun play(game: Game): TrainingAction {
    // try to find barracks with that type of unit
    val battlefield = game.battlefield

    val barracksClosestToEnemyQueen = battlefield.sites.friendly.stables.minBy {
      it.site.position.distanceTo(battlefield.units.enemy.queen.position)
    }?.site?.id

    return if (barracksClosestToEnemyQueen != null) {
      TrainingAction.AtLocation(barracksClosestToEnemyQueen)
    } else {
      TrainingAction.None
    }
  }
}

object BuildArchers : TrainingStrategy {
  override fun play(game: Game): TrainingAction {
    // try to find barracks with that type of unit
    val barracksClosestToFriendlyQueen = game.battlefield.sites.friendly.archeries.minBy {
      it.site.position.distanceTo(game.battlefield.units.friendly.queen.position)
    }?.site?.id

    return if (barracksClosestToFriendlyQueen != null) {
      TrainingAction.AtLocation(barracksClosestToFriendlyQueen)
    } else {
      TrainingAction.None
    }
  }
}

data class BalancedTrainingStrategy(val maxKnightToArcherRatio: Double) : TrainingStrategy {
  override fun play(game: Game): TrainingAction {
    val battlefield = game.battlefield

    // try to balance archers and knights when possible
    val knightCount = battlefield.units.friendly.knights.size
    val archerCount = battlefield.units.friendly.archers.size

    return if (archerCount * maxKnightToArcherRatio > knightCount) {
      debug("Build knight knights=$knightCount, archers=$archerCount")
      BuildKnightsCloserToEnemyQueen.play(game)
    } else {
      debug("Build archer knights=$knightCount, archers=$archerCount")
      BuildArchers.play(game)
    }
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

    MapSite(siteId, Position(x, y), radius)
  }

  val game = Game(
    mapSites = mapSites,
    internalMetaStrategy = FixedMeta(
      BuildOrder().and(BuildArchers).then(
        OutmineAndDefend(10).and(BalancedTrainingStrategy(2.0))
      )
    )
  )

  // game loop
  while (true) {
    val gold = input.nextInt()
    val touchedSite = input.nextInt()

    val sites = parseSites(mapSites, numSites, input)
    val numUnits = input.nextInt()
    val units = parseUnits(numUnits, input)

    val battlefield = Battlefield(gold, TouchedSite(touchedSite), sites, units)

    game.playTurn(battlefield)
  }
}
