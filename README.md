<div align="right">
  🌐 <strong>English</strong> | <a href="README-vn.md">Tiếng Việt</a>
</div>
<h1>Fast Snap Tik ✨</h1>

📱 An Android application for downloading TikTok and Douyin videos without watermarks. Intended for personal use only. **Owner:** Stone.

## ⚙️ How It Works

The app does not process video frames to remove watermarks. Instead, it opens the TikTok video page, parses embedded data inside the page, retrieves the original watermark-free CDN video link, and downloads it directly to your device.

1. **🔗 Link Parsing:** Accepts TikTok links (including `vt.tiktok.com`, `vm.tiktok.com`) or Douyin links (`v.douyin.com`, `www.douyin.com`, `iesdouyin.com`), extracts the URLs from shared text (including Chinese text), strips tracking parameters, follows redirects, and extracts the video ID. `RoutingExtractor` selects the appropriate processing pipeline based on the domain name.
2. **🕵️ Data Extraction:** `Extractor` fetches title, author, poster, duration, video streams, and audio streams. It tries a **headless WebView** first (a real browser environment, less likely to be blocked by TikTok), falling back to **OkHttp** if that fails.
3. **📥 Downloading:** `DownloadWorker` saves files to `Download/TikTokSave` using MediaStore (no broad storage permissions required), attaching the WebView's cookies and User-Agent when fetching content from the CDN.

## 🏗️ Architecture & Structure

| Package | Role | 
| ----- | ----- | 
| 📦 `extractor` | `Extractor` interface; `RoutingExtractor` (routes TikTok/Douyin by domain); TikTok: `WebViewExtractor` (primary), `TikTokWebExtractor` (OkHttp fallback), `FallbackExtractor`; Douyin: `DouyinWebExtractor` + `DouyinParsing` (parses `RENDER_DATA`/`_ROUTER_DATA`, `<video>` source, network-captured URLs); `TikTokPageParser` (common data parser), `LinkParser`, `ExportOptions` (data export options) | 
| ⬇️ `download` | WorkManager, MediaStore storage, progress notification (with Cancel action) | 
| 💾 `data` | DataStore (theme, share mode), Room (History database), poster cache | 
| 🎨 `ui` | Jetpack Compose + Material 3, 3 themes sharing a single token table (`ui/theme/Tokens.kt`) | 

**📊 Specifications:** Min API 29, Target/Compile API 35, Kotlin 2.0.21, AGP 8.7.3, Gradle 8.9. For the current version details, see `CHANGELOG.md`.

## 🚀 Usage

* 📤 Share a video from TikTok and select this app.
* 🛠️ On the first share, a dialog prompts you to choose a mode:
  * **Mode A**: Opens the app, auto-pastes the link, and triggers "Remove Watermark".
  * **Mode B**: Downloads highest quality MP4 in the background with progress notifications.
* ⚙️ Change mode anytime by long-pressing the gear icon on the main screen.
* 🎛️ Settings includes 3 sections: About, Theme, and History.
* 🎵 Audio files are saved in their native source format (`.mp3` or `.m4a`).

## 📥 Installation

1. Go to the [Releases](../../releases) page of this repository.
2. Download the latest `.apk` file from the newest release.
3. Install apk like normal.

## ⚠️ Troubleshooting Extraction Errors

* 🛡️ **"TikTok requires verification"**: Tap the **Verify with TikTok** button, solve the captcha/verification step on the loaded page, wait for the video to show up, then tap **Done**. The app will automatically retry. Cookies are saved for future requests.
* ❌ **"Failed to read data from TikTok"**: Tap **Open TikTok page to view** to see what TikTok is displaying (captcha, login prompt, video unavailable, etc.). The error message lists data key names returned by the page for diagnostic purposes. Mobile pages use `webapp.reflow.video.detail` while desktop pages use `webapp.video-detail`; the app supports both.
* 📋 **Error messages with bracketed details** (e.g., `[WebView: ...; OkHttp: HTTP 403]`): Copy and provide this entire string when seeking diagnostic help.
* 🔄 **If issues persist**: TikTok may have updated its page structure. Parsing logic resides in `TikTokPageParser.kt`, while page-fetching logic resides in `WebViewExtractor.kt` and `TikTokWebExtractor.kt`. UI and download logic remain unaffected. Alternative implementations can be wired in `AppContainer.extractor`.

## 🛑 Disclaimer

Downloading videos may violate TikTok's Terms of Service and copyright laws. Please respect content creators' intellectual property.

## 📌 Douyin Notes

* 👻 Uses a headless WebView without requiring custom client-side signing algorithms (`X-Bogus` / `a_bogus`).
* 🔗 The watermark-free stream URL is `play_addr`; `download_addr` (watermarked) is never used. `/playwm/` URLs are automatically rewritten to `/play/`.
* 🧩 Douyin's data structures change frequently and may vary. If an error occurs, the notification logs detected data sources and raw key names; the **Open Douyin page to view** button helps inspect what Douyin is serving.
* 🛡️ If Douyin requests verification, the **Verify with Douyin** button opens an inline browser window to complete the verification step once.

## 📸 Photo / Image Posts

* 🖼️ When sharing a photo post (TikTok `/photo/`, Douyin `/note/`), video action buttons are replaced by **Download All** and an itemized image list with individual **Download** buttons.
* 💾 Images are saved to `Pictures/TikTokSave`. Original file formats are retained if supported by Android (JPEG, PNG, WebP, GIF, BMP; HEIC from Android 9+; AVIF from Android 12+), otherwise converted to JPEG.
* 🚫 Watermarked image URLs (`owner_watermark_image`, `download_url_list`, etc.) are explicitly excluded.
* 📦 Share Mode B (background download) automatically downloads all images for photo posts. History logs one entry per batch download ("N photos" when downloading all; tapping Open targets the first image).

## 🏷️ Versioning Conventions

🔄 For every code or configuration change, increment `versionCode` (+1) and update `versionName` in `app/build.gradle.kts`, then document changes in `CHANGELOG.md`. This ensures Android recognizes subsequent builds as valid updates over existing installations.
