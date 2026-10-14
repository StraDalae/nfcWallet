package com.aetherion.nfcwallet.nfc

import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import com.aetherion.nfcwallet.data.CardDump
import com.aetherion.nfcwallet.data.ClassicDump
import com.aetherion.nfcwallet.data.PagesDump
import com.aetherion.nfcwallet.data.hexToBytes
import java.io.IOException

/**
 * Writes a saved card onto a blank tag, then reads it back to verify.
 *
 * What is deliberately NOT written:
 *  - UID / manufacturer pages (read-only on genuine tags)
 *  - NTAG/Ultralight lock bytes, OTP and config pages (could permanently lock or brick the target)
 *  - MIFARE Classic sector trailers (would overwrite the target's keys and access bits)
 *  - MIFARE Classic block 0, unless the user says the target is a "magic" card
 */
object TagWriter {

    sealed interface Outcome {
        data class Success(val verified: Boolean, val detail: String) : Outcome
        data class Failure(val message: String) : Outcome
    }

    /** Blocking; call off the main thread. */
    fun write(tag: Tag, dump: CardDump, magicBlock0: Boolean = false): Outcome = try {
        val pages = dump.pages
        val classic = dump.classic
        val ndefHex = dump.ndef?.messageHex
        when {
            pages != null -> writePages(tag, pages)
            classic != null -> writeClassic(tag, classic, magicBlock0)
            ndefHex != null -> writeNdef(tag, ndefHex)
            else -> Outcome.Failure("This card is view-only and can't be written to a tag.")
        }
    } catch (e: TagLostException) {
        Outcome.Failure("The tag moved away. Hold it steady against the back of the phone and try again.")
    } catch (e: IOException) {
        Outcome.Failure("Write failed (${e.message ?: "I/O error"}). The tag may be locked or the wrong type.")
    } catch (e: Exception) {
        Outcome.Failure(e.message ?: "Unexpected error while writing.")
    }

    // ---- NTAG / Ultralight ------------------------------------------------------------------

    private fun writePages(tag: Tag, src: PagesDump): Outcome {
        val mu = MifareUltralight.get(tag)
            ?: return Outcome.Failure("That isn't an NTAG/Ultralight tag. This card needs a blank NTAG or Ultralight.")
        mu.connect()
        try {
            val target = probePageLayout(mu)
            if (target.endPage < src.pageCount) {
                return Outcome.Failure(
                    "Target is too small: ${(target.endPage - 4) * 4} bytes, need ${(src.pageCount - 4) * 4}.",
                )
            }
            // Pages 0-3 are UID / lock / capability container: never written. User memory starts at page 4.
            for (page in 4 until src.pageCount) {
                mu.writePage(page, src.pagesHex[page].hexToBytes())
            }
            var verified = true
            var p = 4
            while (p < src.pageCount) {
                val chunk = mu.readPages(p)
                for (i in 0 until 4) {
                    val page = p + i
                    if (page < src.pageCount && !chunk.copyOfRange(i * 4, i * 4 + 4).contentEquals(src.pagesHex[page].hexToBytes())) {
                        verified = false
                    }
                }
                p += 4
            }
            return Outcome.Success(verified, "Copied ${(src.pageCount - 4) * 4} bytes to ${target.chip}.")
        } finally {
            runCatching { mu.close() }
        }
    }

    // ---- NDEF -------------------------------------------------------------------------------

    private fun writeNdef(tag: Tag, messageHex: String): Outcome {
        val bytes = messageHex.hexToBytes()
        val message = NdefMessage(bytes)

        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.connect()
            try {
                if (!ndef.isWritable) return Outcome.Failure("That tag is read-only.")
                if (ndef.maxSize < bytes.size) {
                    return Outcome.Failure("Target is too small: ${ndef.maxSize} bytes, need ${bytes.size}.")
                }
                ndef.writeNdefMessage(message)
                val verified = ndef.ndefMessage?.toByteArray()?.contentEquals(bytes) == true
                return Outcome.Success(verified, "Wrote ${bytes.size} bytes of NDEF data.")
            } finally {
                runCatching { ndef.close() }
            }
        }

        val formatable = NdefFormatable.get(tag)
            ?: return Outcome.Failure("That tag doesn't support NDEF. Use a blank NTAG sticker or card.")
        formatable.connect()
        try {
            formatable.format(message)
        } finally {
            runCatching { formatable.close() }
        }
        return Outcome.Success(false, "Formatted the tag and wrote ${bytes.size} bytes. Scan it again to confirm.")
    }

    // ---- MIFARE Classic ---------------------------------------------------------------------

    private fun writeClassic(tag: Tag, src: ClassicDump, magicBlock0: Boolean): Outcome {
        val mc = MifareClassic.get(tag)
            ?: return Outcome.Failure("That isn't a MIFARE Classic card, or this phone can't write that format.")
        mc.connect()
        try {
            if (mc.size != src.sizeBytes) {
                return Outcome.Failure("Card size doesn't match (source ${src.sizeBytes} bytes, target ${mc.size}).")
            }
            var written = 0
            var skippedSectors = 0
            var verified = true

            for (sector in src.sectors) {
                val blocks = sector.blocksHex
                if (blocks == null || !authDefault(mc, sector.index)) {
                    skippedSectors++
                    continue
                }
                val first = mc.sectorToBlock(sector.index)
                // The last block of every sector is the trailer (keys + access bits): leave it alone.
                for (i in 0 until blocks.lastIndex) {
                    val blockIndex = first + i
                    val hex = blocks[i]
                    if (hex.isEmpty() || blockIndex == 0) continue
                    val data = hex.hexToBytes()
                    mc.writeBlock(blockIndex, data)
                    if (!mc.readBlock(blockIndex).contentEquals(data)) verified = false
                    written++
                }
            }

            var detail = "Wrote $written blocks"
            if (skippedSectors > 0) detail += ", skipped $skippedSectors sectors (not readable on the source, or the target isn't on default keys)"
            detail += "."

            if (magicBlock0) {
                val block0 = src.sectors.firstOrNull()?.blocksHex?.firstOrNull().orEmpty()
                if (block0.isNotEmpty() && authDefault(mc, 0)) {
                    try {
                        mc.writeBlock(0, block0.hexToBytes())
                        detail += " Block 0 (UID) written."
                    } catch (e: TagLostException) {
                        throw e
                    } catch (e: IOException) {
                        detail += " Block 0 was rejected: the target isn't a magic card, so the UID differs."
                    }
                }
            }
            return Outcome.Success(verified && skippedSectors == 0, detail)
        } finally {
            runCatching { mc.close() }
        }
    }

    private fun authDefault(mc: MifareClassic, sector: Int): Boolean =
        mc.authenticateSectorWithKeyA(sector, MifareClassic.KEY_DEFAULT) ||
            mc.authenticateSectorWithKeyB(sector, MifareClassic.KEY_DEFAULT)
}
