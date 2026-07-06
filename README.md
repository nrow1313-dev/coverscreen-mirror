# Cover Screen Mirror

Ứng dụng hỗ trợ trình chiếu và điều khiển màn hình chính từ màn hình phụ (Cover Screen) của các thiết bị Samsung Galaxy Z Flip (hỗ trợ tối ưu cho Z Flip 5 / Z Flip 6).

## 🌟 Tính năng nổi bật

1. **Giao diện Đơn sắc Sang trọng (Premium Monochrome Theme)**:
   - Giao diện thiết kế phẳng chuẩn High-Contrast, sử dụng tông màu Trắng - Đen - Xám tối giản.
   - Trải nghiệm thị giác mượt mà, chuyên nghiệp và loại bỏ mọi chi tiết thừa.

2. **Hai chế độ hoạt động cực mạnh**:
   - **Phản chiếu (MediaProjection)**: Trình chiếu màn hình chính ra ngoài thông qua cơ chế ghi hình, hỗ trợ thanh điều hướng ảo (Home, Back, Recents) bên trái.
   - **Màn Chính (VirtualDisplay + Shizuku)**: Can thiệp sâu vào hệ thống để gọi trực tiếp giao diện gốc lên màn hình phụ, tương tác đa điểm siêu mượt.

3. **Công nghệ "Diệt Zombie" (Tự động dọn dẹp rác hệ thống)**:
   - Tự động phát hiện và dọn dẹp các tiến trình ngầm Shizuku bị kẹt (`mirror_service`) từ những lần đóng ứng dụng ngang hoặc sập nguồn trước đó.
   - Đảm bảo thiết bị luôn giải phóng RAM, chạy ổn định, và đánh bại hoàn toàn lỗi "Tự khởi động lại" (Soft Reboot) kinh điển của Samsung Framework.

4. **Đồng bộ Luồng khởi tạo tĩnh**:
   - Lệnh điều khiển xuất hình ảnh được đồng bộ tuyệt đối với vòng đời của lõi Shizuku. Hệ thống tự động chờ dịch vụ kết nối hoàn tất 100% rồi mới bắt đầu truyền hình, loại bỏ triệt để lỗi "Màn hình đen" lúc khởi chạy.

5. **Lách giới hạn cảm biến gập mở**:
   - Tự động chèn lệnh `cmd device_state state 4` ngầm để "đánh lừa" hệ thống rằng máy đang được mở, nhằm duy trì chất lượng xuất hình tối đa mà không bị giới hạn bởi tính năng tiết kiệm pin. Hoàn trả trạng thái tức thời khi ấn Dừng.

## 📱 Yêu cầu hệ thống

- Thiết bị: Samsung Galaxy Z Flip (Đã test và tối ưu hoàn hảo trên Z Flip 5 / Z Flip 6 chạy One UI 6 / Android 14).
- Cấp quyền: Trợ năng (Accessibility) hoặc Shizuku (Khuyên dùng Shizuku để đạt độ trễ cảm ứng 0ms).

## 🚀 Hướng dẫn sử dụng

1. Mở ứng dụng từ màn hình trong (hoặc từ Cover Screen nếu có launcher hỗ trợ).
2. Chọn lõi điều khiển: **Trợ năng** hoặc **Shizuku**.
3. Chọn tính năng:
   - **Phản chiếu**: Chạy qua Screen Record.
   - **Màn Chính**: Gọi trực tiếp hệ thống.
4. Bấm **Có** ở hộp thoại xác nhận viền tương phản. Gập máy lại và trải nghiệm.
5. Khi kết thúc, bấm nút **Dừng** để hoàn trả thiết bị về trạng thái gốc.
