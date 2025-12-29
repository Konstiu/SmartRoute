export type Sanitary = 'Toilet' | 'Fountain';

export interface Coordinate {
  latitude: number;
  longitude: number;
}

export interface ViennaPointDto {
  id: string;
  coordinate: Coordinate;
  type: Sanitary;
}
