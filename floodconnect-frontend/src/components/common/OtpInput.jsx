import { useRef } from 'react';

/** Các ô nhập mã OTP: gõ tự nhảy ô kế, dán cả mã điền hết các ô, Backspace lùi về ô trước, gọi onComplete khi đủ số chữ số. */
export function OtpInput({ value = '', onChange, length = 6, disabled, onComplete }) {
  const refs = useRef([]);
  const set = (v) => {
    const next = v.replace(/\D/g, '').slice(0, length);
    onChange(next);
    if (next.length === length) onComplete?.(next);
  };

  const handleChange = (i, raw) => {
    const d = raw.replace(/\D/g, '').slice(-1); // chỉ giữ chữ số cuối cùng gõ vào ô này
    set(value.slice(0, i) + d + value.slice(i + 1));
    if (d && i < length - 1) refs.current[i + 1]?.focus();
  };
  const handleKeyDown = (i, e) => {
    if (e.key === 'Backspace' && !value[i] && i > 0) refs.current[i - 1]?.focus();
  };
  const handlePaste = (i, e) => {
    const text = e.clipboardData.getData('text');
    if (!/\d/.test(text)) return;
    e.preventDefault();
    const d = text.replace(/\D/g, '');
    set(value.slice(0, i) + d);
    const pos = Math.min(i + d.length, length - 1);
    requestAnimationFrame(() => refs.current[pos]?.focus());
  };

  return (
    <div className="otp-boxes">
      {Array.from({ length }).map((_, i) => (
        <input
          key={i}
          ref={(el) => { refs.current[i] = el; }}
          className="input otp-box"
          inputMode="numeric"
          maxLength={1}
          disabled={disabled}
          value={value[i] || ''}
          autoFocus={i === 0}
          onChange={(e) => handleChange(i, e.target.value)}
          onKeyDown={(e) => handleKeyDown(i, e)}
          onPaste={(e) => handlePaste(i, e)}
        />
      ))}
    </div>
  );
}
