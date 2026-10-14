package com.aetherion.nfcwallet.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val VaultJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
enum class TagFamily(val label: String) {
    NTAG("NTAG / Ultralight"),
    CLASSIC("MIFARE Classic"),
    ISO_DEP("ISO-DEP smart card"),
    ISO15693("ISO 15693 tag"),
    FELICA("FeliCa"),
    NDEF_TAG("NFC tag"),
    UNKNOWN("NFC tag"),
}

/** What the app can honestly do with a saved card. */
@Serializable
enum class Capability(val label: String, val description: String) {
    NDEF_REWRITABLE(
        "Rewritable",
        "Its stored text/link data can be written to a blank NFC tag.",
    ),
    PAGES_CLONABLE(
        "Cloneable",
        "Its memory can be copied to a blank NTAG/Ultralight tag. The new tag keeps its own UID.",
    ),
    CLASSIC_DATA(
        "Data copy",
        "Its data blocks can be copied to a blank MIFARE Classic card. Copying the UID too needs a \"magic\" card.",
    ),
    VIEW_ONLY(
        "View-only",
        "Protected by design (encrypted or challenge-response), so it can't be copied.",
    ),
}

/**
 * Everything the wallet list needs. Encrypted with the META key; never stored in plaintext.
 */
@Serializable
data class CardMeta(
    val name: String,
    val colorIndex: Int,
    val iconKey: String,
    val note: String,
    val family: TagFamily,
    val capability: Capability,
    val summary: String,
    val createdAt: Long,
    val lastUsedAt: Long? = null,
)

/**
 * Raw tag contents. Encrypted with the DUMP key; only decrypted on reveal / copy.
 */
@Serializable
data class CardDump(
    val uidHex: String,
    val techList: List<String>,
    val atqaHex: String? = null,
    val sak: Int? = null,
    val ndef: NdefDump? = null,
    val pages: PagesDump? = null,
    val classic: ClassicDump? = null,
    val isoDep: IsoDepInfo? = null,
)

@Serializable
data class NdefDump(
    val type: String,
    val maxSize: Int,
    val isWritable: Boolean,
    val messageHex: String? = null,
    val records: List<RecordSummary> = emptyList(),
)

@Serializable
data class RecordSummary(val kind: String, val preview: String)

/** NTAG / Ultralight memory, 4 bytes per page, starting at page 0. */
@Serializable
data class PagesDump(
    val chip: String,
    val pageCount: Int,
    val pagesHex: List<String>,
)

@Serializable
data class ClassicDump(
    val sizeBytes: Int,
    val sectors: List<ClassicSector>,
)

@Serializable
data class ClassicSector(
    val index: Int,
    val keyAHex: String? = null,
    val keyBHex: String? = null,
    /** One hex string per block (trailer last); empty string = block unreadable. Null = sector locked. */
    val blocksHex: List<String>? = null,
) {
    val readable: Boolean get() = blocksHex != null
}

@Serializable
data class IsoDepInfo(
    val historicalBytesHex: String? = null,
    val hiLayerResponseHex: String? = null,
)

/** A decrypted list entry held in memory only while the wallet is unlocked. */
data class WalletCard(val id: String, val meta: CardMeta)
