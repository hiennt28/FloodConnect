// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { AdjustSeverityModal } from './AdjustSeverityModal';

describe('AdjustSeverityModal: ghi chú xác minh chỉ cho ca AI xếp “Không”', () => {
  afterEach(cleanup);

  it('xác minh ca KHONG: bắt buộc ghi chú, gửi mức + ghi chú đã bỏ khoảng trắng', async () => {
    const onSave = vi.fn().mockResolvedValue(null);
    render(<AdjustSeverityModal s={{ id: '7', code: 'SOS-1031', severity: 'KHONG', verify: true }} onClose={() => {}} onSave={onSave} />);
    const button = screen.getByText('Xác minh & chuyển tổ chức');

    fireEvent.click(button);
    expect(screen.getByText('Vui lòng ghi lại thông tin đã xác minh với người gửi.')).toBeTruthy();
    expect(onSave).not.toHaveBeenCalled();

    fireEvent.change(screen.getByRole('textbox'), { target: { value: '  Nhà ngập 1 m, 2 người già  ' } });
    expect(screen.getByText(/29\/1000/)).toBeTruthy(); // bộ đếm ký tự (tính cả khoảng trắng đang gõ)
    fireEvent.click(button);
    await waitFor(() => expect(onSave).toHaveBeenCalledWith('TRUNG_BINH', 'Nhà ngập 1 m, 2 người già'));
  });

  it('điều chỉnh mức thông thường: không có ô ghi chú, chỉ gửi mức', async () => {
    const onSave = vi.fn().mockResolvedValue(null);
    render(<AdjustSeverityModal s={{ id: '8', code: 'SOS-1032', severity: 'THAP' }} onClose={() => {}} onSave={onSave} />);
    expect(screen.queryByRole('textbox')).toBeNull();
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'CAO' } });
    fireEvent.click(screen.getByText('Xác nhận thay đổi'));
    await waitFor(() => expect(onSave).toHaveBeenCalledWith('CAO'));
  });

  it('hiện lỗi backend trả về', async () => {
    const onSave = vi.fn().mockResolvedValue('Ca SOS không còn ở trạng thái Chờ xử lý.');
    render(<AdjustSeverityModal s={{ id: '7', code: 'SOS-1031', severity: 'KHONG', verify: true }} onClose={() => {}} onSave={onSave} />);
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Đã gọi' } });
    fireEvent.click(screen.getByText('Xác minh & chuyển tổ chức'));
    expect(await screen.findByText('Ca SOS không còn ở trạng thái Chờ xử lý.')).toBeTruthy();
  });
});
