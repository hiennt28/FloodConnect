/*
 * Cấu hình nhà cung cấp bản đồ (đặt trong file .env ở thư mục gốc):
 *   VITE_MAP_PROVIDER=esri | openfreemap | osm | goong | maptiler
 *   VITE_MAP_API_KEY=<khóa API, chỉ cần với goong / maptiler>
 * Miễn phí, không cần khóa: esri (địa hình, mặc định), openfreemap (vector), osm.
 */
const provider = (import.meta.env.VITE_MAP_PROVIDER || 'esri').toLowerCase();
const key = import.meta.env.VITE_MAP_API_KEY || '';

const PROVIDERS = {
  openfreemap: {
    style: 'https://tiles.openfreemap.org/styles/liberty',
    attribution: '<a href="https://openfreemap.org" target="_blank">OpenFreeMap</a> © <a href="https://www.openstreetmap.org/copyright" target="_blank">OpenStreetMap</a>',
    vietnameseLabels: true,
    islandLabels: true,
  },
  osm: {
    type: 'raster',
    url: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
    attribution: '© <a href="https://www.openstreetmap.org/copyright" target="_blank">OpenStreetMap</a>',
    islandLabels: true,
  },
  esri: {
    type: 'raster',
    url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}',
    attribution: 'Tiles © Esri — Esri, HERE, Garmin, USGS, Intermap, INCREMENT P, NRCAN, METI, NASA, NOAA, OpenStreetMap contributors',
    islandLabels: true,
  },
  goong: {
    // Goong (Việt Nam): nhãn tiếng Việt, thể hiện quần đảo Hoàng Sa, Trường Sa
    style: `https://tiles.goong.io/assets/goong_map_web.json?api_key=${key}`,
    attribution: '© <a href="https://goong.io" target="_blank">Goong</a>',
    transformRequest: (url) => (url.includes('goong.io') && !url.includes('api_key=')
      ? { url: `${url}${url.includes('?') ? '&' : '?'}api_key=${key}` } : { url }),
    islandLabels: false,
  },
  maptiler: {
    style: `https://api.maptiler.com/maps/streets-v2/style.json?key=${key}`,
    attribution: '© <a href="https://www.maptiler.com/copyright/" target="_blank">MapTiler</a> © OpenStreetMap',
    vietnameseLabels: true,
    islandLabels: true,
  },
};

const needsKey = ['goong', 'maptiler'].includes(provider);
export const MAP = PROVIDERS[provider] && (!needsKey || key) ? PROVIDERS[provider] : PROVIDERS.openfreemap;
export const MAP_PROVIDER = MAP === PROVIDERS[provider] ? provider : 'openfreemap';

/*
 * Lớp "Vệ tinh" cho nút chuyển Bản đồ / Vệ tinh (MapView prop layerSwitch). Esri, miễn phí, không cần khóa:
 * ảnh vệ tinh + tên đường + tên địa danh. Tên địa danh chỉ hiện khi phóng to (từ zoom PLACES_MIN_ZOOM) để không hiện
 * nhãn cấp quốc gia/biển đảo của Esri; tên Hoàng Sa, Trường Sa vẫn do lớp riêng của MapView vẽ.
 * Ảnh vệ tinh được chụp từ trước, không thể hiện tình trạng ngập hiện tại: chỉ giúp nhận ra nhà cửa, đường sá khi đặt ghim.
 */
export const SATELLITE = {
  imagery: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
  roads: 'https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Transportation/MapServer/tile/{z}/{y}/{x}',
  places: 'https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}',
  attribution: 'Tiles © Esri — Esri, Maxar, Earthstar Geographics, GIS User Community',
  PLACES_MIN_ZOOM: 10,
};

/* Lớp dự phòng nếu bản đồ vector không tải được */
/* (CARTO đã yêu cầu khóa API nên dùng tile chuẩn của OpenStreetMap) */
export const FALLBACK_RASTER = PROVIDERS.osm;
