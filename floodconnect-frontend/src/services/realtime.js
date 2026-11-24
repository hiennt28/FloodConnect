/* Thông báo thời gian thực qua WebSocket (STOMP) tới backend: ws(s)://<API>/ws, nghe /user/queue/notifications. */
import { Client } from '@stomp/stompjs';
import { api, API_BASE, tokens } from './http';

/**
 * Kết nối và gọi onNotification(dto) mỗi khi backend đẩy thông báo cho người đang đăng nhập.
 * Mất kết nối thì tự kết nối lại sau 5 giây. Trả về hàm ngắt kết nối (gọi khi đăng xuất / rời trang).
 */
export function connectNotifications(onNotification) {
  const client = new Client({
    brokerURL: `${API_BASE.replace(/^http/, 'ws')}/ws`,
    reconnectDelay: 5000,
    // Backend chỉ kiểm tra JWT lúc CONNECT: gọi nhẹ một API trước để lớp http tự làm mới access token nếu đã hết hạn
    beforeConnect: async () => {
      await api('/api/notifications/unread-count').catch(() => {});
      client.connectHeaders = { Authorization: `Bearer ${tokens.get()?.access || ''}` };
    },
    onConnect: () => {
      client.subscribe('/user/queue/notifications', (msg) => {
        let dto = null;
        try { dto = JSON.parse(msg.body); } catch { /* nội dung không phải JSON: vẫn báo để tải lại dữ liệu */ }
        onNotification(dto);
      });
    },
  });
  client.activate();
  return () => { client.deactivate(); };
}
