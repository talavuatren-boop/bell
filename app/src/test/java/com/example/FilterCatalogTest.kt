package com.example

import com.example.model.FilterCategory
import com.example.model.PhotoFilterPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterCatalogTest {

    @Test
    fun `filter catalog has 116 presets with 86 leica warm-rich and 29 regular filters`() {
        val all = PhotoFilterPreset.entries
        assertEquals(116, all.size)

        val leica = all.filter { it.isLeica }
        assertEquals(86, leica.size)
        assertTrue(leica.all { it.category == FilterCategory.LEICA })

        val regular = all.filter { it.category == FilterCategory.FILM }
        assertEquals(29, regular.size)
        assertTrue(regular.none { it.isLeica })

        val warmLeica = leica.filter { it.isWarm }
        assertTrue("Expected Leica presets to be predominantly warm", warmLeica.size >= 68)

        val base = all.filter { it.category == FilterCategory.BASE }
        assertEquals(listOf(PhotoFilterPreset.NATURAL), base)

        for (preset in all) {
            assertFalse("Label must not be blank for ${preset.name}", preset.label.isBlank())
            assertTrue("Swatch colors needed for ${preset.name}", preset.swatchColors.size >= 2)
        }

        // All 100 newly added presets carry explicit ColorGradeSpec formulas
        val gradedPresets = all.filter { it.grade != null }
        assertEquals(100, gradedPresets.size)
        for (preset in gradedPresets) {
            assertNotNull(preset.grade)
        }

        val usedSubjects = all.map { it.sampleSubject }.toSet()
        assertEquals(com.example.model.FilterSampleSubject.entries.toSet(), usedSubjects)
    }
}
