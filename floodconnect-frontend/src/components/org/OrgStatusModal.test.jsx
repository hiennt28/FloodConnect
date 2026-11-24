// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { OrgStatusModal } from './OrgStatusModal';

describe('OrgStatusModal: xác nhận đổi trạng thái tổ chức, ghi rõ hệ quả kèm số ca thật', () => {
  afterEach(cleanup);

  it('Không hoạt động khi đang có ca: ca chờ xác nhận bị trả lại, ca đang cứu hộ phải hoàn thành, nút đỏ', async () => {
    const onConfirm = vi.fn().mockResolvedValue(null);
    render(<OrgStatusModal target="INACTIVE" assigned={2} handling={1} onClose={() => {}} onConfirm={onConfirm} />);
    expect(screen.getByText(/2 ca đang chờ bạn xác nhận sẽ được trả lại/)).toBeTruthy();
    expect(screen.getByText(/1 ca đang cứu hộ bạn vẫn phải tiếp tục/)).toBeTruthy();
    expect(screen.getByText(/Ca bị trả lại sẽ được báo/)).toBeTruthy();
    const ok = screen.getByText('Chuyển sang Không hoạt động');
    expect(ok.className).toContain('btn-danger');
    fireEvent.click(ok);
    await waitFor(() => expect(onConfirm).toHaveBeenCalledTimes(1));
  });

  it('Không có ca: không ghi các dòng về số ca, không có cảnh báo trả ca', () => {
    render(<OrgStatusModal target="INACTIVE" onClose={() => {}} onConfirm={vi.fn()} />);
    expect(screen.queryByText(/sẽ được trả lại/)).toBeNull();
    expect(screen.queryByText(/Ca bị trả lại/)).toBeNull();
  });

  it('Đang bận: ca đã giao vẫn giữ nguyên, nút cam', () => {
    render(<OrgStatusModal target="BUSY" assigned={1} handling={0} onClose={() => {}} onConfirm={vi.fn()} />);
    expect(screen.getByText(/1 ca đang chờ bạn xác nhận/)).toBeTruthy();
    expect(screen.queryByText(/đang cứu hộ/)).toBeNull();
    expect(screen.getByText('Chuyển sang Đang bận').className).toContain('btn-warning');
  });

  it('lỗi từ máy chủ hiện trong popup, popup không đóng', async () => {
    const onConfirm = vi.fn().mockResolvedValue('Tổ chức đang bị khóa.');
    render(<OrgStatusModal target="READY" onClose={() => {}} onConfirm={onConfirm} />);
    fireEvent.click(screen.getByText('Chuyển sang Sẵn sàng'));
    expect(await screen.findByText('Tổ chức đang bị khóa.')).toBeTruthy();
  });
});
