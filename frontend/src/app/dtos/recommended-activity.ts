export interface RecommendedActivityDto {
  title: string;
  type: SessionType,
  route?: {
    distance: number;
    pace: number;
    elevation: number;
    description?: string;
  },
  gymSession?: {
    description?: string;
  }
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
    injuries?: [Injury];
  }
}

interface Injury{
  title: string;
}

export enum SessionType {
  RUN,
  GYM,
  REST,
}
