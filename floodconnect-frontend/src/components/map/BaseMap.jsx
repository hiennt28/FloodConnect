import { useEffect, useState } from 'react';
import { TileLayer, useMap } from 'react-leaflet';
import 'maplibre-gl/dist/maplibre-gl.css';
import maplibreGL from '@maplibre/maplibre-gl-leaflet';
import { FALLBACK_RASTER, MAP } from '@/utils';

/* Đổi nhãn sang tiếng Việt (OSM có name:vi / name) */
function applyVietnameseLabels(gl) {
  const style = gl.getStyle();
  (style?.layers || []).forEach((l) => {
    if (l.type === 'symbol' && l.layout?.['text-field']) {
      gl.setLayoutProperty(l.id, 'text-field', ['coalesce', ['get', 'name:vi'], ['get', 'name'], ['get', 'name_en']]);
    }
  });
}

/* Nền bản đồ vector (MapLibre GL) gắn vào Leaflet; lỗi thì dùng lớp raster dự phòng */
export default function BaseMap() {
  const map = useMap();
  const [failed, setFailed] = useState(MAP.type === 'raster');

  useEffect(() => {
    if (failed) return undefined;
    const layer = maplibreGL({ style: MAP.style, attribution: MAP.attribution, transformRequest: MAP.transformRequest });
    layer.addTo(map);
    const gl = layer.getMaplibreMap();
    let loaded = false;
    const onLoad = () => { loaded = true; if (MAP.vietnameseLabels) applyVietnameseLabels(gl); };
    gl.on('load', onLoad);
    // Chỉ chuyển sang lớp dự phòng khi style không tải được sau 15 giây
    // (lỗi lẻ tẻ của từng tile/font không làm hỏng cả bản đồ)
    const timer = setTimeout(() => { if (!loaded) setFailed(true); }, 15000);
    return () => { clearTimeout(timer); map.removeLayer(layer); };
  }, [map, failed]);

  if (!failed) return null;
  const raster = MAP.type === 'raster' ? MAP : FALLBACK_RASTER;
  return <TileLayer url={raster.url} attribution={raster.attribution} maxZoom={19} />;
}
