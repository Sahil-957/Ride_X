// Leaflet + OpenStreetMap helpers. Routing itself happens on the server (see RoutingService).
const RxMap = (() => {
  const DEFAULT_CENTER = [19.076, 72.8777]; // Mumbai, used until the browser shares a location

  function create(elementId, center = DEFAULT_CENTER, zoom = 13) {
    const map = L.map(elementId, { zoomControl: true }).setView(center, zoom);
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    }).addTo(map);
    return map;
  }

  function icon(kind) {
    const glyph = { pickup: 'bi-person-fill', drop: 'bi-flag-fill', driver: 'bi-scooter' }[kind];
    return L.divIcon({
      className: '',
      html: `<div class="rx-pin ${kind}"><i class="bi ${glyph}"></i></div>`,
      iconSize: [30, 30],
      iconAnchor: kind === 'driver' ? [15, 15] : [4, 30],
    });
  }

  /** Creates the marker on first call, moves it afterwards. */
  function placeMarker(map, existing, kind, latLng, options = {}) {
    if (existing) {
      existing.setLatLng(latLng);
      return existing;
    }
    return L.marker(latLng, { icon: icon(kind), ...options }).addTo(map);
  }

  /** Draws (or replaces) the route polyline. `path` is an array of [lat, lng]. */
  function drawRoute(map, existing, path) {
    if (existing) map.removeLayer(existing);
    if (!path || path.length < 2) return null;
    const line = L.polyline(path, { color: '#111827', weight: 5, opacity: .85 }).addTo(map);
    map.fitBounds(line.getBounds(), { padding: [40, 40] });
    return line;
  }

  function fit(map, latLngs) {
    const points = latLngs.filter(Boolean);
    if (points.length === 1) map.setView(points[0], 15);
    else if (points.length > 1) map.fitBounds(L.latLngBounds(points), { padding: [40, 40] });
  }

  /** Browser GPS as a promise (rejects if the user denies permission). */
  function currentPosition() {
    return new Promise((resolve, reject) => {
      if (!navigator.geolocation) return reject(new Error('Location is not available in this browser.'));
      navigator.geolocation.getCurrentPosition(
        (p) => resolve([p.coords.latitude, p.coords.longitude]),
        () => reject(new Error('Location permission denied. You can tap the map instead.')),
        { enableHighAccuracy: true, timeout: 10000 });
    });
  }

  return { DEFAULT_CENTER, create, icon, placeMarker, drawRoute, fit, currentPosition };
})();
