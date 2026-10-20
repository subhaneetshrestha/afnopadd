package io.github.subhaneetshrestha.afnopadd.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Users' drawings stay on the device: cloud backup must exclude sheets, trash, quarantine and the
 * library index (Android's Auto Backup would otherwise upload them by default), while a
 * device-to-device transfer still carries the sheets and the index.
 */
class BackupRulesTest {

    private fun xml(path: String) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File(path)).documentElement

    private fun Element.children(tag: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }

    private fun Element.excludedPaths(): Set<String> = children("exclude").map { "${it.getAttribute("domain")}:${it.getAttribute("path")}" }.toSet()

    private val neverInTheCloud = setOf("file:sheets/", "file:trash/", "file:quarantine/", "file:library.db", "file:library.db-wal", "file:library.db-shm")

    @Test
    fun theManifestPointsAtBothRuleFiles() {
        val application = xml("src/main/AndroidManifest.xml").children("application").single()
        val android = "http://schemas.android.com/apk/res/android"
        assertEquals("@xml/data_extraction_rules", application.getAttributeNS(android, "dataExtractionRules"))
        assertEquals("@xml/backup_rules", application.getAttributeNS(android, "fullBackupContent"))
    }

    @Test
    fun cloudBackupLeavesDrawingsOnTheDevice() {
        val rules = xml("src/main/res/xml/data_extraction_rules.xml")
        val cloud = rules.children("cloud-backup").single()
        assertTrue("cloud backup excludes ${cloud.excludedPaths()}", cloud.excludedPaths().containsAll(neverInTheCloud))

        val legacy = xml("src/main/res/xml/backup_rules.xml")
        assertTrue("legacy backup excludes ${legacy.excludedPaths()}", legacy.excludedPaths().containsAll(neverInTheCloud))
    }

    @Test
    fun aDeviceTransferStillCarriesTheSheets() {
        val transfer = xml("src/main/res/xml/data_extraction_rules.xml").children("device-transfer").single()
        val excluded = transfer.excludedPaths()
        assertTrue("device transfer drops the sheets: $excluded", "file:sheets/" !in excluded && "file:library.db" !in excluded)
        assertTrue("device transfer carries the trash: $excluded", "file:trash/" in excluded && "file:quarantine/" in excluded)
    }
}
