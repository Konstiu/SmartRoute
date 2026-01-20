export interface RouteWithFacilityDefaults {
  originalRoute: string;
  includeToilets: boolean;
  toiletIntervalMeters: number;
  includeFountains: boolean;
  fountainIntervalMeters: number;
  maxFacilityDistance: number;
}

export interface RouteWithFacilitiesResponseDto {
  polyline: string;
  distance: number;
  originalDistance: number;
  distanceAdded: number;
  facilitiesAdded: number;
  totalPoints: number;
}
