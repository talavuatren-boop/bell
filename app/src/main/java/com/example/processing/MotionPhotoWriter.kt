package com.example.processing

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Packages a still JPEG and its companion MP4 motion clip into a single
 * Android / Google / Xiaomi Motion Photo JPEG (XMP MotionPhoto v2 Container
 * Directory + legacy MicroVideo tags in APP1, plus raw MP4 appended after JPEG EOI).
 *
 * System galleries (Google Photos, HyperOS/MIUI Gallery, Samsung Gallery)
 * recognize this single .jpg as a Live / Motion Photo without any stray .mp4
 * appearing in MediaStore.Video.
 */
object MotionPhotoWriter {

    private const val XMP_HEADER = "http://ns.adobe.com/xap/1.0/\u0000"

    fun embedMotionPhoto(
        jpegBytes: ByteArray,
        mp4Bytes: ByteArray,
        presentationTimestampUs: Long = 0L
    ): ByteArray {
        if (jpegBytes.size < 4 || mp4Bytes.isEmpty()) return jpegBytes
        if ((jpegBytes[0].toInt() and 0xFF) != 0xFF || (jpegBytes[1].toInt() and 0xFF) != 0xD8) {
            return jpegBytes
        }

        val mp4Len = mp4Bytes.size
        val safeTs = presentationTimestampUs.coerceAtLeast(0L)
        val xmpXml = buildMotionPhotoXmp(mp4ByteLength = mp4Len, presentationTsUs = safeTs)
        val xmpHeaderBytes = XMP_HEADER.toByteArray(StandardCharsets.US_ASCII)
        val xmpPayloadBytes = xmpXml.toByteArray(StandardCharsets.UTF_8)
        val xmpSegmentPayloadLen = xmpHeaderBytes.size + xmpPayloadBytes.size
        val xmpSegmentLenField = xmpSegmentPayloadLen + 2
        if (xmpSegmentLenField > 0xFFFF) return jpegBytes

        val out = ByteArrayOutputStream(jpegBytes.size + xmpSegmentLenField + 2 + mp4Len)
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
        // vendor padding so MP4 ftyp box starts immediately after FF D9.
        if (pos < jpegBytes.size) {
            val eoiEnd = findOuterJpegEoiEnd(jpegBytes, pos)
            out.write(jpegBytes, pos, eoiEnd - pos)
        }
        out.write(mp4Bytes)
        return out.toByteArray()
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

    private fun buildMotionPhotoXmp(mp4ByteLength: Int, presentationTsUs: Long): String {
        // Conforms to Android Motion Photo Format 1.0 (GCamera + Container:Directory without
        // invalid Item:Padding on secondary item) while keeping MicroVideo v1 + MiCamera tags for
        // Google Photos, Xiaomi HyperOS/MIUI Gallery, and OEM Android galleries.
        return """<?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
 <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
  <rdf:Description rdf:about=""
    xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
    xmlns:MiCamera="http://ns.xiaomi.com/photos/1.0/camera/"
    xmlns:Container="http://ns.google.com/photos/1.0/container/"
    xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
    GCamera:MotionPhoto="1"
    GCamera:MotionPhotoVersion="1"
    GCamera:MotionPhotoPresentationTimestampUs="$presentationTsUs"
    GCamera:MicroVideo="1"
    GCamera:MicroVideoVersion="1"
    GCamera:MicroVideoOffset="$mp4ByteLength"
    GCamera:MicroVideoPresentationTimestampUs="$presentationTsUs"
    MiCamera:XMPMeta="&lt;?xml version='1.0' encoding='UTF-8' standalone='yes' ?&gt;">
   <Container:Directory>
    <rdf:Seq>
     <rdf:li rdf:parseType="Resource">
      <Container:Item
        Item:Mime="image/jpeg"
        Item:Semantic="Primary"
        Item:Length="0"
        Item:Padding="0"/>
     </rdf:li>
     <rdf:li rdf:parseType="Resource">
      <Container:Item
        Item:Mime="video/mp4"
        Item:Semantic="MotionPhoto"
        Item:Length="$mp4ByteLength"/>
     </rdf:li>
    </rdf:Seq>
   </Container:Directory>
  </rdf:Description>
 </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>"""
    }
}
