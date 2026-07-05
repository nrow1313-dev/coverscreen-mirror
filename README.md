# Cover Screen Mirror

Ứng dụng hỗ trợ trình chiếu và điều khiển ngược (cảm ứng hai chiều) từ màn hình phụ (Cover Screen) của các thiết bị Samsung Galaxy Z Flip (hỗ trợ tối ưu cho Z Flip 5 / Z Flip 6).

---

## 🌟 Tính năng nổi bật

1. **Trình chiếu màn hình trong ra màn ngoài**:
   - Tự động căn chỉnh đúng tỷ lệ màn hình chính bên trong khi đưa lên màn hình ngoài (không bị méo, biến dạng hình ảnh).
   - Thiết kế dải phím ảo trợ năng (Stop, Home, Recents, Back) xếp dọc gọn gàng ở khoảng trống màu đen phía bên trái của màn hình phụ, giúp thao tác nhanh mà không che khuất màn hình chính.

2. **Cảm ứng đảo chiều mượt mà**:
   - Hỗ trợ 2 chế độ điều khiển:
     - **Dịch vụ trợ năng (Accessibility Service)**: Cảm ứng giả lập chuẩn xác thông qua thao tác vẽ điểm chạm thực (`lineTo`).
     - **Shizuku (CMD)**: Phản hồi cảm ứng siêu tốc không có độ trễ nhờ quyền Shell hệ thống.

3. **Cơ chế gập mở thông minh**:
   - **Hộp thoại xác nhận Dual Screen**: Tự động hỏi ý kiến khi bắt đầu chiếu để chủ động đánh thức màn hình trong (`cmd device_state state 4`), tránh hiện tượng màn hình phụ bị đen do màn hình chính đang tắt.
   - **Khoảng trễ ổn định (500ms Delay)**: Đảm bảo phần cứng màn hình và bộ quản lý hiển thị (`WindowManager`) đồng bộ ổn định trước khi kích hoạt `MediaProjection`, loại bỏ hiện tượng chập chờn hay giật hình.
   - **Giải phóng cảm biến thông minh**: Khôi phục 100% cảm biến gập mở của Samsung ngay khi bạn bấm nút **X (Dừng)**, đóng app hoặc gạt tắt app từ danh sách ứng dụng gần đây (Recents).

---

## 🛠️ Yêu cầu hệ thống

- Thiết bị: Samsung Galaxy Z Flip (Z Flip 5 / Z Flip 6 khuyến nghị).
- Hệ điều hành: Android 10 trở lên (Đã tối ưu hóa hoàn toàn cho Android 14 One UI 6).
- Công cụ bổ sung (tùy chọn): **Shizuku** (nếu muốn dùng chế độ điều khiển độ trễ thấp).

---

## 🚀 Hướng dẫn sử dụng

1. **Khởi động ứng dụng**:
   - Mở ứng dụng từ màn hình trong (hoặc màn hình ngoài nếu dùng launcher hỗ trợ).
2. **Chọn chế độ điều khiển**:
   - Chọn **Trợ năng** hoặc **Shizuku** tùy theo nhu cầu.
   - Cấp quyền tương ứng theo chỉ dẫn trên màn hình.
3. **Bắt đầu chiếu**:
   - Bấm **BẮT ĐẦU TRÌNH CHIẾU** -> Chọn **CÓ** trên popup xác nhận -> Gập máy lại. Màn hình ngoài sẽ tự động kích hoạt và bắt đầu hiển thị.
4. **Kết thúc trình chiếu**:
   - Bấm nút **X (màu đỏ)** ở dải phím ảo phía bên trái màn hình ngoài để đóng trình chiếu hoàn toàn và đưa màn phụ về trạng thái bình thường.
