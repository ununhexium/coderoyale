package net.lab0.coderoyale

import MAX_HEIGHT
import MAX_WIDTH
import Position
import kotlin.random.Random

fun randomPosition(): Position =
  Position(Random.nextInt(MAX_WIDTH), Random.nextInt(MAX_HEIGHT))