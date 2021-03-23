package net.lab0.coderoyale

import Battlefield
import Decision
import Const.FRIENDLY_OWNER
import Game
import History
import MapSite
import Memory
import MetaStrategy
import PlayerSites
import PlayerSoldiers
import PositionImpl
import Const.Units.QueenData
import Output.Intent
import Site
import Sites
import Soldier
import Soldiers
import StaticMeta
import GameStrategy
import TouchedSite
import Output.TrainingAction
import SimpleStrategy
import SiteId
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

  fun goldMines(vararg mines: Site.GoldMine) {
    this.goldMines = goldMines.toMutableList()
  }

  fun towers(vararg towers: Site.Tower) {
    this.towers = towers.toMutableList()
  }

  fun stables(vararg stables: Site.Barracks) {
    this.stables = stables.toMutableList()
  }

  fun archeries(vararg archeries: Site.Barracks) {
    this.archeries = archeries.toMutableList()
  }

  fun phlegra(vararg phlegra: Site.Barracks) {
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
    Soldier.Queen(PositionImpl(0, 0), FRIENDLY_OWNER, QueenData.type, QueenData.hp)

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

  var touchedSite = TouchedSite(SiteId(-1))
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

fun buildStrategy(builder: (StrategyBuilder) -> Unit): GameStrategy {
  val b = StrategyBuilder()
  builder(b)
  return b.build()
}

class StrategyBuilder {
  private var decision: Decision = Decision.NoOp

  fun build(): GameStrategy {
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
  private var intent: Intent = Intent.Wait
  private var training: TrainingAction = TrainingAction.NoTraining

  fun build(): Decision {
    return Decision(intent, training)
  }

  fun intent(intent: Intent) {
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
  private var strategy: GameStrategy = SimpleStrategy(Decision.NoOp)
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

  var currentStrategy: GameStrategy = SimpleStrategy(Decision.NoOp)
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

