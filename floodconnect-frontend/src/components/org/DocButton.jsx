import { useState } from 'react';
import { FileText } from 'lucide-react';
import { useApp } from '@/hooks';
import { orgApi } from '@/services/api';
import { openProtectedFile } from '@/services/fileOpen';

/**
 * Mở một giấy tờ pháp lý / CCCD của tổ chức ở tab mới. Giấy tờ lưu riêng tư, không có đường dẫn công khai: tải qua API kèm token
 * (chỉ Admin và chính tổ chức đó được phép), nên dùng nút thay cho link thường.
 */
export function DocButton({ docId, children, size = 16 }) {
  const { toast } = useApp();
  const [busy, setBusy] = useState(false);
  const open = async () => {
    setBusy(true);
    const err = await openProtectedFile(() => orgApi.docFile(docId));
    setBusy(false);
    if (err) toast(err, 'err');
  };
  return (
    <button type="button" className="doc-row" onClick={open} disabled={busy} title="Mở giấy tờ ở tab mới">
      <FileText size={size} /> {busy ? 'Đang mở…' : children}
    </button>
  );
}
