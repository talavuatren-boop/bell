package com.example.processing

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Packages a still JPEG and its companion MP4 motion clip into a single
 * Android / Google / Xiaomi Motion Photo JPEG (XMP MicroVideo + MotionPhoto v2
 * metadata in APP1 + raw MP4 appended after JPEG EOI).
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
            // Skip fill 0xFF bytes
            while (pos < jpegBytes.size && (jpegBytes[pos].toInt() and 0xFF) == 0xFF) {
                pos++
            }
            if (pos >= jpegBytes.size) break
            val marker = jpegBytes[pos].toInt() and 0xFF
            pos++

            // Standalone markers without length
            if (marker == 0x00 || marker == 0xD8 || (marker in 0xD0..0xD7)) {
                out.write(0xFF)
                out.write(marker)
                continue
            }

            // Reached SOS (0xDA) or EOI (0xD9): inject MotionPhoto XMP before image entropy scan
            if (marker == 0xDA || marker == 0xD9) {
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

            // Strip any pre-existing XMP APP1 packet so we emit a single clean MotionPhoto XMP
            val isExistingXmp = marker == 0xE1 &&
                segLen >= 2 + xmpHeaderBytes.size &&
                matchesAsciiPrefix(jpegBytes, pos + 2, xmpHeaderBytes)

            if (!isExistingXmp) {
                out.write(0xFF)
                out.write(marker)
                out.write(jpegBytes, pos, segLen)
            }
            pos += segLen

            // Insert right after APP0/EXIF APP1 if encountered
            if (!xmpWritten && (marker == 0xE1 || marker == 0xE0) && !isExistingXmp) {
                writeXmpApp1Segment(out, xmpSegmentLenField, xmpHeaderBytes, xmpPayloadBytes)
                xmpWritten = true
            }
        }

        if (pos < jpegBytes.size) {
            out.write(jpegBytes, pos, jpegBytes.size - pos)
        }
        out.write(mp4Bytes)
        return out.toByteArray()
    }

    fun extractEmbeddedMp4Offset(motionPhotoBytes: ByteArray): Int? {
        val text = String(
            motionPhotoBytes,
            0,
            minOf(motionPhotoBytes.size, 64 * 1024),
            StandardCharsets.UTF_8
        )
        val regex = Regex("""MicroVideoOffset="(\d+)"""")
        val match = regex.find(text) ?: return null
        val offset = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
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
        return """<?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/">
 <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
  <rdf:Description rdf:about=""
    xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
    xmlns:Container="http://ns.google.com/photos/1.0/container/"
    xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
    GCamera:MotionPhoto="1"
    GCamera:MotionPhotoVersion="1"
    GCamera:MotionPhotoPresentationTimestampUs="$presentationTsUs"
    GCamera:MicroVideo="1"
    GCamera:MicroVideoVersion="1"
    GCamera:MicroVideoOffset="$mp4ByteLength"
    GCamera:MicroVideoPresentationTimestampUs="$presentationTsUs">
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
        Item:Length="$mp4ByteLength"
        Item:Padding="0"/>
     </rdf:li>
    </rdf:Seq>
   </Container:Directory>
  </rdf:Description>
 </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>"""
    }
}
