import L, {LatLng} from 'leaflet';

/**
 * Encodes an array of LatLng points into a Google encoded polyline string.
 * Compatible with Google / Mapbox / OSRM polyline format.
 */
export function encodePolyline(latlngs: LatLng[]): string {
  let result = '';
  let lastLat = 0;
  let lastLng = 0;

  for (const point of latlngs) {
    const lat = Math.round(point.lat * 1e5);
    const lng = Math.round(point.lng * 1e5);

    result += encodeSigned(lat - lastLat);
    result += encodeSigned(lng - lastLng);

    lastLat = lat;
    lastLng = lng;
  }

  return result;
}

// Polyline decoding algorithm (Google's encoded polyline format)
export function decodePolyline(encoded: string): L.LatLng[] {
  const points: L.LatLng[] = [];
  let index = 0;
  const len = encoded.length;
  let lat = 0;
  let lng = 0;

  while (index < len) {
    let b: number;
    let shift = 0;
    let result = 0;

    do {
      b = encoded.charCodeAt(index++) - 63;
      result |= (b & 0x1f) << shift;
      shift += 5;
    } while (b >= 0x20);

    const dlat = ((result & 1) !== 0 ? ~(result >> 1) : (result >> 1));
    lat += dlat;

    shift = 0;
    result = 0;

    do {
      b = encoded.charCodeAt(index++) - 63;
      result |= (b & 0x1f) << shift;
      shift += 5;
    } while (b >= 0x20);

    const dlng = ((result & 1) !== 0 ? ~(result >> 1) : (result >> 1));
    lng += dlng;

    points.push(L.latLng(lat / 1e5, lng / 1e5));
  }

  return points;
}

function encodeSigned(value: number): string {
  let sgnNum = value << 1;
  if (value < 0) {
    sgnNum = ~sgnNum;
  }
  return encodeUnsigned(sgnNum);
}

function encodeUnsigned(value: number): string {
  let encoded = '';
  while (value >= 0x20) {
    encoded += String.fromCharCode((0x20 | (value & 0x1f)) + 63);
    value >>= 5;
  }
  encoded += String.fromCharCode(value + 63);
  return encoded;
}
