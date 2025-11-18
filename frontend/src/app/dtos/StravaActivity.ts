export interface DetailedStravaActivity {
  id: number;
  name: string;
  distance: number;
  movingTime: number;
  elapsedTime: number;
  totalElevationGain: number;
  type: string;
  sportType: string;
  startDate: string;
  startDateLocal: string;
  averageSpeed: number;
  maxSpeed: number;
  averageHeartrate: number | null;
  maxHeartrate: number | null;
  averageWatts: number | null;
  kilojoules: number | null;
  summaryPolyline: string|null;
}

export interface StravaActivity {
  id: number;
  name: string;
  distance: number;
  movingTime: number;
  totalElevationGain: number;
  sportType: string;
  startDateLocal: string;
  averageSpeed: number;
  averageHeartrate: number | null;
}
