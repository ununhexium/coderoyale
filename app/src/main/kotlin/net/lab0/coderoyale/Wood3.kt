import java.util.*
import java.io.*
import java.math.*

/**
 * Auto-generated code below aims at helping you parse
 * the standard input according to the problem statement.
 **/
fun main(args : Array<String>) {
  val input = Scanner(System.`in`)
  val numSites = input.nextInt()
  for (i in 0 until numSites) {
    val siteId = input.nextInt()
    val x = input.nextInt()
    val y = input.nextInt()
    val radius = input.nextInt()
  }

  // game loop
  while (true) {
    val gold = input.nextInt()
    val touchedSite = input.nextInt() // -1 if none
    for (i in 0 until numSites) {
      val siteId = input.nextInt()
      val ignore1 = input.nextInt() // used in future leagues
      val ignore2 = input.nextInt() // used in future leagues
      val structureType = input.nextInt() // -1 = No structure, 2 = Barracks
      val owner = input.nextInt() // -1 = No structure, 0 = Friendly, 1 = Enemy
      val param1 = input.nextInt()
      val param2 = input.nextInt()
    }
    val numUnits = input.nextInt()
    for (i in 0 until numUnits) {
      val x = input.nextInt()
      val y = input.nextInt()
      val owner = input.nextInt()
      val unitType = input.nextInt() // -1 = QUEEN, 0 = KNIGHT, 1 = ARCHER
      val health = input.nextInt()
    }

    // Write an action using println()
    // To debug: System.err.println("Debug messages...");


    // First line: A valid queen action
    // Second line: A set of training instructions
    println("WAIT")
    println("TRAIN")
  }
}
