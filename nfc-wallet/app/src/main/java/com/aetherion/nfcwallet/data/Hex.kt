package com.aetherion.nfcwallet.data

fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }

fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "Odd-length hex" }
    return ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}

/** "04A1B2C3" -> "04:A1:B2:C3" */
fun String.colonSeparated(): String = chunked(2).joinToString(":")
