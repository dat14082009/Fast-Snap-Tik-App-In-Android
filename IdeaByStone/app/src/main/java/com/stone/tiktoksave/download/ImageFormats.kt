package com.stone.tiktoksave.download

/** Nhận dạng định dạng ảnh từ các byte đầu tiên (không tin vào đuôi tệp hay Content-Type). */
object ImageFormats {

    class Detected(val extension: String, val mime: String)

    sealed interface Plan {
        /** Giữ nguyên định dạng gốc vì Android đọc được. */
        class Keep(val detected: Detected) : Plan

        /** Định dạng lạ hoặc Android chưa đọc được: giải mã rồi lưu thành JPEG. */
        data object ConvertToJpeg : Plan
    }

    fun detect(bytes: ByteArray): Detected? {
        if (bytes.size < 12) return null
        fun at(i: Int): Int = bytes[i].toInt() and 0xFF
        fun ascii(from: Int, length: Int): String = String(bytes, from, length, Charsets.ISO_8859_1)

        return when {
            at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> Detected("jpg", "image/jpeg")
            at(0) == 0x89 && ascii(1, 3) == "PNG" -> Detected("png", "image/png")
            ascii(0, 3) == "GIF" -> Detected("gif", "image/gif")
            ascii(0, 4) == "RIFF" && ascii(8, 4) == "WEBP" -> Detected("webp", "image/webp")
            ascii(0, 2) == "BM" && at(6) == 0 && at(7) == 0 && at(8) == 0 && at(9) == 0 -> Detected("bmp", "image/bmp")
            ascii(4, 4) == "ftyp" -> when (ascii(8, 4)) {
                "avif", "avis" -> Detected("avif", "image/avif")
                "heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1" -> Detected("heic", "image/heic")
                else -> null
            }
            else -> null
        }
    }

    /** Android có giải mã/hiển thị được định dạng này ở mức API [sdkInt] không. */
    fun androidCanRead(detected: Detected, sdkInt: Int): Boolean = when (detected.extension) {
        "jpg", "png", "gif", "webp", "bmp" -> true
        "heic" -> sdkInt >= 28
        "avif" -> sdkInt >= 31
        else -> false
    }

    fun plan(bytes: ByteArray, sdkInt: Int): Plan {
        val detected = detect(bytes)
        return if (detected != null && androidCanRead(detected, sdkInt)) Plan.Keep(detected) else Plan.ConvertToJpeg
    }
}
