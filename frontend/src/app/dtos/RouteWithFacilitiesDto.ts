export interface GeoJsonPosition {
  latitude: number;
  longitude: number;
  altitude?: number | null;
}

export interface RouteWithFacilityDefaults {
  originalRoute: GeoJsonPosition[];
  includeToilets: boolean;
  toiletIntervalMeters: number;
  includeFountains: boolean;
  fountainIntervalMeters: number;
  maxFacilityDistance: number;
}
