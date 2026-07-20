package family.wire

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Covers the three-outcome reader gate — `docs/architecture/wire-format.md` §3, §4, §8. */
class WireVersionHeaderTest {

    private data class Header(
        override val schemaVersion: WireVersion,
        override val minReaderVersion: WireVersion,
        override val minWriterVersion: WireVersion,
    ) : WireVersionHeader

    private fun header(schema: String, minReader: String, minWriter: String) = Header(
        schemaVersion = WireVersion.parse(schema),
        minReaderVersion = WireVersion.parse(minReader),
        minWriterVersion = WireVersion.parse(minWriter),
    )

    @Test
    fun readerAtOrAboveMinWriter_getsFullAccess() {
        val doc = header(schema = "2.1", minReader = "2.0", minWriter = "2.1")
        assertEquals(WireAccess.FULL, doc.accessFor(WireVersion.parse("2.1")))
        assertEquals(WireAccess.FULL, doc.accessFor(WireVersion.parse("3.0")))
    }

    @Test
    fun readerBetweenMinReaderAndMinWriter_goesReadOnly() {
        // The outcome a two-state scheme cannot express: understandable, not safely writable.
        val doc = header(schema = "2.1", minReader = "2.0", minWriter = "2.1")
        assertEquals(WireAccess.READ_ONLY, doc.accessFor(WireVersion.parse("2.0")))
    }

    @Test
    fun readerBelowMinReader_refusesWithTypedError() {
        val doc = header(schema = "3.0", minReader = "3.0", minWriter = "3.0")
        assertFailsWith<UnknownWireVersionException> { doc.accessFor(WireVersion.parse("2.1")) }
    }

    @Test
    fun schemaVersionAloneNeverChangesTheOutcome() {
        // §3: schemaVersion is diagnostics only — no reader decision depends on it.
        val reader = WireVersion.parse("2.0")
        val low = header(schema = "2.0", minReader = "2.0", minWriter = "2.0")
        val high = header(schema = "9.9", minReader = "2.0", minWriter = "2.0")
        assertEquals(low.accessFor(reader), high.accessFor(reader))
    }

    @Test
    fun headerOutOfOrder_isCorruptNotUnknown() {
        // §8: unknown means "we are too old"; corrupt means the document is broken. A caller
        // must be able to tell them apart — different UI, different fix.
        val minReaderAboveMinWriter = header(schema = "2.1", minReader = "2.1", minWriter = "2.0")
        assertFailsWith<CorruptWireFormatException> {
            minReaderAboveMinWriter.accessFor(WireVersion.parse("2.1"))
        }

        val minWriterAboveSchema = header(schema = "2.0", minReader = "2.0", minWriter = "2.1")
        assertFailsWith<CorruptWireFormatException> {
            minWriterAboveSchema.accessFor(WireVersion.parse("2.1"))
        }
    }
}
