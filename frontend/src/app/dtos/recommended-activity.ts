import {GymWorkoutDto} from "./gymworkout";
import {ViewInjuryDto} from "./injuries";

export interface RecommendedActivityDto {
  title: string;
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
    windDirection: string;
    precipitation: number;
    relativeHumidity: number;
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
  RUN,
  GYM,
  REST,
}
