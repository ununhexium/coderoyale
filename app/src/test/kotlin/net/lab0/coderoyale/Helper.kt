package net.lab0.coderoyale

import Battlefield
import Decision
import FRIENDLY_OWNER
import Game
import History
import MapSite
import Memory
import MetaStrategy
import PlayerSites
import PlayerSoldiers
import Position
import QUEEN_START_HP
import QUEEN_TYPE
import QueenAction
import Site
import Sites
import Soldier
import Soldiers
import StaticMeta
import Strategy
import TouchedSite
import TrainingAction
import Turn


fun buildPlayerSite(builder: (PlayerSitesBuilder) -> Unit): PlayerSites {
  val b = PlayerSitesBuilder()
  builder(b)
  return b.build()
}

class PlayerSitesBuilder {
  private var goldMines = mutableListOf<Site.GoldMine>()
  private var towers = mutableListOf<Site.Tower>()
  private var stables = mutableListOf<Site.Barracks>()
  private var archeries = mutableListOf<Site.Barracks>()
  private var phlegra = mutableListOf<Site.Barracks>()

  fun build(): PlayerSites {
    return PlayerSites(goldMines, towers, stables, archeries, phlegra)
  }

  fun goldMines(goldMines: List<Site.GoldMine>) {
    this.goldMines = goldMines.toMutableList()
  }

  fun towers(towers: List<Site.Tower>) {
    this.towers = towers.toMutableList()
  }

  fun stables(stables: List<Site.Barracks>) {
    this.stables = stables.toMutableList()
  }

  fun archeries(archeries: List<Site.Barracks>) {
    this.archeries = archeries.toMutableList()
  }

  fun phlegra(phlegra: List<Site.Barracks>) {
    this.phlegra = phlegra.toMutableList()
  }
}

fun buildSites(builder: (SitesBuilder) -> Unit): Sites {
  val b = SitesBuilder()
  builder(b)
  return b.build()
}

class SitesBuilder {
  private var emptySites = mutableListOf<Site.Empty>()
  private var friendlySites: PlayerSites = buildPlayerSite { }
  private var enemySites: PlayerSites = buildPlayerSite { }

  fun build(): Sites {
    return Sites(emptySites, friendlySites, enemySites)
  }

  fun friendlySites(builder: (PlayerSitesBuilder) -> Unit) {
    this.friendlySites = buildPlayerSite(builder)
  }
}

fun buildPlayerSoldiers(builder: (PlayerSoldiersBuilder) -> Unit): PlayerSoldiers {
  val b = PlayerSoldiersBuilder()
  builder(b)
  return b.build()
}

// TODO: ensure consistent owners
class PlayerSoldiersBuilder {
  var queen: Soldier.Queen =
    Soldier.Queen(Position(0, 0), FRIENDLY_OWNER, QUEEN_TYPE, QUEEN_START_HP)

  val knights = mutableListOf<Soldier.Knight>()
  val archers = mutableListOf<Soldier.Archer>()
  val giants = mutableListOf<Soldier.Giant>()

  fun build(): PlayerSoldiers {
    return PlayerSoldiers(queen, knights, archers, giants)
  }
}

fun buildSoldiers(builder: (SoldiersBuilder) -> Unit): Soldiers {
  val b = SoldiersBuilder()
  builder(b)
  return b.build()
}

class SoldiersBuilder {
  var friendly: PlayerSoldiers = buildPlayerSoldiers { }
  var enemy: PlayerSoldiers = buildPlayerSoldiers { }

  fun build(): Soldiers {
    return Soldiers(friendly, enemy)
  }
}

fun buildBattlefield(builder: (BattlefieldBuilder) -> Unit): Battlefield {
  val b = BattlefieldBuilder()
  builder(b)
  return b.build()
}

class BattlefieldBuilder {
  // must sync map sites and sites
  var gold = 0
    private set

  var touchedSite = TouchedSite(-1)
    private set

  var sites = buildSites { }
    private set

  var units = buildSoldiers { }
    private set


  fun build(): Battlefield {
    return Battlefield(gold, touchedSite, sites, units)
  }

  fun gold(amount: Int) {
    this.gold = amount
  }

  fun sites(builder: (SitesBuilder) -> Unit) {
    this.sites = buildSites(builder)
  }
}

fun buildStrategy(builder: (StrategyBuilder) -> Unit): Strategy {
  val b = StrategyBuilder()
  builder(b)
  return b.build()
}

class SimpleStrategy(val decision: Decision) : Strategy {
  override fun result(game: Game) = decision
}

class StrategyBuilder {
  private var decision: Decision = Decision.NoOp

  fun build(): Strategy {
    return SimpleStrategy(decision)
  }

  fun decision(builder: (DecisionBuilder) -> Unit) {
    decision = buildDecision(builder)
  }
}

fun buildDecision(builder: (DecisionBuilder) -> Unit): Decision {
  val b = DecisionBuilder()
  builder(b)
  return b.build()
}

class DecisionBuilder {
  private var intent: QueenAction = QueenAction.Wait
  private var training: TrainingAction = TrainingAction.None

  fun build(): Decision {
    return Decision(intent, training)
  }

  fun intent(intent: QueenAction) {
    this.intent = intent
  }

  fun training(training: TrainingAction) {
    this.training = training
  }
}

fun buildTurn(builder: (TurnBuilder) -> Unit): Turn {
  val b = TurnBuilder()
  builder(b)
  return b.build()
}

class TurnBuilder {
  private var battlefield: Battlefield = buildBattlefield { }
  private var strategy: Strategy = Strategy.NoOp
  private var decision: Decision = buildDecision {}

  fun build(): Turn {
    return Turn(battlefield, strategy, decision)
  }

  fun battlefield(builder: (BattlefieldBuilder) -> Unit) {
    battlefield = buildBattlefield(builder)
  }
}

fun buildHistory(builder: (HistoryBuilder) -> Unit): History {
  val b = HistoryBuilder()
  builder(b)
  return b.build()
}

class HistoryBuilder {
  private var turns = mutableListOf<Turn>()

  fun build(): History {
    return History(turns)
  }

  fun addTurn(function: (TurnBuilder) -> Unit) {
    turns.add(buildTurn(function))
  }
}

fun buildGame(builder: (GameBuilder) -> Unit): Game {
  val b = GameBuilder()
  builder(b)
  return b.build()
}

class GameBuilder {
  var mapSites = mutableListOf<MapSite>()
    private set
  var metaStrategy: MetaStrategy = StaticMeta
    private set

  var memory: Memory = Memory()
    private set

  var history: History = buildHistory { }
    private set

  var currentStrategy: Strategy = Strategy.NoOp
    private set

  var battlefield: Battlefield = buildBattlefield { }
    private set

  fun mapSites(mapSites: List<MapSite>) {
    this.mapSites = mapSites.toMutableList()
  }

  fun build(): Game {
    return Game(mapSites, metaStrategy, memory, history, currentStrategy, battlefield)
  }

  fun history(builder: (HistoryBuilder) -> Unit) {
    history = buildHistory(builder)
  }

  fun strategy(builder: (StrategyBuilder) -> Unit) {
    currentStrategy = buildStrategy(builder)
  }

  fun battlefield(builder: (BattlefieldBuilder) -> Unit) {
    battlefield = buildBattlefield(builder)
  }
}

