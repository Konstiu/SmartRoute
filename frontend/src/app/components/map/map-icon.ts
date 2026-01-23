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

// Super simple emoji markers with colored backgrounds
export function emojiMarker(
  type: 'toilet' | 'fountain',
  options?: Partial<L.DivIconOptions>
): L.DivIcon {
  const config = {
    toilet: {
      emoji: '🚻',
    },
    fountain: {
      emoji: '🚰',
    }
  }[type];

  return L.divIcon({
    className: 'emoji-marker',
    html: `<div class="emoji-marker-icon">${config.emoji}</div>`,
    iconSize: [32, 32],
    iconAnchor: [16, 16],
    popupAnchor: [0, -16],
    ...options
  });
}

export type MarkerType =
  | 'start'
  | 'added'
  | 'confirmed'
  | 'route'
  | 'warning'
  | 'toilet'
  | 'fountain';

export const MAP_MARKER_COLORS: Record<MarkerType, string> = {
  start: '#2dd36f',
  added: '#3880ff',
  confirmed: '#ffc409',
  route: '#7044ff',
  warning: '#eb445a',
  toilet: '#8B5CF6',
  fountain: '#3B82F6',
};
