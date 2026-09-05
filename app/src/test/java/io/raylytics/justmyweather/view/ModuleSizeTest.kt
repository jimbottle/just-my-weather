package io.raylytics.justmyweather.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleSizeTest {
    @Test
    fun `steps move one cell in one direction`() {
        val size = ModuleSize(2, 1)
        assertEquals(ModuleSize(3, 1), size.wider())
        assertEquals(ModuleSize(1, 1), size.narrower())
        assertEquals(ModuleSize(2, 2), size.taller())
        assertEquals(ModuleSize(2, 0), size.shorter()) // unclamped: callers ask fits() first
    }

    @Test
    fun `fits is the lattice and the module's floor together`() {
        val min = ModuleSize(2, 1)
        assertTrue(ModuleSize(2, 1).fits(min))
        assertTrue(ModuleSize(4, 3).fits(min))
        assertFalse(ModuleSize(1, 1).fits(min), "below the floor")
        assertFalse(ModuleSize(5, 1).fits(min), "wider than the grid")
        assertFalse(ModuleSize(2, 4).fits(min), "taller than allowed")
        assertFalse(ModuleSize(2, 0).fits(min), "no rows at all")
    }

    @Test
    fun `clamp lands on the nearest legal size`() {
        val min = ModuleSize(2, 1)
        assertEquals(ModuleSize(2, 1), ModuleSize(0, -3).clamp(min))
        assertEquals(ModuleSize(4, 3), ModuleSize(9, 9).clamp(min))
        assertEquals(ModuleSize(3, 2), ModuleSize(3, 2).clamp(min))
        // A nonsense floor (config is user data) is itself brought into the
        // lattice before it is applied.
        assertEquals(ModuleSize(4, 1), ModuleSize(1, 1).clamp(ModuleSize(7, 0)))
    }

    @Test
    fun `the label reads as counts a user can act on`() {
        assertEquals("4 wide, 2 tall", ModuleSize(4, 2).label)
    }
}
