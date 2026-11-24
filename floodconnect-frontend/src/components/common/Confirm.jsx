import { Modal } from './Modal';

export function Confirm({ title, text, okText = 'Xác nhận', danger, onOk, onClose }) {
  return (
    <Modal title={title} onClose={onClose} footer={<>
      <button className="btn btn-ghost" onClick={onClose}>Hủy</button>
      <button className={`btn ${danger ? 'btn-danger' : 'btn-primary'}`} onClick={() => { onOk(); onClose(); }}>{okText}</button>
    </>}>
      <p>{text}</p>
    </Modal>
  );
}
