package com.launcher.app.backup

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Spec 012 FR-025 (mandatory PII protection): verifies that расшифрованные
 * media files в `Context.filesDir/private-media/` ИСКЛЮЧЕНЫ из cloud-backup
 * (Google Drive Auto Backup) и device-transfer (Samsung Smart Switch, etc.).
 *
 * Без этого exclude — расшифрованные фото паспортов / медкарт уйдут в
 * Google account бабушки автоматически, превращая E2E крипто-фундамент
 * (спек 011) в бесполезный.
 *
 * См. specs/012-contact-photos-and-private-documents/contracts/local-media-store-layout.md.
 *
 * Task: T1202.
 *
 * Implementation note: тест читает XML файлы напрямую как text (без Robolectric)
 * чтобы избежать Koin double-start при per-method Application setup.
 */
class BackupRulesTest {

    private val dataExtractionRulesPath = "src/main/res/xml/data_extraction_rules.xml"
    private val backupRulesPath = "src/main/res/xml/backup_rules.xml"

    @Test
    fun `data_extraction_rules excludes private-media from cloud-backup`() {
        val xml = readModuleResource(dataExtractionRulesPath)

        assertTrue(
            "data_extraction_rules.xml must contain <cloud-backup> section",
            xml.contains("<cloud-backup>")
        )
        assertTrue(
            "FR-025 VIOLATION: private-media/ MUST be excluded from cloud-backup. " +
                "Without this, decrypted PII (passport photos, medical card photos) " +
                "will leak to Google Drive backup.",
            cloudBackupSection(xml).contains("""path="private-media/"""")
        )
    }

    @Test
    fun `data_extraction_rules excludes private-media from device-transfer`() {
        val xml = readModuleResource(dataExtractionRulesPath)

        assertTrue(
            "data_extraction_rules.xml must contain <device-transfer> section",
            xml.contains("<device-transfer>")
        )
        assertTrue(
            "FR-025 VIOLATION: private-media/ MUST be excluded from device-transfer. " +
                "Without this, decrypted PII will leak during Samsung Smart Switch / " +
                "Google device transfer.",
            deviceTransferSection(xml).contains("""path="private-media/"""")
        )
    }

    @Test
    fun `backup_rules (pre-Android-12 fallback) excludes private-media`() {
        val xml = readModuleResource(backupRulesPath)

        assertTrue(
            "FR-025 VIOLATION: private-media/ MUST be excluded in legacy backup_rules.xml " +
                "as well, for Android < 12 devices (minSdk=26 supports back to Android 8).",
            xml.contains("""path="private-media/"""")
        )
    }

    /**
     * Reads file relative to module root. Gradle test working dir = module dir.
     */
    private fun readModuleResource(relativePath: String): String {
        val file = File(relativePath)
        check(file.exists()) {
            "Resource file not found at ${file.absolutePath}. " +
                "Gradle test workingDir expected to be module root (':app')."
        }
        return file.readText()
    }

    private fun cloudBackupSection(xml: String): String {
        val start = xml.indexOf("<cloud-backup>")
        val end = xml.indexOf("</cloud-backup>")
        require(start >= 0 && end > start) { "<cloud-backup> section missing" }
        return xml.substring(start, end)
    }

    private fun deviceTransferSection(xml: String): String {
        val start = xml.indexOf("<device-transfer>")
        val end = xml.indexOf("</device-transfer>")
        require(start >= 0 && end > start) { "<device-transfer> section missing" }
        return xml.substring(start, end)
    }
}
