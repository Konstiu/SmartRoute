export function formatDuration(seconds: number): string {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  return `${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
}

export function formatDistance(dist: number): string {
  // convert meters to km
  return `${(dist / 1000).toFixed(2)} km`
}

export function formatPace(averageSpeed: number): string {
  if (averageSpeed <= 0) return "0:00";
  const paceInKmh = averageSpeed * 3.6; // Convert m/s to km/h
  const paceInMinutesPerKm = 60 / paceInKmh;
  let minutes = Math.floor(paceInMinutesPerKm);
  let seconds = Math.round((paceInMinutesPerKm - minutes) * 60);
  // Handle edge case where seconds round up to 60
  if (seconds === 60) {
    minutes += 1;
    seconds = 0;
  }
  return `${minutes}:${seconds.toString().padStart(2, '0')}/km`;
}

export function formatElevation(elevation: number) {
  return `${elevation.toFixed(2)} m`;
}

export function formatHeartRate(heartRate: number): string {
  return `${heartRate.toFixed(0)} bpm`;
}

export function formatTemperature(temperature: number) {
  return `${temperature.toFixed(0)} °C`;
}

export function formatWindSpeed(speed: number) {
  return `${speed.toFixed(0)} km/h`;
}

export function formatWindDirection(direction: string) {
  return "" + direction;
}

export function formatPrecipitation(precipitation: number) {
  return `${precipitation.toFixed(0)} mm`;
}

export function formatInjuryIndex(injuryIndex: number) {
  return `${(injuryIndex * 100).toFixed(0)}%`;
}
