// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';

const app = { state: { sos: [] }, sos: { loadCase: vi.fn(() => Promise.resolve()) } };
vi.mock('./useApp', () => ({ useApp: () => app }));
const { useCaseParam } = await import('./useCaseParam');

/* Chạy hook trong router ở địa chỉ cho trước; trả về cả địa chỉ hiện tại để kiểm tra ?case= đã được xóa */
const run = (url, tryOpen, onMissing) => renderHook(() => { useCaseParam(tryOpen, onMissing); return useLocation(); },
  { wrapper: ({ children }) => <MemoryRouter initialEntries={[url]}>{children}</MemoryRouter> });

describe('useCaseParam (mở ca SOS từ thông báo)', () => {
  it('ca đã có sẵn: mở ngay, xóa ?case= khỏi địa chỉ, không tải lại', async () => {
    app.sos.loadCase.mockClear();
    const tryOpen = vi.fn(() => true);
    const onMissing = vi.fn();
    const { result } = run('/rescue?case=12&x=1', tryOpen, onMissing);
    await waitFor(() => expect(tryOpen).toHaveBeenCalledWith('12'));
    await waitFor(() => expect(result.current.search).toBe('?x=1'));
    expect(app.sos.loadCase).not.toHaveBeenCalled();
    expect(onMissing).not.toHaveBeenCalled();
  });

  it('ca chưa có: tải lại một lần rồi mở được', async () => {
    app.sos.loadCase.mockClear();
    let loaded = false;
    app.sos.loadCase.mockImplementationOnce(async () => { loaded = true; });
    const tryOpen = vi.fn(() => loaded);
    const onMissing = vi.fn();
    run('/admin/sos?case=7', tryOpen, onMissing);
    await waitFor(() => expect(tryOpen).toHaveLastReturnedWith(true));
    expect(app.sos.loadCase).toHaveBeenCalledTimes(1);
    expect(app.sos.loadCase).toHaveBeenCalledWith('7');
    expect(onMissing).not.toHaveBeenCalled();
  });

  it('tải lại vẫn không có: báo không tìm thấy đúng một lần', async () => {
    app.sos.loadCase.mockClear();
    const onMissing = vi.fn();
    run('/sos?case=9', () => false, onMissing);
    await waitFor(() => expect(onMissing).toHaveBeenCalledWith('9'));
    expect(app.sos.loadCase).toHaveBeenCalledTimes(1);
    expect(onMissing).toHaveBeenCalledTimes(1);
  });

  it('không có ?case=: không làm gì', async () => {
    app.sos.loadCase.mockClear();
    const tryOpen = vi.fn(() => true);
    run('/sos', tryOpen, vi.fn());
    await new Promise((r) => setTimeout(r, 20));
    expect(tryOpen).not.toHaveBeenCalled();
    expect(app.sos.loadCase).not.toHaveBeenCalled();
  });
});
