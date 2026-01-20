export interface GeoJsonPosition {
  latitude: number;
  longitude: number;
  altitude?: number | null;
}

export interface AddStopsRequest {
  originalRoute: GeoJsonPosition[];
  newPoints: GeoJsonPosition[];
}
