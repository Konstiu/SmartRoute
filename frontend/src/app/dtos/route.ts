export interface GeneratedRouteDto {
  bbox: number[],
  polyline: string,
  coordinates3d: Array<[number, number, number | null]>;
  distance: number,
  elevation: number,
}
