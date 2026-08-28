package de.gesturelink.app

object AppConfig {
    const val productName = BuildConfig.PRODUCT_NAME
    const val protocolVersion = 1
    const val defaultPort = 39_827
    const val maxMessageBytes = 65_536
    const val pointerSendHz = 60
    const val heartbeatMs = 250L
    const val watchdogMs = 750L
}

