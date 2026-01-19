import * as L from 'leaflet';

export function coloredMarker(
  color: string,
  options?: Partial<L.DivIconOptions>
): L.DivIcon {
  return L.divIcon({
    className: 'custom-marker',
    html: `
      <svg width="32" height="32" viewBox="0 0 24 24">
        <path
          d="M12 2C8.1 2 5 5.1 5 9c0 5.2 7 13 7 13s7-7.8 7-13c0-3.9-3.1-7-7-7z"
          fill="${color}"
        />
        <circle cx="12" cy="9" r="2.5" fill="white"/>
      </svg>
    `,
    iconSize: [32, 32],
    iconAnchor: [16, 32],
    popupAnchor: [0, -28],
    ...options
  });
}

export type MarkerType =
  | 'start'
  | 'added'
  | 'confirmed'
  | 'route'
  | 'warning';

export const MAP_MARKER_COLORS: Record<MarkerType, string> = {
  start: '#2dd36f',        // user start / location
  added: '#3880ff',        // unconfirmed points
  confirmed: '#ffc409',   // confirmed points
  route: '#7044ff',       // optional route markers
  warning: '#eb445a',     // errors / invalid points
};

