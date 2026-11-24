import { Tag } from '@/components/common';

export function AiNote({ s }) {
  if (!s.reasons?.length && !s.needsVerify) return null;
  return (
    <div className="ai-note">
      <div className="row gap6 wrap">
        <Tag tone="blue">Gợi ý AI</Tag>
        {/* Cần xác minh: ca nhãn Không đang chờ Admin, chưa gửi tổ chức. Nên xác minh: ca đã gửi tổ chức nhưng AI chưa chắc chắn */}
        {s.adminReview ? <Tag tone="orange">Cần xác minh</Tag> : s.needsVerify && <Tag tone="gray">Nên xác minh</Tag>}
        {s.aiConfidence != null && <span className="muted small">Độ tin cậy {Math.round(s.aiConfidence * 100)}%</span>}
      </div>
      {s.reasons.map((r, i) => <span key={i} className="small">• {r}</span>)}
      <span className="muted small">AI chỉ hỗ trợ đánh giá, con người quyết định cuối cùng. Hệ thống không tự động hạ mức ưu tiên.</span>
    </div>
  );
}
