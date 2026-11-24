import { useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';
import { PASSWORD_CHARS_RULE } from '@/utils';

/** Ô nhập mật khẩu có nút hiện/ẩn. Nhận mọi prop của <input> (value, onChange, placeholder, autoComplete...).
 *  Cảnh báo ngay khi gõ phải chữ có dấu hoặc dấu cách (thường do quên tắt bộ gõ tiếng Việt khi ô đang ẩn). */
export function PasswordInput({ className = '', ...props }) {
  const [show, setShow] = useState(false);
  const bad = typeof props.value === 'string' && props.value !== '' && !PASSWORD_CHARS_RULE(props.value);
  return (
    <>
      <div className="input-wrap">
        <input {...props} type={show ? 'text' : 'password'} className={`input ${className}`.trim()} aria-invalid={bad || undefined} />
        <button type="button" className="input-icon-btn" aria-label={show ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'} onClick={() => setShow((v) => !v)}>
          {show ? <EyeOff size={18} /> : <Eye size={18} />}
        </button>
      </div>
      {bad && <span className="field-error" role="alert">Mật khẩu đang có chữ có dấu hoặc dấu cách. Hãy tắt bộ gõ tiếng Việt.</span>}
    </>
  );
}
