package com.example.processing

import android.os.Build
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Packages a still JPEG and its companion MP4 motion clip into a single
 * cross-OEM Android Motion Photo JPEG:
 * - Standard Android Motion Photo 1.0 (`Camera:` + `Container:Directory`)
 * - Xiaomi MIUI / HyperOS (`GCamera:MicroVideo` + `MiCamera:XMPMeta`)
 * - OPPO / Realme / OnePlus ColorOS (`OpCamera:` O-Live tags)
 * - Samsung One UI Gallery (`MotionPhoto_Data` block + `SEFH`...`SEFT` trailer)
 *
 * System galleries (Google Photos, Samsung Gallery, HyperOS/MIUI Gallery, ColorOS Photos)
 * recognize this single `.jpg` as a native Live / Motion Photo without any stray `.mp4`
 * appearing in MediaStore.Video.
 */
object MotionPhotoWriter {

    private const val XMP_HEADER = "http://ns.adobe.com/xap/1.0/\u0000"
    private const val SAMSUNG_SEF_KEY = "MotionPhoto_Data"
    private const val SAMSUNG_SEF_DATA_TYPE = 0x0A30
    private const val SAMSUNG_SEF_VERSION = 106
    private const val SAMSUNG_SEF_HEADER_PREFIX_LEN = 24 // 2(subType) + 2(type) + 4(nameLen) + 16("MotionPhoto_Data")
    private const val SAMSUNG_SEF_TRAILER_LEN = 32       // "SEFH"(4) + ver(4) + cnt(4) + entry(12) + sefSize(4) + "SEFT"(4)

    fun isSamsungDevice(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        val b = Build.BRAND.lowercase()
        return m.contains("samsung") || b.contains("samsung")
    }

    fun isXiaomiDevice(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        val b = Build.BRAND.lowercase()
        return m.contains("xiaomi") || b.contains("xiaomi") ||
            m.contains("redmi") || b.contains("redmi") ||
            m.contains("poco") || b.contains("poco")
    }

    fun isOplusDevice(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        val b = Build.BRAND.lowercase()
        return m.contains("oppo") || b.contains("oppo") ||
            m.contains("realme") || b.contains("realme") ||
            m.contains("oneplus") || b.contains("oneplus") ||
            m.contains("oplus")
    }

    fun embedMotionPhoto(
        jpegBytes: ByteArray,
        mp4Bytes: ByteArray,
        presentationTimestampUs: Long = 0L,
        includeSamsungSef: Boolean = isSamsungDevice(),
        useLegacyGCameraPrefix: Boolean = isXiaomiDevice()
    ): ByteArray {
        if (jpegBytes.size < 4 || mp4Bytes.isEmpty()) return jpegBytes
        if ((jpegBytes[0].toInt() and 0xFF) != 0xFF || (jpegBytes[1].toInt() and 0xFF) != 0xD8) {
            return jpegBytes
        }

        val mp4Len = mp4Bytes.size
        val containerMotionLen = if (includeSamsungSef) mp4Len + SAMSUNG_SEF_TRAILER_LEN else mp4Len
        val primaryPadding = if (includeSamsungSef) SAMSUNG_SEF_HEADER_PREFIX_LEN else 0
        val safeTs = presentationTimestampUs.coerceAtLeast(0L)
        val xmpXml = buildMotionPhotoXmp(
            mp4ByteLength = mp4Len,
            containerMotionLength = containerMotionLen,
            primaryPadding = primaryPadding,
            presentationTsUs = safeTs,
            useLegacyGCameraPrefix = useLegacyGCameraPrefix
        )
        val xmpHeaderBytes = XMP_HEADER.toByteArray(StandardCharsets.US_ASCII)
        val xmpPayloadBytes = xmpXml.toByteArray(StandardCharsets.UTF_8)
        val xmpSegmentPayloadLen = xmpHeaderBytes.size + xmpPayloadBytes.size
        val xmpSegmentLenField = xmpSegmentPayloadLen + 2
        if (xmpSegmentLenField > 0xFFFF) return jpegBytes

        val extraTrailerSize = if (includeSamsungSef) {
            SAMSUNG_SEF_HEADER_PREFIX_LEN + SAMSUNG_SEF_TRAILER_LEN
        } else {
            0
        }
        val out = ByteArrayOutputStream(jpegBytes.size + xmpSegmentLenField + 2 + mp4Len + extraTrailerSize)
        // SOI
        out.write(0xFF)
        out.write(0xD8)

        var pos = 2
        var xmpWritten = false

        while (pos + 1 < jpegBytes.size) {
            if ((jpegBytes[pos].toInt() and 0xFF) != 0xFF) {
                break
            }
            while (pos < jpegBytes.size && (jpegBytes[pos].toInt() and 0xFF) == 0xFF) {
                pos++
            }
            if (pos >= jpegBytes.size) break
            val marker = jpegBytes[pos].toInt() and 0xFF
            pos++

            if (marker == 0x00 || marker == 0xD8 || (marker in 0xD0..0xD7)) {
                out.write(0xFF)
                out.write(marker)
                continue
            }

            if (marker == 0xD9) {
                if (!xmpWritten) {
                    writeXmpApp1Segment(out, xmpSegmentLenField, xmpHeaderBytes, xmpPayloadBytes)
                    xmpWritten = true
                }
                out.write(0xFF)
                out.write(0xD9)
                pos = jpegBytes.size
                break
            }

            if (marker == 0xDA) {
                if (!xmpWritten) {
                    writeXmpApp1Segment(out, xmpSegmentLenField, xmpHeaderBytes, xmpPayloadBytes)
                    xmpWritten = true
                }
                out.write(0xFF)
                out.write(0xDA)
                if (pos + 2 <= jpegBytes.size) {
                    val sosLen = ((jpegBytes[pos].toInt() and 0xFF) shl 8) or (jpegBytes[pos + 1].toInt() and 0xFF)
                    if (sosLen >= 2 && pos + sosLen <= jpegBytes.size) {
                        out.write(jpegBytes, pos, sosLen)
                        pos += sosLen
                    }
                }
                break
            }

            if (pos + 2 > jpegBytes.size) break
            val segLen = ((jpegBytes[pos].toInt() and 0xFF) shl 8) or (jpegBytes[pos + 1].toInt() and 0xFF)
            if (segLen < 2 || pos + segLen > jpegBytes.size) {
                if (!xmpWritten) {
                    writeXmpApp1Segment(out, xmpSegmentLenField, xmpHeaderBytes, xmpPayloadBytes)
                    xmpWritten = true
                }
                out.write(0xFF)
                out.write(marker)
                out.write(jpegBytes, pos, jpegBytes.size - pos)
                pos = jpegBytes.size
                break
            }

            val isExistingXmp = marker == 0xE1 &&
                segLen >= 2 + xmpHeaderBytes.size &&
                matchesAsciiPrefix(jpegBytes, pos + 2, xmpHeaderBytes)

            if (!isExistingXmp) {
                out.write(0xFF)
                out.write(marker)
                out.write(jpegBytes, pos, segLen)
            }
            pos += segLen

            if (!xmpWritten && (marker == 0xE1 || marker == 0xE0) && !isExistingXmp) {
                writeXmpApp1Segment(out, xmpSegmentLenField, xmpHeaderBytes, xmpPayloadBytes)
                xmpWritten = true
            }
        }

        // Write entropy scan(s) up to exact outer JPEG EOI (0xFF 0xD9), trimming any trailing
        // vendor padding so MP4 or SEF data starts immediately after FF D9.
        if (pos < jpegBytes.size) {
            val eoiEnd = findOuterJpegEoiEnd(jpegBytes, pos)
            out.write(jpegBytes, pos, eoiEnd - pos)
        }

        if (includeSamsungSef) {
            writeSamsungMotionPhotoSefPayload(out, mp4Bytes)
        } else {
            out.write(mp4Bytes)
        }
        return out.toByteArray()
    }

    /**
     * Appends Samsung SEF (`MotionPhoto_Data` header + MP4 + `SEFH`...`SEFT` footer) so native
     * Samsung One UI Gallery recognizes and plays the Motion Photo while `Container:Directory`
     * still points Google Photos / Media3 directly to `mp4Bytes`.
     */
    private fun writeSamsungMotionPhotoSefPayload(out: ByteArrayOutputStream, mp4Bytes: ByteArray) {
        val keyBytes = SAMSUNG_SEF_KEY.toByteArray(StandardCharsets.US_ASCII)
        val blockLen = SAMSUNG_SEF_HEADER_PREFIX_LEN + mp4Bytes.size

        // 24-byte data entry header immediately preceding raw MP4
        val prefix = ByteBuffer.allocate(SAMSUNG_SEF_HEADER_PREFIX_LEN).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0)                              // subType = 0x0000
            putShort(SAMSUNG_SEF_DATA_TYPE.toShort()) // dataType = 0x0A30 LE
            putInt(keyBytes.size)                    // ASCII key name length = 16
            put(keyBytes)                            // "MotionPhoto_Data"
        }.array()
        out.write(prefix)
        out.write(mp4Bytes)

        // 32-byte SEFH..SEFT footer
        val sefSize = 24 // "SEFH"(4) + version(4) + count(4) + 1*entry(12)
        val footer = ByteBuffer.allocate(SAMSUNG_SEF_TRAILER_LEN).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("SEFH".toByteArray(StandardCharsets.US_ASCII))
            putInt(SAMSUNG_SEF_VERSION)
            putInt(1)                                // entry count
            putShort(0)                              // entry subType
            putShort(SAMSUNG_SEF_DATA_TYPE.toShort()) // entry dataType = 0x0A30
            putInt(blockLen)                         // distance backwards from SEFH to MotionPhoto_Data start
            putInt(blockLen)                         // total byte length of MotionPhoto_Data entry
            putInt(sefSize)                          // 24 -> EOF - 8 - 24 = SEFH start
            put("SEFT".toByteArray(StandardCharsets.US_ASCII))
        }.array()
        out.write(footer)
    }

    /**
     * Decodes a Samsung SEF trailer from the end of a file and returns the exact MP4 byte range
     * (`startInclusive..endInclusive`), or `null` if no valid Samsung `SEFT`/`SEFH` block is present.
     */
    fun extractSamsungSefMp4Range(motionPhotoBytes: ByteArray): IntRange? {
        val n = motionPhotoBytes.size
        if (n < SAMSUNG_SEF_TRAILER_LEN) return null
        val tailTag = String(motionPhotoBytes, n - 4, 4, StandardCharsets.US_ASCII)
        if (tailTag != "SEFT") return null
        val sefSize = ByteBuffer.wrap(motionPhotoBytes, n - 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val sefhStart = n - 8 - sefSize
        if (sefhStart < 0 || sefhStart + 12 > n - 8) return null
        val headTag = String(motionPhotoBytes, sefhStart, 4, StandardCharsets.US_ASCII)
        if (headTag != "SEFH") return null

        val headerBuf = ByteBuffer.wrap(motionPhotoBytes, sefhStart + 4, sefSize - 4).order(ByteOrder.LITTLE_ENDIAN)
        /* version */ headerBuf.int
        val count = headerBuf.int
        if (count <= 0 || sefhStart + 12 + count * 12 > n - 8) return null

        for (idx in 0 until count) {
            /* subType */ headerBuf.short
            val dataType = headerBuf.short.toInt() and 0xFFFF
            val backOffset = headerBuf.int
            val dataLen = headerBuf.int
            if (dataType == SAMSUNG_SEF_DATA_TYPE && backOffset > 0 && dataLen > 8) {
                val entryStart = sefhStart - backOffset
                if (entryStart < 0 || entryStart + 8 > sefhStart) return null
                val nameLen = ByteBuffer.wrap(motionPhotoBytes, entryStart + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                if (nameLen <= 0 || entryStart + 8 + nameLen > sefhStart) return null
                val keyName = String(motionPhotoBytes, entryStart + 8, nameLen, StandardCharsets.US_ASCII)
                if (keyName == SAMSUNG_SEF_KEY) {
                    val mp4Start = entryStart + 8 + nameLen
                    val mp4End = entryStart + dataLen - 1
                    if (mp4Start in 0..mp4End && mp4End < sefhStart) {
                        return mp4Start..mp4End
                    }
                }
            }
        }
        return null
    }

    /**
     * Walks entropy-coded JPEG stream(s) from [startPos] skipping stuffed 0xFF 0x00 and RST0..RST7
     * markers until outer EOI (0xFF 0xD9) is reached. Falls back safely to [jpegBytes.size] if
     * the JPEG does not follow standard framing.
     */
    private fun findOuterJpegEoiEnd(jpegBytes: ByteArray, startPos: Int): Int {
        var i = startPos
        val n = jpegBytes.size
        while (i + 1 < n) {
            val b = jpegBytes[i].toInt() and 0xFF
            if (b != 0xFF) {
                i++
                continue
            }
            while (i < n && (jpegBytes[i].toInt() and 0xFF) == 0xFF) {
                i++
            }
            if (i >= n) break
            val marker = jpegBytes[i].toInt() and 0xFF
            i++
            when {
                marker == 0x00 || marker == 0x01 || marker in 0xD0..0xD7 -> continue
                marker == 0xD9 -> return i // right after 0xFF 0xD9
                else -> {
                    // Progressive/multi-scan header (SOS/DHT/DQT/DRI etc.): skip segment payload
                    if (i + 2 > n) break
                    val segLen = ((jpegBytes[i].toInt() and 0xFF) shl 8) or (jpegBytes[i + 1].toInt() and 0xFF)
                    if (segLen < 2 || i + segLen > n) break
                    i += segLen
                }
            }
        }
        return n
    }

    fun extractEmbeddedMp4Offset(motionPhotoBytes: ByteArray): Int? {
        val text = String(
            motionPhotoBytes,
            0,
            minOf(motionPhotoBytes.size, 64 * 1024),
            StandardCharsets.UTF_8
        )
        val itemLenMatch = Regex("""Item:Semantic="MotionPhoto"[^>]*Item:Length="(\d+)"""", RegexOption.DOT_MATCHES_ALL)
            .find(text)
        val microMatch = Regex("""MicroVideoOffset="(\d+)"""").find(text)
        val offset = itemLenMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: microMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: return null
        return offset.takeIf { it in 1..motionPhotoBytes.size }
    }

    private fun writeXmpApp1Segment(
        out: ByteArrayOutputStream,
        segLen: Int,
        headerBytes: ByteArray,
        payloadBytes: ByteArray
    ) {
        out.write(0xFF)
        out.write(0xE1)
        out.write((segLen shr 8) and 0xFF)
        out.write(segLen and 0xFF)
        out.write(headerBytes)
        out.write(payloadBytes)
    }

    private fun matchesAsciiPrefix(data: ByteArray, offset: Int, prefix: ByteArray): Boolean {
        if (offset + prefix.size > data.size) return false
        for (i in prefix.indices) {
            if (data[offset + i] != prefix[i]) return false
        }
        return true
    }

    private fun buildMotionPhotoXmp(
        mp4ByteLength: Int,
        containerMotionLength: Int,
        primaryPadding: Int,
        presentationTsUs: Long,
        useLegacyGCameraPrefix: Boolean
    ): String {
        val p = if (useLegacyGCameraPrefix) "GCamera" else "Camera"
        // Uses canonical `Camera:` for Android Motion Photo 1.0 (or `GCamera:` on Xiaomi MIUI/HyperOS)
        // plus OpCamera O-Live attributes and MiCamera:XMPMeta without duplicate URI namespace bindings.
        return """<?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
 <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
  <rdf:Description rdf:about=""
    xmlns:$p="http://ns.google.com/photos/1.0/camera/"
    xmlns:OpCamera="http://ns.oplus.com/photos/1.0/camera/"
    xmlns:MiCamera="http://ns.xiaomi.com/photos/1.0/camera/"
    xmlns:Container="http://ns.google.com/photos/1.0/container/"
    xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
    $p:MotionPhoto="1"
    $p:MotionPhotoVersion="1"
    $p:MotionPhotoPresentationTimestampUs="$presentationTsUs"
    $p:MicroVideo="1"
    $p:MicroVideoVersion="1"
    $p:MicroVideoOffset="$containerMotionLength"
    $p:MicroVideoPresentationTimestampUs="$presentationTsUs"
    OpCamera:MotionPhotoOwner="oplus"
    OpCamera:OLivePhotoVersion="2"
    OpCamera:MotionPhotoPrimaryPresentationTimestampUs="$presentationTsUs"
    OpCamera:VideoLength="$mp4ByteLength"
    MiCamera:XMPMeta="&lt;?xml version='1.0' encoding='UTF-8' standalone='yes' ?&gt;">
   <Container:Directory>
    <rdf:Seq>
     <rdf:li rdf:parseType="Resource">
      <Container:Item
        Item:Mime="image/jpeg"
        Item:Semantic="Primary"
        Item:Length="0"
        Item:Padding="$primaryPadding"/>
     </rdf:li>
     <rdf:li rdf:parseType="Resource">
      <Container:Item
        Item:Mime="video/mp4"
        Item:Semantic="MotionPhoto"
        Item:Length="$containerMotionLength"/>
     </rdf:li>
    </rdf:Seq>
   </Container:Directory>
  </rdf:Description>
 </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>"""
    }
}

