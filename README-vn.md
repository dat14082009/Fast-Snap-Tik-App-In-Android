<div align="right">
  🌐 <a href="README.md">English</a> | <strong>Tiếng Việt</strong>
</div>
# 💡 idea by Stone, create is AI 🤖

📱 Ứng dụng Android tải video TikTok và Douyin không dấu chìm. Chỉ dùng cho mục đích cá nhân, chủ sở hữu: Stone.

## ⚙️ Cách hoạt động

App không xử lý hình ảnh để xóa logo. App mở trang video TikTok, đọc dữ liệu nhúng trong trang, lấy đường link video gốc không logo trên CDN rồi tải thẳng về máy.

1. **🔗 Xử lý link:** Nhận link TikTok (kể cả `vt.tiktok.com`, `vm.tiktok.com`) hoặc Douyin (`v.douyin.com`, `www.douyin.com`, `iesdouyin.com`), tách URL khỏi văn bản chia sẻ (kể cả văn bản tiếng Trung), bỏ tham số theo dõi, theo dõi redirect, lấy video ID. `RoutingExtractor` chọn đường xử lý theo tên miền.
2. **🕵️ Lấy dữ liệu:** `Extractor` lấy tiêu đề, tác giả, poster, thời lượng, các luồng video và luồng âm thanh. Thử **WebView ẩn** trước (là trình duyệt thật nên ít bị TikTok chặn), nếu thất bại thì dùng **OkHttp** làm dự phòng.
3. **📥 Tải về:** `DownloadWorker` tải về `Download/TikTokSave` bằng MediaStore (không cần quyền lưu trữ), kèm cookie và User-Agent của WebView khi tải từ CDN.

## 🏗️ Cấu trúc

| Package | Vai trò |
|---|---|
| 📦 `extractor` | Interface `Extractor`; `RoutingExtractor` (chọn TikTok/Douyin theo tên miền); TikTok: `WebViewExtractor` (chính), `TikTokWebExtractor` (OkHttp, dự phòng), `FallbackExtractor`; Douyin: `DouyinWebExtractor` + `DouyinParsing` (đọc `RENDER_DATA`/`_ROUTER_DATA`, nguồn `<video>`, URL bắt từ mạng); `TikTokPageParser` (đọc dữ liệu dùng chung), `LinkParser`, `ExportOptions` (danh sách tùy chọn xuất dạng dữ liệu) |
| ⬇️ `download` | WorkManager, lưu MediaStore, thông báo tiến trình (có nút Hủy) |
| 💾 `data` | DataStore (theme, chế độ chia sẻ), Room (History), cache poster |
| 🎨 `ui` | Compose + Material 3, 3 theme dùng chung một bảng token (`ui/theme/Tokens.kt`) |

**📊 Thông số:** min API 29, target/compile API 35, Kotlin 2.0.21, AGP 8.7.3, Gradle 8.9. Phiên bản hiện tại: xem `CHANGELOG.md`.

## 🚀 Sử dụng

- 📤 Chia sẻ video từ TikTok, chọn app này.
- 🛠️ Lần chia sẻ đầu tiên hiện hộp thoại chọn chế độ: **A** mở app và tự dán link, tự bấm Gỡ dấu chìm; **B** tải ngầm MP4 chất lượng cao nhất kèm thông báo tiến trình.
- ⚙️ Đổi chế độ sau này: nhấn giữ biểu tượng bánh răng ở màn chính.
- 🎛️ Cài đặt có 3 mục: About, Theme, History.
- 🎵 Âm thanh lưu đúng định dạng gốc của nguồn (mp3 hoặc m4a).

## 🔨 Build APK

**🌐 Cách 1: GitHub Actions.** Tạo repository, đẩy toàn bộ project lên (cần cả thư mục ẩn `.github`). Vào tab Actions, chạy "Build APK", tải artifact `idea-by-stone-apk`.

**💻 Cách 2: Android Studio.** File, Open, chọn thư mục project, chờ Gradle sync, rồi Build, Build APK(s). Tệp nằm ở `app/build/outputs/apk/debug/app-debug.apk`.

*APK được ký bằng khóa debug, đủ để cài cho cá nhân và bạn bè (cần bật cài từ nguồn không xác định).*

## ⚠️ Khi app báo lỗi lấy dữ liệu

- 🛡️ **"TikTok yêu cầu xác minh"**: bấm nút **Xác minh với TikTok**, giải bước xác minh trong trang hiện ra, chờ video hiện rồi bấm **Xong**. App tự thử lại. Cookie được lưu cho các lần sau.
- ❌ **"Không đọc được dữ liệu từ TikTok"**: bấm nút **Mở trang TikTok để xem** để thấy TikTok đang hiển thị gì (captcha, đòi đăng nhập, video không khả dụng...). Thông báo lỗi liệt kê tên các khóa dữ liệu mà trang trả về, dùng để chẩn đoán. Trang dành cho điện thoại dùng khóa `webapp.reflow.video.detail`, trang máy tính dùng `webapp.video-detail`; app đọc được cả hai.
- 📋 **Thông báo có chi tiết trong ngoặc vuông** (ví dụ `[WebView: ...; OkHttp: HTTP 403]`): gửi nguyên dòng này khi cần chẩn đoán.
- 🔄 **Nếu vẫn không đọc được**: TikTok có thể đã đổi cấu trúc trang. Logic đọc dữ liệu nằm trong `TikTokPageParser.kt`; cách tải trang nằm trong `WebViewExtractor.kt` và `TikTokWebExtractor.kt`. UI và phần tải không bị ảnh hưởng. Có thể thay bằng implementation khác tại `AppContainer.extractor`.

## 🛑 Lưu ý

Tải video có thể vi phạm điều khoản của TikTok và quyền tác giả của người đăng. Hãy tôn trọng quyền tác giả.

## 📌 Douyin

- 👻 Dùng WebView ẩn, không có thuật toán ký (`X-Bogus`/`a_bogus`) trong app.
- 🔗 Luồng không logo là `play_addr`; `download_addr` (có logo) không bao giờ được dùng. URL `/playwm/` được đổi thành `/play/`.
- 🧩 Cấu trúc dữ liệu của Douyin thay đổi thường xuyên và phần này chưa được kiểm chứng với Douyin thật. Khi lỗi, thông báo liệt kê nguồn dữ liệu tìm thấy và tên khóa gốc; nút **Mở trang Douyin để xem** cho thấy Douyin đang hiển thị gì.
- 🛡️ Nếu Douyin đòi xác minh, nút **Xác minh với Douyin** mở trang để bạn giải bước đó một lần.

## 📸 Bài đăng dạng ảnh

- 🖼️ Khi link là bài ảnh (TikTok `/photo/`, Douyin `/note/`), các nút cấu hình video được thay bằng **Tải tất cả** và danh sách từng ảnh, mỗi ảnh có nút **Tải** riêng.
- 💾 Ảnh lưu vào `Pictures/TikTokSave`. Định dạng gốc được giữ nếu Android đọc được (JPEG, PNG, WebP, GIF, BMP; HEIC từ Android 9; AVIF từ Android 12), nếu không thì chuyển sang JPEG.
- 🚫 Ảnh có watermark (`owner_watermark_image`, `download_url_list`...) không bao giờ được dùng.
- 📦 Chế độ chia sẻ B (tải ngầm) gặp bài ảnh thì tải tất cả ảnh. Lịch sử ghi một mục cho mỗi lần tải ("N ảnh" khi tải tất cả, nút Mở sẽ mở ảnh đầu tiên).

## 🏷️ Quy ước phiên bản

🔄 Mỗi lần sửa bất kỳ thứ gì phải nâng `versionCode` (cộng 1) và `versionName` trong `app/build.gradle.kts` và ghi vào `CHANGELOG.md`, để Android luôn nhận ra đây là phiên bản mới và cài đè được lên bản cũ.