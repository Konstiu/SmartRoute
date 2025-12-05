import {Injectable, inject} from '@angular/core';
import {Globals} from '../global/globals';
import {HttpClient} from '@angular/common/http';
import { Observable } from "rxjs";
import {RecommendedActivityDto} from "../app/dtos/recommended-activity";

@Injectable({
  providedIn: 'root',
})
export class TrainingPlanService {
  private readonly httpClient: HttpClient = inject(HttpClient);
  private readonly globals: Globals = inject(Globals);

  private readonly trainingPlanBaseUri: string = this.globals.backendUri + '/training-plan';

  /**
   * Gets the recommended activity for the user for today.
   */
  getTrainingPlan(): Observable<RecommendedActivityDto> {
    return this.httpClient.get<RecommendedActivityDto>(this.trainingPlanBaseUri)
  }

}
