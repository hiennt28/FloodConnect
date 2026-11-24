import { DOC_SLOTS, ORG_TYPES } from './constants';
import { provinceName, wardName } from './geo';

export const orgTypeLabel = (o) => ORG_TYPES[o?.type]?.label || '';

/* Ô hồ sơ theo loại tổ chức: thiện nguyện nộp CCCD hai mặt, các loại khác nộp giấy tờ chính; minh chứng không bắt buộc với mọi loại */
export const requiredDocSlots = (type) => (type === 'VOLUNTEER' ? ['ID_FRONT', 'ID_BACK'] : ['MAIN']);
export const docSlotsOf = (type) => [...requiredDocSlots(type), 'EVIDENCE'];
export const docSlotLabel = (type, slot) => (slot === 'MAIN' ? ORG_TYPES[type]?.docLabel : DOC_SLOTS[slot]?.label);
export const docSlotHint = (type, slot) => (slot === 'MAIN' ? ORG_TYPES[type]?.docHint : DOC_SLOTS[slot]?.hint);
export const emptyDocs = () => ({ MAIN: [], ID_FRONT: [], ID_BACK: [], EVIDENCE: [] });

/* Địa chỉ hiển thị: Số nhà/Đường, Phường/Xã, Tỉnh/Thành phố */
export const orgAddress = (o) => o?.addressText || [o?.street, wardName(o?.provinceCode, o?.wardCode), provinceName(o?.provinceCode)].filter(Boolean).join(', ');
