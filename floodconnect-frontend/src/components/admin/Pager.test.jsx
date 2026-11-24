// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { Pager } from './Pager';

describe('Pager (Trước / Sau cho bảng của Admin)', () => {
  afterEach(cleanup);
  it('ẩn khi chỉ có một trang', () => {
    const { container } = render(<Pager page={0} size={20} total={20} last onChange={() => {}} />);
    expect(container.innerHTML).toBe('');
  });
  it('hiện tổng số, trang hiện tại; tắt nút ở hai đầu; bấm thì báo trang mới', () => {
    const onChange = vi.fn();
    render(<Pager page={0} size={20} total={45} last={false} unit="ca" onChange={onChange} />);
    expect(screen.getByText('45 ca • Trang 1/3')).toBeTruthy();
    expect(screen.getByText('Trang trước').disabled).toBe(true);
    fireEvent.click(screen.getByText('Trang sau'));
    expect(onChange).toHaveBeenCalledWith(1);
    cleanup();
    render(<Pager page={2} size={20} total={45} last unit="ca" onChange={onChange} />);
    expect(screen.getByText('Trang sau').disabled).toBe(true);
    fireEvent.click(screen.getByText('Trang trước'));
    expect(onChange).toHaveBeenLastCalledWith(1);
  });
});
