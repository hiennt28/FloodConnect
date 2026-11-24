// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { SkeletonLines, SkeletonPost, SkeletonRows } from './Skeleton';

describe('Skeleton (khung chờ)', () => {
  afterEach(cleanup); // vitest không bật globals nên Testing Library không tự dọn DOM giữa các test
  it('báo "đang tải" cho trình đọc màn hình bằng role=status', () => {
    render(<SkeletonLines lines={3} label="Đang tải ca lân cận…" />);
    const st = screen.getByRole('status');
    expect(st).toHaveProperty('textContent', 'Đang tải ca lân cận…');
    expect(st.getAttribute('aria-busy')).toBe('true');
    expect(st.querySelectorAll('.sk')).toHaveLength(3);
  });

  it('khung lặp lại (label rỗng) bị ẩn khỏi trình đọc màn hình, chỉ đọc một lần', () => {
    render(<><SkeletonPost /><SkeletonPost label="" /></>);
    expect(screen.getAllByRole('status')).toHaveLength(1);
  });

  it('bảng: mỗi dòng chờ là một ô trải hết số cột', () => {
    const { container } = render(<table><tbody><SkeletonRows cols={7} rows={4} /></tbody></table>);
    const rows = container.querySelectorAll('tr.sk-row');
    expect(rows).toHaveLength(4);
    rows.forEach((tr) => expect(tr.querySelector('td').getAttribute('colspan')).toBe('7'));
    expect(screen.getAllByRole('status')).toHaveLength(1);
  });
});
