package com.aetherion.nfcwallet.nfc

import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import com.aetherion.nfcwallet.data.Capability
import com.aetherion.nfcwallet.data.CardDump
import com.aetherion.nfcwallet.data.ClassicDump
import com.aetherion.nfcwallet.data.ClassicSector
import com.aetherion.nfcwallet.data.IsoDepInfo
import com.aetherion.nfcwallet.data.NdefDump
import com.aetherion.nfcwallet.data.PagesDump
import com.aetherion.nfcwallet.data.RecordSummary
import com.aetherion.nfcwallet.data.TagFamily
import com.aetherion.nfcwallet.data.toHex
import java.io.IOException

/**
 * Reads whatever a tag will give up without breaking any protection:
 * NDEF messages, NTAG/Ultralight memory, MIFARE Classic sectors that use well-known default keys,
 * and identifying info for ISO-DEP cards (which are protected by design and stay view-only).
 */
object TagReader {

    data class Result(
        val dump: CardDump,
        val family: TagFamily,
        val capability: Capability,
        val summary: String,
        val notice: String?,
    )

    // Factory-default / public-directory keys only. No cracking, no attacks.
    private val CLASSIC_KEYS: List<ByteArray> = listOf(
        MifareClassic.KEY_DEFAULT,
        MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY,
        MifareClassic.KEY_NFC_FORUM,
        byteArrayOf(0, 0, 0, 0, 0, 0),
        byteArrayOf(0xA0.toByte(), 0xB0.toByte(), 0xC0.toByte(), 0xD0.toByte(), 0xE0.toByte(), 0xF0.toByte()),
        byteArrayOf(0xA1.toByte(), 0xB1.toByte(), 0xC1.toByte(), 0xD1.toByte(), 0xE1.toByte(), 0xF1.toByte()),
        byteArrayOf(0xB0.toByte(), 0xB1.toByte(), 0xB2.toByte(), 0xB3.toByte(), 0xB4.toByte(), 0xB5.toByte()),
        byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte()),
    )

    /** Blocking; call off the main thread. Throws IOException (incl. TagLostException) on I/O failure. */
    fun read(tag: Tag): Result {
        val techs = tag.techList.map { it.substringAfterLast('.') }
        val uid = tag.id.toHex()
        val nfcA = NfcA.get(tag)
        val atqa = nfcA?.atqa?.toHex()
        val sak = nfcA?.sak?.toInt()

        val ndef = readNdef(tag)
        var pages: PagesDump? = null
        var classic: ClassicDump? = null
        var isoDep: IsoDepInfo? = null
        var family = TagFamily.UNKNOWN
        var notice: String? = null

        when {
            "MifareClassic" in techs -> {
                family = TagFamily.CLASSIC
                classic = readClassic(tag)
                val total = classic?.sectors?.size ?: 0
                val locked = classic?.sectors?.count { !it.readable } ?: 0
                if (locked > 0) {
                    notice = "$locked of $total sectors use non-default keys and were skipped. A copy will be incomplete."
                }
            }
            "MifareUltralight" in techs -> {
                family = TagFamily.NTAG
                pages = readPages(tag)
            }
            "IsoDep" in techs -> {
                family = TagFamily.ISO_DEP
                isoDep = readIsoDep(tag)
                notice = "Smart cards (DESFire, payment, transit, passports) protect their secrets by design. " +
                    "This can be saved for reference but not copied."
            }
            "NfcV" in techs -> {
                family = TagFamily.ISO15693
                notice = "ISO 15693 tags are saved as UID only."
            }
            "NfcF" in techs -> {
                family = TagFamily.FELICA
                notice = "FeliCa cards are saved as UID only."
            }
            sak != null && sak in CLASSIC_SAKS -> {
                family = TagFamily.CLASSIC
                notice = "This looks like a MIFARE Classic card, but this phone's NFC chip can't read that format " +
                    "(common on Pixel and many Samsung models)."
            }
            "Ndef" in techs -> family = TagFamily.NDEF_TAG
        }

        val capability = when {
            pages != null && pages.pagesHex.isNotEmpty() -> Capability.PAGES_CLONABLE
            classic != null && classic.sectors.any { it.readable } -> Capability.CLASSIC_DATA
            ndef?.messageHex != null -> Capability.NDEF_REWRITABLE
            else -> Capability.VIEW_ONLY
        }

        val summary = when {
            pages != null -> "${pages.chip} · ${(pages.pageCount - 4) * 4} bytes"
            classic != null -> "${classicName(classic.sizeBytes)} · ${classic.sectors.count { it.readable }}/${classic.sectors.size} sectors read"
            ndef != null -> "${family.label} · NDEF, ${ndef.records.size} record(s)"
            else -> family.label
        }

        val dump = CardDump(
            uidHex = uid,
            techList = techs,
            atqaHex = atqa,
            sak = sak,
            ndef = ndef,
            pages = pages,
            classic = classic,
            isoDep = isoDep,
        )
        return Result(dump, family, capability, summary, notice)
    }

    private val CLASSIC_SAKS = setOf(0x08, 0x09, 0x18, 0x28, 0x38, 0x88)

    private fun classicName(sizeBytes: Int) = when (sizeBytes) {
        MifareClassic.SIZE_MINI -> "MIFARE Classic Mini"
        MifareClassic.SIZE_1K -> "MIFARE Classic 1K"
        MifareClassic.SIZE_2K -> "MIFARE Classic 2K"
        MifareClassic.SIZE_4K -> "MIFARE Classic 4K"
        else -> "MIFARE Classic"
    }

    // ---- NDEF -------------------------------------------------------------------------------

    private fun readNdef(tag: Tag): NdefDump? {
        val ndef = Ndef.get(tag) ?: return null
        return try {
            ndef.connect()
            val message = ndef.ndefMessage ?: ndef.cachedNdefMessage
            NdefDump(
                type = ndef.type,
                maxSize = ndef.maxSize,
                isWritable = ndef.isWritable,
                messageHex = message?.toByteArray()?.takeIf { it.isNotEmpty() }?.toHex(),
                records = message?.records?.map(::summarize).orEmpty(),
            )
        } catch (e: TagLostException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            runCatching { ndef.close() }
        }
    }

    private fun summarize(record: NdefRecord): RecordSummary {
        val type = record.type
        return when {
            record.tnf == NdefRecord.TNF_WELL_KNOWN && type.contentEquals(NdefRecord.RTD_TEXT) ->
                RecordSummary("Text", decodeText(record.payload).take(160))
            record.tnf == NdefRecord.TNF_WELL_KNOWN && type.contentEquals(NdefRecord.RTD_URI) ->
                RecordSummary("URI", (record.toUri()?.toString() ?: "").take(160))
            record.tnf == NdefRecord.TNF_MIME_MEDIA ->
                RecordSummary("MIME", record.toMimeType() ?: "unknown")
            else -> RecordSummary("Record", "${record.payload.size} bytes")
        }
    }

    private fun decodeText(payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        val langLen = payload[0].toInt() and 0x3F
        val charset = if ((payload[0].toInt() and 0x80) == 0) Charsets.UTF_8 else Charsets.UTF_16
        val start = 1 + langLen
        return if (start <= payload.size) String(payload, start, payload.size - start, charset) else ""
    }

    // ---- NTAG / Ultralight ------------------------------------------------------------------

    private fun readPages(tag: Tag): PagesDump? {
        val mu = MifareUltralight.get(tag) ?: return null
        return try {
            mu.connect()
            val layout = probePageLayout(mu)
            val pages = ArrayList<String>(layout.endPage)
            var p = 0
            while (p < layout.endPage) {
                val chunk = mu.readPages(p) // 4 pages = 16 bytes
                for (i in 0 until 4) {
                    if (p + i < layout.endPage) pages += chunk.copyOfRange(i * 4, i * 4 + 4).toHex()
                }
                p += 4
            }
            PagesDump(chip = layout.chip, pageCount = layout.endPage, pagesHex = pages)
        } finally {
            runCatching { mu.close() }
        }
    }

    // ---- MIFARE Classic ---------------------------------------------------------------------

    private fun readClassic(tag: Tag): ClassicDump? {
        val mc = MifareClassic.get(tag) ?: return null
        return try {
            mc.connect()
            ClassicDump(sizeBytes = mc.size, sectors = (0 until mc.sectorCount).map { readSector(mc, it) })
        } finally {
            runCatching { mc.close() }
        }
    }

    private fun readSector(mc: MifareClassic, sector: Int): ClassicSector {
        for (key in CLASSIC_KEYS) {
            if (mc.authenticateSectorWithKeyA(sector, key)) {
                return ClassicSector(sector, keyAHex = key.toHex(), blocksHex = readBlocks(mc, sector))
            }
        }
        for (key in CLASSIC_KEYS) {
            if (mc.authenticateSectorWithKeyB(sector, key)) {
                return ClassicSector(sector, keyBHex = key.toHex(), blocksHex = readBlocks(mc, sector))
            }
        }
        return ClassicSector(sector)
    }

    private fun readBlocks(mc: MifareClassic, sector: Int): List<String> {
        val first = mc.sectorToBlock(sector)
        return (0 until mc.getBlockCountInSector(sector)).map { i ->
            try {
                mc.readBlock(first + i).toHex()
            } catch (e: TagLostException) {
                throw e
            } catch (e: IOException) {
                ""
            }
        }
    }

    // ---- ISO-DEP ----------------------------------------------------------------------------

    private fun readIsoDep(tag: Tag): IsoDepInfo? {
        val iso = IsoDep.get(tag) ?: return null
        return IsoDepInfo(
            historicalBytesHex = iso.historicalBytes?.toHex(),
            hiLayerResponseHex = iso.hiLayerResponse?.toHex(),
        )
    }
}

/** Where user memory ends on an NTAG / Ultralight, and a friendly chip name. */
internal data class PageLayout(val endPage: Int, val chip: String)

/**
 * Works out how many pages hold user data. Uses GET_VERSION (NTAG21x, Ultralight EV1) when the tag
 * answers it, otherwise the NDEF capability container, otherwise assumes a plain 16-page Ultralight.
 * [mu] must already be connected.
 */
internal fun probePageLayout(mu: MifareUltralight): PageLayout {
    var version: ByteArray? = null
    try {
        version = mu.transceive(byteArrayOf(0x60))
    } catch (e: TagLostException) {
        throw e
    } catch (e: IOException) {
        // Tags without GET_VERSION NAK it and fall idle; reselect before reading.
        runCatching { mu.close(); mu.connect() }
    }

    val storage = version?.takeIf { it.size >= 8 }?.get(6)?.toInt()?.and(0xFF)
    val (userBytes, chip) = when (storage) {
        0x0B -> 48 to "Ultralight EV1 (MF0UL11)"
        0x0E -> 128 to "Ultralight EV1 (MF0UL21)"
        0x0F -> 144 to "NTAG213"
        0x11 -> 504 to "NTAG215"
        0x13 -> 888 to "NTAG216"
        else -> null to "NTAG / Ultralight"
    }
    if (userBytes != null) return PageLayout(endPage = 4 + userBytes / 4, chip = chip)

    val cc = mu.readPages(0).copyOfRange(12, 16)
    val ccMagic = cc[0].toInt() and 0xFF
    val ccSize = cc[2].toInt() and 0xFF
    val end = if (ccMagic == 0xE1 && ccSize > 0) 4 + ccSize * 2 else 16
    return PageLayout(endPage = minOf(end, 256), chip = chip)
}
