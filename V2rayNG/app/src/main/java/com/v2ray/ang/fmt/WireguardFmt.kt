package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.idnHost
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.extension.removeWhiteSpace
import com.v2ray.ang.util.Utils
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64

object WireguardFmt : FmtBase() {
    private val interfaceSection = Regex("(?im)^\\s*\\[interface]\\s*$")
    private val peerSection = Regex("(?im)^\\s*\\[peer]\\s*$")
    private val amneziaKeys = setOf(
        "jc", "jmin", "jmax", "s1", "s2", "s3", "s4",
        "h1", "h2", "h3", "h4", "i1", "i2", "i3", "i4", "i5",
        "headerprotectionkey", "contentpaddingaddition", "rekeyaftertime",
        "rekeytimeout", "rejectaftertime", "keepalivetimeout",
        "maxhandshakeattempts", "randomizepackettrailers", "disablecookiereplies",
    )

    fun parse(str: String): ProfileItem? {
        val config = ProfileItem.create(EConfigType.WIREGUARD)

        val uri = URI(Utils.fixIllegalUrl(str))
        if (uri.rawQuery.isNullOrEmpty()) return null
        val queryParam = getQueryParam(uri)

        config.remarks = Utils.decodeURIComponent(uri.fragment.orEmpty()).let { it.ifEmpty { "none" } }
        config.server = uri.idnHost
        config.serverPort = uri.port.toString()
        config.secretKey = uri.userInfo.orEmpty()
        config.localAddress = queryParam["address"] ?: AppConfig.WIREGUARD_LOCAL_ADDRESS_V4
        config.publicKey = queryParam["publickey"].orEmpty()
        config.preSharedKey = queryParam["presharedkey"]?.nullIfBlank()
        config.mtu = Utils.parseInt(queryParam["mtu"] ?: AppConfig.WIREGUARD_LOCAL_MTU)
        config.reserved = queryParam["reserved"] ?: "0,0,0"
        config.allowedIPs = queryParam["allowedips"]
        config.keepAlive = queryParam["keepalive"]?.toIntOrNull()?.takeIf { it > 0 }

        return config
    }

    fun parseAmneziaWG(str: String): ProfileItem? {
        val payload = str.substringAfter("://", "").substringBefore('#')
        if (payload.isBlank()) return null
        val name = str.substringAfter('#', "").takeIf { it.isNotEmpty() }
            ?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) }

        return runCatching {
            parseWireguardConfFile(
                decodeBase64Url(payload).toString(Charsets.UTF_8),
                forceAmnezia = true,
                profileName = name,
            )
        }.getOrNull()
    }

    fun parseWireguardConfFile(
        str: String,
        forceAmnezia: Boolean = false,
        profileName: String? = null,
    ): ProfileItem {
        val normalizedConfig = normalizeConfText(str)
        val config = ProfileItem.create(EConfigType.WIREGUARD)
        val interfaceParams = linkedMapOf<String, String>()
        val peerParams = linkedMapOf<String, String>()
        val interfaceAddresses = mutableListOf<String>()
        var currentSection: String? = null

        normalizedConfig.lines().forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#") || trimmedLine.startsWith(";")) {
                return@forEach
            }

            when {
                trimmedLine.equals("[Interface]", ignoreCase = true) -> currentSection = "Interface"
                trimmedLine.equals("[Peer]", ignoreCase = true) -> currentSection = "Peer"
                else -> {
                    val parts = trimmedLine.split("=", limit = 2).map { it.trim() }
                    if (parts.size == 2) {
                        when (currentSection) {
                            "Interface" -> {
                                val key = parts[0].lowercase()
                                if (key == "address") {
                                    interfaceAddresses += parts[1]
                                } else {
                                    interfaceParams[key] = parts[1]
                                }
                            }
                            "Peer" -> peerParams[parts[0].lowercase()] = parts[1]
                        }
                    }
                }
            }
        }

        config.secretKey = interfaceParams["privatekey"].orEmpty()
        config.remarks = profileName ?: System.currentTimeMillis().toString()
        config.localAddress = interfaceAddresses
            .flatMap { it.split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString("\n")
            .ifEmpty { AppConfig.WIREGUARD_LOCAL_ADDRESS_V4 }
        config.mtu = Utils.parseInt(interfaceParams["mtu"] ?: AppConfig.WIREGUARD_LOCAL_MTU)
        config.publicKey = peerParams["publickey"].orEmpty()
        config.preSharedKey = peerParams["presharedkey"]?.nullIfBlank()
        config.allowedIPs = peerParams["allowedips"]?.takeIf { it.isNotBlank() }
            ?: "0.0.0.0/0,::/0"
        config.keepAlive = peerParams["persistentkeepalive"]?.toIntOrNull()?.takeIf { it > 0 }
        parseEndpoint(peerParams["endpoint"].orEmpty()).also {
            config.server = it.first
            config.serverPort = it.second
        }
        config.reserved = peerParams["reserved"] ?: "0,0,0"

        config.isAmneziaWG = forceAmnezia || interfaceParams.keys.any(amneziaKeys::contains)
        if (config.isAmneziaWG) {
            config.awgJc = interfaceParams.intValue("jc")
            config.awgJmin = interfaceParams.intValue("jmin")
            config.awgJmax = interfaceParams.intValue("jmax")
            config.awgS1 = interfaceParams.intValue("s1")
            config.awgS2 = interfaceParams.intValue("s2")
            config.awgS3 = interfaceParams.intValue("s3")
            config.awgS4 = interfaceParams.intValue("s4")
            config.awgH1 = interfaceParams["h1"]
            config.awgH2 = interfaceParams["h2"]
            config.awgH3 = interfaceParams["h3"]
            config.awgH4 = interfaceParams["h4"]
            config.awgI1 = interfaceParams["i1"]
            config.awgI2 = interfaceParams["i2"]
            config.awgI3 = interfaceParams["i3"]
            config.awgI4 = interfaceParams["i4"]
            config.awgI5 = interfaceParams["i5"]
            config.awgHeaderProtectionKey = interfaceParams["headerprotectionkey"]
            config.awgContentPaddingAddition = interfaceParams["contentpaddingaddition"]
            config.awgRekeyAfterTime = interfaceParams["rekeyaftertime"]
            config.awgRekeyTimeout = interfaceParams["rekeytimeout"]
            config.awgRejectAfterTime = interfaceParams["rejectaftertime"]
            config.awgKeepaliveTimeout = interfaceParams["keepalivetimeout"]
            config.awgMaxHandshakeAttempts = interfaceParams["maxhandshakeattempts"]
            config.awgRandomizePacketTrailers =
                interfaceParams["randomizepackettrailers"].asBoolean()
            config.awgDisableCookieReplies =
                interfaceParams["disablecookiereplies"].asBoolean()
        }

        return config
    }

    /**
     * Normalizes WireGuard/AmneziaWG INI copied from files, messages, or Markdown.
     * Some Android clipboards prepend invisible Unicode characters, while chat apps
     * commonly wrap configuration blocks in ```ini fences.
     */
    fun normalizeConfText(str: String): String {
        var normalized = str.trim()
            .trimStart('\uFEFF', '\u200B', '\u2060')
            .trim()

        if (normalized.startsWith("```")) {
            normalized = normalized.substringAfter('\n', "")
            if (normalized.trimEnd().endsWith("```")) {
                normalized = normalized.trimEnd().dropLast(3)
            }
        }

        return normalized.trim()
    }

    fun isWireguardConf(str: String): Boolean {
        val normalized = normalizeConfText(str)
        return interfaceSection.containsMatchIn(normalized) &&
                peerSection.containsMatchIn(normalized)
    }

    fun toUri(config: ProfileItem): String {
        val query = hashMapOf<String, String>()
        query["publickey"] = config.publicKey.orEmpty()
        config.reserved?.let { query["reserved"] = it.removeWhiteSpace().orEmpty() }
        query["address"] = config.localAddress
            .orEmpty()
            .split(',', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(",")
        config.mtu?.let { query["mtu"] = it.toString() }
        config.preSharedKey?.let { query["presharedkey"] = it.removeWhiteSpace().orEmpty() }
        config.allowedIPs?.takeIf { it.isNotBlank() }?.let {
            query["allowedips"] = it.removeWhiteSpace().orEmpty()
        }
        config.keepAlive?.takeIf { it > 0 }?.let { query["keepalive"] = it.toString() }
        return toUri(config, config.secretKey, query)
    }

    fun exportUri(config: ProfileItem): String {
        if (!config.isAmneziaWG) return AppConfig.WIREGUARD + toUri(config)

        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(toConf(config).toByteArray())
        val name = config.remarks.takeIf { it.isNotBlank() }
            ?.let { URLEncoder.encode(it, Charsets.UTF_8.name()).replace("+", "%20") }
        return buildString {
            append(AppConfig.AMNEZIAWG)
            append(payload)
            if (name != null) append('#').append(name)
        }
    }

    fun toConf(config: ProfileItem): String = buildString {
        appendLine("[Interface]")
        appendLine("PrivateKey = ${config.secretKey.orEmpty()}")
        config.localAddress
            .orEmpty()
            .split(',', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .forEach { appendLine("Address = $it") }
        config.mtu?.let { appendLine("MTU = $it") }

        if (config.isAmneziaWG) {
            appendInt("Jc", config.awgJc)
            appendInt("Jmin", config.awgJmin)
            appendInt("Jmax", config.awgJmax)
            appendInt("S1", config.awgS1)
            appendInt("S2", config.awgS2)
            appendInt("S3", config.awgS3)
            appendInt("S4", config.awgS4)
            appendValue("H1", config.awgH1)
            appendValue("H2", config.awgH2)
            appendValue("H3", config.awgH3)
            appendValue("H4", config.awgH4)
            appendValue("I1", config.awgI1)
            appendValue("I2", config.awgI2)
            appendValue("I3", config.awgI3)
            appendValue("I4", config.awgI4)
            appendValue("I5", config.awgI5)
            appendValue("HeaderProtectionKey", config.awgHeaderProtectionKey)
            appendValue("ContentPaddingAddition", config.awgContentPaddingAddition)
            appendValue("RekeyAfterTime", config.awgRekeyAfterTime)
            appendValue("RekeyTimeout", config.awgRekeyTimeout)
            appendValue("RejectAfterTime", config.awgRejectAfterTime)
            appendValue("KeepaliveTimeout", config.awgKeepaliveTimeout)
            appendValue("MaxHandshakeAttempts", config.awgMaxHandshakeAttempts)
            if (config.awgRandomizePacketTrailers) appendLine("RandomizePacketTrailers = true")
            if (config.awgDisableCookieReplies) appendLine("DisableCookieReplies = true")
        }

        appendLine()
        appendLine("[Peer]")
        appendLine("PublicKey = ${config.publicKey.orEmpty()}")
        config.preSharedKey?.takeIf { it.isNotBlank() }?.let {
            appendLine("PreSharedKey = $it")
        }
        appendLine("Endpoint = ${Utils.getIpv6Address(config.server)}:${config.serverPort.orEmpty()}")
        config.allowedIPs?.takeIf { it.isNotBlank() }?.let {
            appendLine("AllowedIPs = ${it.replace('\n', ',')}")
        }
        config.keepAlive?.takeIf { it > 0 }?.let {
            appendLine("PersistentKeepalive = $it")
        }
        config.reserved?.takeIf { it.isNotBlank() && it != "0,0,0" }?.let {
            appendLine("Reserved = $it")
        }
    }

    private fun parseEndpoint(endpoint: String): Pair<String, String> {
        if (endpoint.startsWith('[')) {
            val separator = endpoint.lastIndexOf("]:")
            if (separator > 0) {
                return endpoint.substring(1, separator) to endpoint.substring(separator + 2)
            }
        }
        val separator = endpoint.lastIndexOf(':')
        return if (separator > 0) {
            endpoint.substring(0, separator) to endpoint.substring(separator + 1)
        } else {
            endpoint to ""
        }
    }

    private fun decodeBase64Url(value: String): ByteArray {
        val normalized = value.replace('-', '+').replace('_', '/')
        val padded = normalized.padEnd((normalized.length + 3) / 4 * 4, '=')
        return Base64.getDecoder().decode(padded)
    }

    private fun Map<String, String>.intValue(name: String): Int =
        get(name)?.toIntOrNull() ?: 0

    private fun String?.asBoolean(): Boolean = when (this?.trim()?.lowercase()) {
        "1", "true", "yes", "on" -> true
        else -> false
    }

    private fun StringBuilder.appendInt(name: String, value: Int) {
        if (value != 0) appendLine("$name = $value")
    }

    private fun StringBuilder.appendValue(name: String, value: String?) {
        if (!value.isNullOrBlank()) appendLine("$name = $value")
    }
}
