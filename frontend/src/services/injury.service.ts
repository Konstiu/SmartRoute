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
      return await firstValueFrom(
        this.http.get<ViewInjuryDto[]>(this.baseUri)
      );
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
        this.http.post<void>(this.baseUri, injuries, {
          headers: new HttpHeaders({
            'Content-Type': 'application/json'
          })
        })
      );
    } catch (error) {
      console.error('Error creating injuries:', error);
      throw error;
    }
  }

  /**
   * Create a single injury
   * Convenience method that wraps createInjuries
   */
  async createInjury(injury: CreateInjuryStateDto): Promise<void> {
    return this.createInjuries([injury]);
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
   * Update a single injury
   * Convenience method that wraps updateInjuries
   */
  async updateInjury(injury: UpdateInjuryDto): Promise<void> {
    return this.updateInjuries([injury]);
  }

  /**
   * Delete an injury by ID
   * Note: You may need to add this endpoint to your backend
   * DELETE /api/v1/user/injuries/:id
   */
  async deleteInjury(injuryId: number): Promise<void> {
    try {
      await firstValueFrom(
        this.http.delete<void>(`${this.baseUri}/injuryId}`)
      );
    } catch (error) {
      console.error('Error deleting injury:', error);
      throw error;
    }
  }

  /**
   * Get injury by ID
   * Note: You may need to add this endpoint to your backend
   * GET /api/v1/user/injuries/:id
   */
  async getInjuryById(injuryId: number): Promise<ViewInjuryDto> {
    try {
      return await firstValueFrom(
        this.http.get<ViewInjuryDto>(`${this.baseUri}/injuries/${injuryId}`)
      );
    } catch (error) {
      console.error('Error fetching injury:', error);
      throw error;
    }
  }

  /**
   * Get injuries by body part (affected area)
   * Filters injuries locally by affectedArea
   */
  async getInjuriesByBodyPart(affectedArea: string): Promise<ViewInjuryDto[]> {
    const injuries = await this.getInjuries();
    return injuries.filter(injury => injury.affectedArea === affectedArea);
  }

  /**
   * Get injuries by severity (based on injury index)
   * @param severity 'MILD' (0-0.33), 'MODERATE' (0.33-0.67), 'SEVERE' (0.67-1.0)
   */
  async getInjuriesBySeverity(severity: 'MILD' | 'MODERATE' | 'SEVERE'): Promise<ViewInjuryDto[]> {
    const injuries = await this.getInjuries();
    return injuries.filter(injury => {
      if (severity === 'MILD') return injury.injuryIndex < 0.33;
      if (severity === 'MODERATE') return injury.injuryIndex >= 0.33 && injury.injuryIndex < 0.67;
      if (severity === 'SEVERE') return injury.injuryIndex >= 0.67;
      return false;
    });
  }

  /**
   * Check if user has any active injuries
   */
  async hasActiveInjuries(): Promise<boolean> {
    const injuries = await this.getInjuries();
    return injuries.length > 0;
  }

  /**
   * Get count of injuries by severity
   */
  async getInjuryCountBySeverity(): Promise<{ mild: number; moderate: number; severe: number }> {
    const injuries = await this.getInjuries();
    return injuries.reduce((acc, injury) => {
      if (injury.injuryIndex < 0.33) {
        acc.mild++;
      } else if (injury.injuryIndex < 0.67) {
        acc.moderate++;
      } else {
        acc.severe++;
      }
      return acc;
    }, {mild: 0, moderate: 0, severe: 0});
  }

  /**
   * Get active (not recovered) injuries
   * An injury is considered active if lastHealthyDate is null or before lastInjuryDate
   */
  async getActiveInjuries(): Promise<ViewInjuryDto[]> {
    const injuries = await this.getInjuries();
    return injuries.filter(injury => {
      if (!injury.lastHealthyDate) return true;
      return new Date(injury.lastHealthyDate) <= new Date(injury.lastInjuryDate);
    });
  }

  /**
   * Get recovered injuries
   * An injury is considered recovered if lastHealthyDate is after lastInjuryDate
   */
  async getRecoveredInjuries(): Promise<ViewInjuryDto[]> {
    const injuries = await this.getInjuries();
    return injuries.filter(injury => {
      if (!injury.lastHealthyDate) return false;
      return new Date(injury.lastHealthyDate) > new Date(injury.lastInjuryDate);
    });
  }
}
