import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {firstValueFrom} from 'rxjs';
import {CreateInjuryStateDto, UpdateInjuryDto, ViewInjuryDto} from "../app/dtos/injuries";
import {Globals} from "../global/globals";


@Injectable({
  providedIn: 'root'
})
export class InjuryService {
  private http: HttpClient = inject(HttpClient);
  private globals: Globals = inject(Globals);

  private baseUri = this.globals.backendUri + '/user/injuries';

  /**
   * Get all injuries for the authenticated user
   * GET /api/v1/user/injuries
   * Returns array of ViewInjuryDto
   */
  async getInjuries(): Promise<ViewInjuryDto[]> {
    try {
      return await firstValueFrom(this.http.get<ViewInjuryDto[]>(this.baseUri));
    } catch (error) {
      console.error('Error fetching injuries:', error);
      throw error;
    }
  }

  /**
   * Create new injury states
   * POST /api/v1/user/injuries
   * Backend expects an array of CreateInjuryStateDto
   * Body: [{ injuryIndex, affectedArea, lastHealthyDate?, lastInjuryDate }]
   */
  async createInjuries(injuries: CreateInjuryStateDto[]): Promise<void> {
    try {
      await firstValueFrom(
        this.http.post<void>(this.baseUri, injuries));
    } catch (error) {
      console.error('Error creating injuries:', error);
      throw error;
    }
  }


  /**
   * Update injury states
   * PUT /api/v1/user/injuries
   * Backend expects an array of UpdateInjuryDto
   * Body: [{ injuryId, injuryIndex, affectedArea, lastHealthyDate?, lastInjuryDate }]
   */
  async updateInjuries(injuries: UpdateInjuryDto[]): Promise<void> {
    try {
      await firstValueFrom(
        this.http.put<void>(this.baseUri, injuries, {
          headers: new HttpHeaders({
            'Content-Type': 'application/json'
          })
        })
      );
    } catch (error) {
      console.error('Error updating injuries:', error);
      throw error;
    }
  }


  /**
   * Delete an injury by ID
   * Note: You may need to add this endpoint to your backend
   * DELETE /api/v1/user/injuries/:id
   */
  async deleteInjury(injuryId: number): Promise<void> {
    try {
      await firstValueFrom(
        this.http.delete<void>(`${this.baseUri}/${injuryId}`)
      );
    } catch (error) {
      console.error('Error deleting injury:', error);
      throw error;
    }
  }

}
