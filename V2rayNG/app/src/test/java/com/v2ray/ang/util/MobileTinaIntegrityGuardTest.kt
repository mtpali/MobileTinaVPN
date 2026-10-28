package com.v2ray.ang.util

import com.v2ray.ang.R
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MobileTinaIntegrityGuardTest {
    // The protected release is Playstore. F-Droid JVM tasks exercise this same release contract.
    private fun releaseStrings(): Map<Int, String> {
        val root = File(requireNotNull(System.getProperty("mobiletina.test.sourceRoot")))
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val values = mutableMapOf<String, String>()
        for (sourceSet in listOf("main", "playstore")) {
            File(root, "src/$sourceSet/res/values").listFiles()
                .orEmpty().filter { it.extension == "xml" }.sortedBy { it.name }.forEach { file ->
                    val nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("string")
                    for (index in 0 until nodes.length) {
                        val node = nodes.item(index)
                        values[node.attributes.getNamedItem("name").nodeValue] = node.textContent
                    }
                }
        }
        fun resolve(name: String): String {
            val value = values.getValue(name)
            if (value.startsWith("@string/")) return resolve(value.removePrefix("@string/"))
            return Regex("""\\(u[0-9a-fA-F]{4}|.)""").replace(value) { match ->
                when (val escaped = match.groupValues[1]) {
                    "n" -> "\n"
                    "t" -> "\t"
                    "r" -> "\r"
                    else -> if (escaped.startsWith("u")) escaped.drop(1).toInt(16).toChar().toString() else escaped
                }
            }
        }
        // Reflection also supports the production resource-name obfuscation step in CI.
        return R.string::class.java.fields.filter { values.containsKey(it.name) }
            .associate { it.getInt(null) to resolve(it.name) }
    }

    @Test
    fun `current release texts and encrypted vault pass actual startup brand validation`() {
        val strings = releaseStrings()
        assertDoesNotThrow { MobileTinaIntegrityGuard.verifyBrandText { strings.getValue(it) } }
    }

    @Test
    fun `modified protected text is still rejected`() {
        val strings = releaseStrings()
        assertThrows(SecurityException::class.java) {
            MobileTinaIntegrityGuard.verifyBrandText {
                strings.getValue(it) + if (it == R.string.app_name) " changed" else ""
            }
        }
    }
}
