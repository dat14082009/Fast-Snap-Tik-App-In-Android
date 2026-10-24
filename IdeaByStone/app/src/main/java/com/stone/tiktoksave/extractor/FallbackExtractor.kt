package com.stone.tiktoksave.extractor

/**
 * Thử [primary] trước. Chỉ khi bị chặn, không đọc được dữ liệu hoặc cần xác minh mới chuyển sang
 * [secondary]. Lỗi dứt khoát (link sai, video đã xóa, bài ảnh, mất mạng) trả về ngay, không thử lại.
 * Nếu primary yêu cầu xác minh, lỗi đó được giữ nguyên để UI hiện nút Xác minh.
 */
class FallbackExtractor(
    private val primary: Extractor,
    private val secondary: Extractor
) : Extractor {

    override suspend fun extract(input: String): VideoInfo {
        val firstFailure: ExtractorException = try {
            return primary.extract(input)
        } catch (e: ExtractorException.Blocked) {
            e
        } catch (e: ExtractorException.Parse) {
            e
        } catch (e: ExtractorException.VerificationRequired) {
            e
        }

        try {
            return secondary.extract(input)
        } catch (e: ExtractorException.Blocked) {
            throw combine(firstFailure, e)
        } catch (e: ExtractorException.Parse) {
            throw combine(firstFailure, e)
        }
    }

    private fun combine(first: ExtractorException, second: ExtractorException): ExtractorException {
        if (first is ExtractorException.VerificationRequired) return first
        val text = "WebView: ${first.shortDetail()}; OkHttp: ${second.shortDetail()}"
        // Loại lỗi theo primary (WebView): nếu WebView đã đọc được trang thì không báo "bị chặn" chỉ vì OkHttp bị chặn.
        return if (first is ExtractorException.Blocked) {
            ExtractorException.Blocked(text)
        } else {
            ExtractorException.Parse(text)
        }
    }
}
