# Lịch sử phiên bản

Quy ước: mỗi lần sửa bất kỳ thứ gì trong app đều phải nâng `versionCode` (cộng 1) và `versionName`
trong `app/build.gradle.kts`, rồi ghi một mục ở đây. Sửa lỗi thì tăng số cuối, thêm tính năng thì
tăng số giữa. Android chỉ cài đè lên bản cũ khi `versionCode` mới lớn hơn `versionCode` đang cài.

## 1.1.0 (versionCode 2)

- Tải ảnh từ bài đăng dạng ảnh của TikTok và Douyin: tải từng ảnh hoặc tải tất cả, lưu vào
  `Pictures/TikTokSave`. Giữ nguyên định dạng gốc nếu Android đọc được, nếu không thì chuyển sang JPEG.
- Khi link là bài ảnh, các nút cấu hình video được thay bằng nút tải ảnh. Chế độ chia sẻ B (tải ngầm)
  gặp bài ảnh thì tải tất cả ảnh.
- Hỗ trợ tải video Douyin (WebView, không có thuật toán ký).
- Trích xuất TikTok bằng WebView trước, OkHttp dự phòng; đọc được cả trang máy tính và trang điện thoại
  (`webapp.reflow.video.detail`).
- Màn Xác minh và nút "Mở trang để xem" khi TikTok hoặc Douyin đòi xác minh hoặc không đọc được dữ liệu.
- Thông báo lỗi kèm chẩn đoán (mã HTTP, tên khóa dữ liệu, địa chỉ trang).
- Thông báo tiến trình đổi thành "Đang tải video".

## 1.0.0 (versionCode 1)

- Bản đầu tiên: tải video TikTok không dấu chìm, tùy chọn 720p/1080p/tối đa và âm thanh,
  chia sẻ từ TikTok (chế độ A và B), 3 giao diện, lịch sử tải.
