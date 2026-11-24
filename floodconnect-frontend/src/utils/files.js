/* Đọc ảnh và nén nhỏ để lưu trong localStorage */
export function fileToDataUrl(file, max = 900) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error('Không đọc được tệp'));
    reader.onload = () => {
      if (!file.type.startsWith('image/')) return resolve({ name: file.name, kind: 'file', url: null });
      const img = new Image();
      img.onload = () => {
        const scale = Math.min(1, max / Math.max(img.width, img.height));
        const c = document.createElement('canvas');
        c.width = Math.round(img.width * scale);
        c.height = Math.round(img.height * scale);
        c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
        resolve({ name: file.name, kind: 'image', url: c.toDataURL('image/jpeg', 0.72) });
      };
      img.onerror = () => reject(new Error('Ảnh không hợp lệ'));
      img.src = reader.result;
    };
    reader.readAsDataURL(file);
  });
}

export function validateFiles(files, existing = 0) {
  if (files.length + existing > 5) return 'Tối đa 5 tệp đính kèm.';
  for (const f of files) {
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(f.type)) return `"${f.name}" sai định dạng (chỉ JPG/PNG/WEBP).`;
    if (f.size > 5 * 1024 * 1024) return `"${f.name}" vượt quá 5 MB.`;
  }
  return null;
}

/* Xem trước tệp ảnh trước khi tải lên (thay cho nén base64 của bản demo) */
export const previewOf = (file) => ({ file, kind: 'image', url: URL.createObjectURL(file), name: file.name });
