import {Injectable, inject} from '@angular/core';
import {Globals} from '../global/globals';
import {HttpClient} from '@angular/common/http';
import {Observable} from "rxjs";
import { TrainingPlan7dDto } from 'src/app/dtos/training-plan-7d';
import { RecommendedActivityDto } from 'src/app/dtos/recommended-activity';

@Injectable({ providedIn: 'root' })
export class TrainingPlan7dService {
  private readonly httpClient: HttpClient = inject(HttpClient);
  private readonly globals = inject(Globals);

  private readonly baseUri = this.globals.backendUri + '/training-plan-7-days';

  getNext7Days(
    latitude: number,
    longitude: number,
    debug?: boolean,
    sims?: number,
    seed?: number,
  ): Observable<TrainingPlan7dDto> {
    return this.httpClient.get<TrainingPlan7dDto>(this.baseUri + '/next-7-days', {
            params: {
                "latitude": latitude,
                "longitude": longitude,
            }
         })
  }
}
