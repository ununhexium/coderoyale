package net.lab0.coderoyale

import Battlefield
import Decision
import ENEMY_OWNER
import FRIENDLY_OWNER
import FixedMeta
import Game
import History
import MAX_HEIGHT
import MAX_WIDTH
import MapSite
import MetaStrategy
import PlayerSites
import Position
import QUEEN_START_HP
import QUEEN_TYPE
import QueenAction
import Site
import Sites
import Soldier
import StaticMeta
import Strategy
import TouchedSite
import TrainingAction
import Turn


fun buildPlayerSite(builder: PlayerSitesBuilder.() -> Unit): PlayerSites {
  val b = PlayerSitesBuilder()
  builder(b)
  return b.build()
}

class PlayerSitesBuilder {
  private val goldMines = mutableListOf<Site.GoldMine>()
  private val towers = mutableListOf<Site.Tower>()
  private val stables = mutableListOf<Site.Barracks>()
  private val archeries = mutableListOf<Site.Barracks>()
  private val phlegra = mutableListOf<Site.Barracks>()

  fun build(): PlayerSites {
    return PlayerSites(goldMines, towers, stables, archeries, phlegra)
  }
}

fun buildSites(builder: SitesBuilder.() -> Unit): Sites {
  val b = SitesBuilder()
  builder(b)
  return b.build()
}

class SitesBuilder {
  private val emptySites = mutableListOf<Site.Empty>()
  private val friendlySites: PlayerSites = buildPlayerSite { }
  private val enemySites: PlayerSites = buildPlayerSite { }

  fun build(): Sites {
    return Sites(emptySites, friendlySites, enemySites)
  }
}


fun buildBattlefield(builder: BattlefieldBuilder.() -> Unit): Battlefield {
  val b = BattlefieldBuilder()
  builder(b)
  return b.build()
}

class BattlefieldBuilder {
  private var mapSites = mutableListOf<MapSite>()
  private var gold = 0
  private var touchedSite = TouchedSite(-1) // should be auto computed
  private var sites = buildSites { }
  private var units = mutableListOf<Soldier>(
    Soldier(Position(0, 0), FRIENDLY_OWNER, QUEEN_TYPE, QUEEN_START_HP),
    Soldier(Position(MAX_WIDTH, MAX_HEIGHT), ENEMY_OWNER, QUEEN_TYPE, QUEEN_START_HP)
  )

  fun build(): Battlefield {
    return Battlefield(mapSites, gold, touchedSite, sites, units)
  }

  fun gold(amount: Int) {
    this.gold = amount
  }
}

fun buildStrategy(builder: StrategyBuilder.() -> Unit): Strategy {
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

  fun decision(builder: DecisionBuilder.() -> Unit) {
    decision = buildDecision(builder)
  }
}

fun buildDecision(builder: DecisionBuilder.() -> Unit): Decision {
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

fun buildTurn(builder: TurnBuilder.() -> Unit): Turn {
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

  fun battlefield(builder: BattlefieldBuilder.() -> Unit) {
    battlefield = buildBattlefield(builder)
  }
}

fun buildHistory(builder: HistoryBuilder.() -> Unit): History {
  val b = HistoryBuilder()
  builder(b)
  return b.build()
}

class HistoryBuilder {
  private var turns = mutableListOf<Turn>()

  fun build(): History {
    return History(turns)
  }

  fun addTurn(function: TurnBuilder.() -> Unit) {
    turns.add(buildTurn(function))
  }
}

fun buildGame(builder: GameBuilder.() -> Unit): Game {
  val b = GameBuilder()
  builder(b)
  return b.build()
}

class GameBuilder {
  private var metaStrategy: MetaStrategy = StaticMeta
  private var history: History = buildHistory { }
  private var currentStrategy: Strategy = Strategy.NoOp
  private var battlefield: Battlefield = buildBattlefield { }

  fun build(): Game {
    return Game(metaStrategy, history, currentStrategy, battlefield)
  }

  fun history(builder: HistoryBuilder.() -> Unit) {
    history = buildHistory(builder)
  }

  fun strategy(builder: StrategyBuilder.() -> Unit) {
    currentStrategy = buildStrategy(builder)
  }

  fun battlefield(builder: BattlefieldBuilder.() -> Unit) {
    battlefield = buildBattlefield(builder)
  }
}
