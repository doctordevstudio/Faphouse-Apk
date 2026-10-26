package com.drdev.faphousepro.ui.main

object UrlInspector {

    private val VIDEO_URL_RE = Regex(
        """^https?://(?:[a-z0-9-]+\.)?faphouse[a-z0-9]*\.(?:com|net|desi)/videos/[a-z0-9-]+-([A-Za-z0-9]{6,8})""",
        RegexOption.IGNORE_CASE
    )

    private val ALLOWED_HOST_SUFFIXES = listOf(
        "faphouse.com", "faphouse2.com", "faphouse1.com",
        "fhaccess.com", "fhcontent.com", "fhchannel.com", "faphouse.desi"
    )

    private val TELEGRAM_HOSTS = listOf("t.me", "telegram.me", "telegram.dog")
    private val ALLOWED_EXTERNAL_SCHEMES = listOf("tel", "mailto", "sms")

    fun isVideoUrl(url: String): Boolean = VIDEO_URL_RE.matches(url)

    fun isFapHouse(url: String): Boolean {
        val host = extractHost(url) ?: return false
        return ALLOWED_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }
    }

    fun isTelegram(url: String): Boolean {
        val host = extractHost(url) ?: return false
        return TELEGRAM_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    fun isAllowedExternalScheme(url: String): Boolean {
        val scheme = url.substringBefore(':', "").lowercase()
        return scheme in ALLOWED_EXTERNAL_SCHEMES
    }

    fun isHttpOrHttps(url: String): Boolean {
        val s = url.substringBefore(':', "").lowercase()
        return s == "http" || s == "https"
    }

    fun isDirectMediaLink(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return path.endsWith(".mp4") || path.endsWith(".m3u8") ||
               path.endsWith(".webm") || path.endsWith(".mkv")
    }

    private fun extractHost(url: String): String? = try {
        java.net.URI(url).host?.lowercase()
    } catch (_: Throwable) {
        Regex("""^https?://([^/:?#]+)""", RegexOption.IGNORE_CASE)
            .find(url)?.groupValues?.getOrNull(1)?.lowercase()
    }
}