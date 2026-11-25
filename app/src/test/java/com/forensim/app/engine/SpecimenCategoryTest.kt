package com.forensim.app.engine

import com.forensim.app.engine.scene.EvidenceType
import com.forensim.app.engine.scene.SpecimenCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecimenCategoryTest {
    @Test fun everySpecimenIsInExactlyOneGroup() {
        val ordered = SpecimenCategory.ordered
        assertEquals(EvidenceType.evidence.toSet(), ordered.toSet())
        assertEquals("a specimen is listed twice", ordered.size, ordered.toSet().size)
        assertTrue("every specimen has a named group", SpecimenCategory.OTHER.specimens.isEmpty())
    }

    @Test fun redHerringsAreNeverListed() {
        assertTrue(SpecimenCategory.ordered.none { it.redHerring })
    }
}
