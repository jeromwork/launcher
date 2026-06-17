package com.launcher.api.localization

import kotlin.test.Test
import kotlin.test.assertEquals

class RtlHelperTest {

    @Test
    fun arabic_isRtl() {
        assertEquals(ReadingDirection.Rtl, readingDirectionFor("ar"))
        assertEquals(ReadingDirection.Rtl, readingDirectionFor("ar-SA"))
    }

    @Test
    fun hebrew_isRtl() {
        assertEquals(ReadingDirection.Rtl, readingDirectionFor("he"))
    }

    @Test
    fun english_isLtr() {
        assertEquals(ReadingDirection.Ltr, readingDirectionFor("en"))
        assertEquals(ReadingDirection.Ltr, readingDirectionFor("en-US"))
    }

    @Test
    fun russian_isLtr() {
        assertEquals(ReadingDirection.Ltr, readingDirectionFor("ru"))
    }

    @Test
    fun hindi_isLtr() {
        assertEquals(ReadingDirection.Ltr, readingDirectionFor("hi"))
    }

    @Test
    fun kazakhLatin_isLtr() {
        assertEquals(ReadingDirection.Ltr, readingDirectionFor("kk-Latn"))
    }
}
