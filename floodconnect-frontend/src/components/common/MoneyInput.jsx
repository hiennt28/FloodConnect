import { useLayoutEffect, useRef } from 'react';

const groupThousands = (digits) => digits.replace(/\B(?=(\d{3})+(?!\d))/g, '.');

/**
 * Ô nhập số tiền: hiển thị có dấu chấm ngăn cách hàng nghìn (kiểu Việt Nam), ví dụ "10.000.000".
 * value: chuỗi số thuần không có dấu chấm (vd '10000000'), giống các ô số tiền khác trong ứng dụng.
 * onChange(rawDigits): gọi lại với chuỗi số thuần mới (không phải event) để chỗ gọi khỏi tự strip dấu chấm.
 */
export function MoneyInput({ value, onChange, suffix = 'đ', className = '', ...props }) {
  const ref = useRef(null);
  const pendingCaret = useRef(null);
  const digits = (value ?? '').toString().replace(/\D/g, '');
  const display = digits ? groupThousands(digits) : '';

  const handleChange = (e) => {
    const el = e.target;
    const caret = el.selectionStart ?? el.value.length;
    const digitsBeforeCaret = el.value.slice(0, caret).replace(/\D/g, '').length;
    pendingCaret.current = digitsBeforeCaret;
    onChange(el.value.replace(/\D/g, ''));
  };

  // Sau khi value định dạng lại (thêm/bớt dấu chấm) làm React vẽ lại, đặt con trỏ về đúng vị trí theo số chữ số đã gõ
  useLayoutEffect(() => {
    if (pendingCaret.current == null || !ref.current) return;
    const nDigits = pendingCaret.current;
    pendingCaret.current = null;
    if (nDigits === 0) { ref.current.setSelectionRange(0, 0); return; }
    let count = 0, pos = display.length;
    for (let i = 0; i < display.length; i++) {
      if (/\d/.test(display[i])) count++;
      if (count === nDigits) { pos = i + 1; break; }
    }
    ref.current.setSelectionRange(pos, pos);
  }, [display]);

  return (
    <div className="input-wrap money-input-wrap">
      <input {...props} ref={ref} inputMode="numeric" className={`input ${className}`.trim()} value={display} onChange={handleChange} />
      {suffix && digits && <span className="money-input-suffix">{suffix}</span>}
    </div>
  );
}
