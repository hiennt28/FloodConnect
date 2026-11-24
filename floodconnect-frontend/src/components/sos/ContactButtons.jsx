import { MessageCircle, PhoneCall } from 'lucide-react';
import { zaloLink } from '@/utils';

export function ContactButtons({ phone, label = 'Gọi' }) {
  if (!phone) return null;
  return <>
    <a className="btn btn-soft btn-sm" href={`tel:${phone}`}><PhoneCall size={15} /> {label}</a>
    <a className="btn btn-ghost btn-sm" href={zaloLink(phone)} target="_blank" rel="noreferrer"><MessageCircle size={15} /> Zalo</a>
  </>;
}
