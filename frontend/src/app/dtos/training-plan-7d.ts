export type WorkoutType =
  | "EASY_RUN"
  | "TEMPO_RUN"
  | "INTERVAL_RUN"
  | "LONG_RUN"
  | "GYM_PREHAB"
  | "MOBILITY"
  | "REST_DAY";

export interface LoadDistributionDto {
  p10: number;
  p50: number;
  p90: number;
  mean: number;
  std: number;
}

export interface RouteDto {
  distance: number | null;
  pace: number | null;
  elevation: number | null;
  seed: number;
}

export interface CompactWeatherDto {
  weatherScore: number | null;
  temperature: number | null;
  windSpeed: number | null;
  precipitation: number | null;
  relativeHumidity: number | null;
  weatherPerformancePenalty: number | null;
  weatherScoreDescription: string | null;
  weatherSummary: any | null;
}

export interface PlannedDayDto {
  date: string; // LocalDate from backend -> "YYYY-MM-DD"
  workoutType: WorkoutType;
  load: LoadDistributionDto;
  tsb: LoadDistributionDto;
  weatherDto?: CompactWeatherDto | null;
  confidence: "high" | "medium" | "low";
  explanation: string[];
  gymWorkout: any | null;
  routeDto: RouteDto | null;
}

export interface TrainingPlan7dDto {
  planId: string;
  days: PlannedDayDto[];
  debug?: any | null;
}
