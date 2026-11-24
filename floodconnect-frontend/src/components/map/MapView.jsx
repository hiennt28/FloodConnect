import { Circle, GeoJSON, MapContainer, Marker, Polygon, Popup, TileLayer, Tooltip, useMap, useMapEvents } from 'react-leaflet';
import BaseMap from './BaseMap';
import { MAP, SATELLITE } from '@/utils';
import thumbMap from '@/assets/map/thumb-map.jpg';
import thumbSatellite from '@/assets/map/thumb-satellite.jpg';
import vietnam from '@/assets/geo/vietnam.geo.json';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { useEffect, useRef, useState } from 'react';
import { Maximize2, Minimize2 } from 'lucide-react';

const ICON_SVG = {
  sos: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="#fff" stroke-width="3" stroke-linecap="round"><path d="M12 6v7"/><path d="M12 17.5v.5"/></svg>',
  me: '<svg viewBox="0 0 24 24" width="12" height="12"><circle cx="12" cy="12" r="6" fill="#fff"/></svg>',
  org: '<svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="#fff" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="8"/><circle cx="12" cy="12" r="3"/><path d="m6.3 6.3 3.6 3.6M14.1 14.1l3.6 3.6M14.1 9.9l3.6-3.6M6.3 17.7l3.6-3.6"/></svg>',
};

export function pin({ kind = 'sos', color = '#c81e1e', label, pulse }) {
  return L.divIcon({
    className: 'map-pin-wrap',
    iconSize: [36, 36],
    iconAnchor: [18, 18],
    popupAnchor: [0, -18],
    html: `<div class="map-pin ${pulse ? 'pulse' : ''}" style="--pin:${color}">${ICON_SVG[kind]}</div>${label ? `<div class="map-pin-label" style="--pin:${color}">${label}</div>` : ''}`,
  });
}

/* Ranh giới Việt Nam: làm mờ phần ngoài lãnh thổ để nổi bật hình chữ S */
const VN_RINGS = vietnam.coordinates.map((poly) => poly[0].map(([lng, lat]) => [lat, lng]));
const WORLD = [[-89, -179], [-89, 179], [89, 179], [89, -179]];
export const VN_BOUNDS = [[8.2, 102.0], [23.5, 109.6]];
const ISLANDS = [
  { name: 'Quần đảo Hoàng Sa', pos: [16.5, 112.0] },
  { name: 'Quần đảo Trường Sa', pos: [10.0, 114.0] },
];

function VietnamLayer({ islands }) {
  return (
    <>
      <Polygon positions={[WORLD, ...VN_RINGS]} interactive={false} pathOptions={{ stroke: false, fillColor: '#f3f6fb', fillOpacity: 0.55 }} />
      <Polygon positions={VN_RINGS} interactive={false} pathOptions={{ color: '#1748d4', weight: 1.5, opacity: 0.7, fill: false }} />
      {islands && ISLANDS.map((i) => (
        <Circle key={i.name} center={i.pos} radius={60000} interactive={false} pathOptions={{ color: '#1748d4', weight: 1, dashArray: '4 4', fillOpacity: 0.04 }}>
          <Tooltip permanent direction="center" className="island-label">{i.name}</Tooltip>
        </Circle>
      ))}
    </>
  );
}

/* Nền ảnh vệ tinh: ảnh + tên đường, tên địa danh chỉ khi đã phóng to (xem SATELLITE trong mapConfig) */
function SatelliteLayer() {
  return (
    <>
      <TileLayer url={SATELLITE.imagery} attribution={SATELLITE.attribution} maxNativeZoom={18} maxZoom={19} />
      <TileLayer url={SATELLITE.roads} maxNativeZoom={18} maxZoom={19} />
      <TileLayer url={SATELLITE.places} minZoom={SATELLITE.PLACES_MIN_ZOOM} maxNativeZoom={18} maxZoom={19} />
    </>
  );
}

/* Lựa chọn Bản đồ / Vệ tinh được nhớ chung cho mọi bản đồ có nút chuyển (chỉ là tiện ích, đọc/ghi lỗi thì dùng Bản đồ) */
const LAYER_KEY = 'fc.mapLayer';
const readLayer = () => { try { return localStorage.getItem(LAYER_KEY) === 'sat' ? 'sat' : 'map'; } catch { return 'map'; } };
const saveLayer = (v) => { try { localStorage.setItem(LAYER_KEY, v); } catch { /* bỏ qua */ } };

/* Đưa bản đồ về toàn quốc hoặc một vùng mỗi khi `bounds` đổi */
function FitBounds({ bounds, fitKey }) {
  const map = useMap();
  useEffect(() => { if (bounds) map.fitBounds(bounds, { padding: [10, 10] }); }, [JSON.stringify(bounds), fitKey]); // eslint-disable-line
  return null;
}

/* Thu nhỏ marker khi nhìn toàn quốc */
function ZoomWatcher({ onZoom }) {
  const map = useMapEvents({ zoomend: () => onZoom(map.getZoom()) });
  useEffect(() => { onZoom(map.getZoom()); }, []); // eslint-disable-line
  return null;
}

/* Đưa điểm `center` vào giữa, rồi dịch khung nhìn theo offset [x, y] (px) khi có bảng nổi che một phần bản đồ */
function FitTo({ center, zoom, offset }) {
  const map = useMap();
  const [dx, dy] = offset || [0, 0];
  useEffect(() => {
    if (!center) return;
    map.setView(center, zoom ?? map.getZoom(), { animate: false });
    if (dx || dy) map.panBy([dx, dy], { animate: false });
  }, [center?.[0], center?.[1], dx, dy]); // eslint-disable-line
  return null;
}

/* Chạm vào bản đồ để chọn điểm (VD người dân chỉnh vị trí SOS) */
function MapClick({ onClick }) {
  useMapEvents({ click: (e) => onClick(e.latlng) });
  return null;
}

/* Marker có onDragEnd thì kéo được, thả ra trả về tọa độ mới */
const markerHandlers = (m) => {
  const h = {};
  if (m.onClick) h.click = m.onClick;
  if (m.onDragEnd) h.dragend = (e) => m.onDragEnd(e.target.getLatLng());
  return Object.keys(h).length ? h : undefined;
};

/**
 * markers: [{ id, lat, lng, kind, color, label, pulse, popup, onClick, onDragEnd }]
 * onMapClick: (latlng) => void, gọi khi chạm vào chỗ trống trên bản đồ
 * outline: { id, geometry } đường viền một vùng (VD phường/xã), geometry dạng GeoJSON; không nhận chạm để chạm bên trong vẫn đặt được ghim
 * focusOffset: [x, y] px, dịch khung nhìn sau khi đưa `focus` vào giữa (VD bảng chi tiết nổi che bên phải bản đồ)
 * circles: [{ lat, lng, radius, color }]
 * heat: [{ lat, lng, n }]  (bản đồ nhiệt mật độ SOS, bán kính 500 m)
 * layerSwitch: hiện ô chuyển Bản đồ / Vệ tinh ở góc dưới trái (chỉ bật ở bản đồ cần nhận ra đúng một vị trí thật:
 *   đặt ghim trụ sở, chọn vị trí SOS, bản đồ đội cứu hộ, Admin xem trụ sở tổ chức)
 */
export default function MapView({ center = [16.047, 108.2], zoom = 13, markers = [], circles = [], heat = [], height = 520, focus, focusOffset, bounds, fitKey, showVietnam = true, onMapClick, outline, layerSwitch = false, children }) {
  const [z, setZ] = useState(zoom);
  const [fullscreen, setFullscreen] = useState(false);
  const [layer, setLayer] = useState(() => (layerSwitch ? readLayer() : 'map'));
  const sat = layer === 'sat';
  const mapBoxRef = useRef(null);
  const toggleLayer = () => { const v = sat ? 'map' : 'sat'; setLayer(v); saveLayer(v); };

  useEffect(() => {
    const onFullscreenChange = () => setFullscreen(document.fullscreenElement === mapBoxRef.current);
    document.addEventListener('fullscreenchange', onFullscreenChange);
    return () => document.removeEventListener('fullscreenchange', onFullscreenChange);
  }, []);

  const toggleFullscreen = async () => {
    try {
      if (document.fullscreenElement === mapBoxRef.current) await document.exitFullscreen();
      else await mapBoxRef.current?.requestFullscreen();
    } catch (error) {
      console.error('Không thể mở bản đồ toàn màn hình', error);
    }
  };

  return (
    <div ref={mapBoxRef} className={`map-box ${z < 7 ? 'far' : ''}`} style={{ height }}>
      <button className="map-fullscreen-btn" type="button" onClick={toggleFullscreen} aria-label={fullscreen ? 'Thoát toàn màn hình' : 'Mở bản đồ toàn màn hình'} title={fullscreen ? 'Thoát toàn màn hình' : 'Mở bản đồ toàn màn hình'}>
        {fullscreen ? <Minimize2 size={17} /> : <Maximize2 size={17} />}
      </button>
      {/* Nằm ngoài MapContainer nên bấm nút không bị tính là chạm bản đồ (không đặt ghim nhầm) */}
      {layerSwitch && (
        <button className="map-layer-btn" type="button" onClick={toggleLayer} aria-pressed={sat}
          aria-label={sat ? 'Chuyển sang bản đồ thường' : 'Chuyển sang ảnh vệ tinh'} title={sat ? 'Bản đồ' : 'Vệ tinh'}
          style={{ backgroundImage: `url(${sat ? thumbMap : thumbSatellite})` }}>
          <span>{sat ? 'Bản đồ' : 'Vệ tinh'}</span>
        </button>
      )}
      <MapContainer center={center} zoom={zoom} minZoom={5} zoomSnap={0.25} zoomDelta={0.5} scrollWheelZoom style={{ height: '100%', width: '100%' }}>
        {sat ? <SatelliteLayer /> : <BaseMap />}
        <ZoomWatcher onZoom={setZ} />
        {showVietnam && <VietnamLayer islands={MAP.islandLabels || sat} />}
        <FitBounds bounds={bounds} fitKey={fitKey} />
        <FitTo center={focus} zoom={focus ? 14 : undefined} offset={focusOffset} />
        {onMapClick && <MapClick onClick={onMapClick} />}
        {/* key theo id (và lớp nền): lớp GeoJSON của Leaflet không tự vẽ lại khi dữ liệu/kiểu đổi.
            Trên nền vệ tinh tối màu, đường viền xanh đậm khó thấy nên đổi sang vàng sáng. */}
        {outline?.geometry && <GeoJSON key={`${outline.id}-${layer}`} data={outline.geometry} interactive={false}
          style={sat
            ? { color: '#fde047', weight: 2.5, dashArray: '6 4', fillColor: '#fde047', fillOpacity: 0.04 }
            : { color: '#1748d4', weight: 2, dashArray: '6 4', fillColor: '#1748d4', fillOpacity: 0.05 }} />}
        {heat.map((h, i) => <Circle key={`h${i}`} center={[h.lat, h.lng]} radius={500} interactive={false} pathOptions={{ stroke: false, fillColor: h.n >= 4 ? '#b91c1c' : h.n >= 2 ? '#ea580c' : '#f59e0b', fillOpacity: Math.min(0.55, 0.14 + h.n * 0.09) }} />)}
        {circles.map((c, i) => <Circle key={i} center={[c.lat, c.lng]} radius={c.radius} pathOptions={{ color: c.color, fillColor: c.color, fillOpacity: 0.12, weight: 1 }} />)}
        {markers.map((m) => (
          <Marker key={m.id} position={[m.lat, m.lng]} icon={pin(m)} draggable={!!m.onDragEnd} eventHandlers={markerHandlers(m)}>
            {m.popup && <Popup minWidth={260}>{m.popup}</Popup>}
          </Marker>
        ))}
        {children}
      </MapContainer>
    </div>
  );
}
