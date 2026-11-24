import { Field } from '@/components/common';
import { ORG_CASES_RANGE, ORG_RADIUS_RANGE } from '@/utils';

/**
 * Hai ô bắt buộc về năng lực tiếp nhận của tổ chức, dùng chung cho đăng ký, sửa thông tin và gửi lại hồ sơ.
 * value = { cases, radiusKm }; onChange nhận phần thay đổi; errors = { cases, radiusKm }.
 */
export function OrgCapacityFields({ value, onChange, errors = {} }) {
  return (
    <div className="grid-2 cap-fields">
      <Field label="Số ca xử lý cùng lúc" required error={errors.cases}
        hint="Số ca SOS đội có thể nhận song song. Khi đội gần kín ca, AI ưu tiên đội khác còn sức.">
        <input className="input" type="number" inputMode="numeric" min={ORG_CASES_RANGE.min} max={ORG_CASES_RANGE.max} step={1}
          placeholder="VD: 3" value={value.cases} onChange={(e) => onChange({ cases: e.target.value })} />
      </Field>
      <Field label="Bán kính hoạt động (km)" required error={errors.radiusKm}
        hint="Khoảng cách tối đa tính từ trụ sở mà đội có thể tới. Ca ở xa hơn sẽ bị hạ điểm hoặc không gợi ý.">
        <input className="input" type="number" inputMode="decimal" min={ORG_RADIUS_RANGE.min} max={ORG_RADIUS_RANGE.max} step={1}
          placeholder="VD: 20" value={value.radiusKm} onChange={(e) => onChange({ radiusKm: e.target.value })} />
      </Field>
    </div>
  );
}
