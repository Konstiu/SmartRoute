export function convertPolylineToCoordinateList(polyline: string): number[][] {
  if (polyline.length == 0)
    return [];
  let i = 0;
  let coords = [];

  function convertCoordinate(): number {
    let coord = 0;
    let shift = 0;

    let char = 0x20;
    while (char >= 0x20) {
      char = polyline.charCodeAt(i) - 63;
      coord |= (char & 0b11111) << shift;
      shift += 5;
      i++;
    }

    return ((coord & 1) ? ~coord : coord) >> 1;
  }

  let lat = 0;
  let long = 0;
  while (i < polyline.length) {
    lat += convertCoordinate();
    long += convertCoordinate();
    coords.push([lat / 1e6, long / 1e6])
  }

  return coords;
}
