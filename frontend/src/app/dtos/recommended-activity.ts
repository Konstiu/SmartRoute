import {GymWorkoutDto} from "./gymworkout";
import {ViewInjuryDto} from "./injuries";
import {LatLng, Polyline} from "leaflet";

export interface RecommendedActivityDto {
  name: string;
  type: SessionType,
  route?: {
    distance: number;
    pace: number;
    elevation: number;
  },
  gymSession?: GymWorkoutDto;
  weather: {
    weatherScore: number;
    temperature: number;
    windSpeed: number;
    precipitation: number;
    relativeHumidity: number;
    weatherPerformancePenalty: number;
    weatherScoreDescription: string;
    weatherSummary: WeatherSummaryDto;
  },
  athleteStatus: {
    tsb: number;
    readinessScore: number;
    injuryIndex: number;
    injuries?: [ViewInjuryDto];
  }
}

export enum SessionType {
  RUN = "RUN",
  GYM = "GYM",
  REST = "REST",
}

export interface WeatherSummaryDto {
  temperatureText: string;
  windText: string;
  precipitationText: string;
}

export interface SaveRouteDto {
  name: string;
  distance: number;
  pace: number;
  elevation: number;
  route: string; // Encoded Polyline
}

export interface ViewRouteDto {
  id: number
  name: string;
  distance: number;
  pace: number;
  elevation: number;
  route: string; // Encoded Polyline
}




