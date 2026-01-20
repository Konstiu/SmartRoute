import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

export interface GeocodeResult {
  display_name: string;
  lat: string;
  lon: string;
}

@Injectable({ providedIn: 'root' })
export class GeocodingService {
  private readonly baseUrl = 'https://nominatim.openstreetmap.org/search';

  constructor(private http: HttpClient) {}

  async search(query: string): Promise<GeocodeResult[]> {
    const q = query.trim();
    if (!q) return [];

    const params = new HttpParams()
      .set('q', q)
      .set('format', 'json')
      .set('addressdetails', '0')
      .set('limit', '5');

    // Nominatim expects a proper User-Agent; browsers send one automatically,
    // but still: avoid hammering it, debounce calls.
    return firstValueFrom(this.http.get<GeocodeResult[]>(this.baseUrl, { params }));
  }
}
