import { PROVINCES, wardsOf } from '@/utils';

/* Chọn địa chỉ 2 cấp: Tỉnh/Thành phố → Phường/Xã/Đặc khu (NQ 202/2025/QH15) */
export default function AddressPicker({ provinceCode, wardCode, onChange, disabled }) {
  return (
    <div className="grid-2">
      <select className="input" aria-label="Tỉnh/Thành phố" value={provinceCode || ''} disabled={disabled}
        onChange={(e) => onChange({ provinceCode: e.target.value, wardCode: '' })}>
        <option value="">Tỉnh/Thành phố</option>
        {PROVINCES.map((p) => <option key={p.code} value={p.code}>{p.name}</option>)}
      </select>
      <select className="input" aria-label="Phường/Xã/Đặc khu" value={wardCode || ''} disabled={disabled || !provinceCode}
        onChange={(e) => onChange({ provinceCode, wardCode: e.target.value })}>
        <option value="">Phường/Xã/Đặc khu</option>
        {wardsOf(provinceCode).map(([code, name]) => <option key={code} value={code}>{name}</option>)}
      </select>
    </div>
  );
}
