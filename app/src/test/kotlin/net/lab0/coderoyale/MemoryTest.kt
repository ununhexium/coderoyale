package net.lab0.coderoyale

import Memory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class MemoryTest {
  @Test
  fun `can tell if a site is a stable`() {
    // given
    val memory = Memory()
    memory.setStable(1)

    assertThat(memory.isStable(1)).isTrue()
    assertThat(memory.isStable(0)).isFalse()
  }
}