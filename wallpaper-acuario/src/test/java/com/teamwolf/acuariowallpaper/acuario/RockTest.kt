package com.teamwolf.acuariowallpaper.acuario

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RockTest {

    @Test
    fun testRockInitialization() {
        val rock = Rock()
        assertNotNull(rock.palette)
        assertEquals(0f, rock.x, 0.001f)
        assertEquals(0f, rock.depth, 0.001f)
    }

    @Test
    fun testPlaceAt() {
        val rock = Rock()
        rock.placeAt(x = 0.45f, depth = 0.60f, scaleX = 0.40f, scaleY = 0.25f, shapeType = 2, rotationDegrees = 5f)
        assertEquals(0.45f, rock.x, 0.001f)
        assertEquals(0.60f, rock.depth, 0.001f)
        assertEquals(0.40f, rock.scaleX, 0.001f)
        assertEquals(0.25f, rock.scaleY, 0.001f)
        assertEquals(2, rock.shapeType)
        assertEquals(5f, rock.rotationDegrees, 0.001f)
    }
}
