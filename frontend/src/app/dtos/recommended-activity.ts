import {GymWorkoutDto} from "./gymworkout";
import {ViewInjuryDto} from "./injuries";

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
     // Placeholder fields until backend generator is merged
     temperatureDescription?: string;
     windDescription?: string;
     precipitationDescription?: string;

    description?: string;
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

