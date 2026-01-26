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

  getNext7Days(lat: number, lng: number, opts?: {
    debug?: boolean;
    sims?: number;
    seed?: number;
    regen?: boolean;
    historyDays?: number;
    historyMean?: number;
    historyStd?: number;
    ctl?: number;
    atl?: number;
    readiness?: number;
    injuryIndex?: number;
  }) {
    let params: any = {
      latitude: lat,
      longitude: lng,
    };

    if (opts?.debug != null) params.debug = String(opts.debug);
    if (opts?.seed != null) params.seed = String(opts.seed);
    if (opts?.sims != null) params.sims = String(opts.sims);
    if (opts?.regen != null) params.regen = String(opts.regen);

    if (opts?.historyDays != null) params.historyDays = String(opts.historyDays);
    if (opts?.historyMean != null) params.historyMean = String(opts.historyMean);
    if (opts?.historyStd != null) params.historyStd = String(opts.historyStd);

    if (opts?.ctl != null) params.ctl = String(opts.ctl);
    if (opts?.atl != null) params.atl = String(opts.atl);

    if (opts?.readiness != null) params.readiness = String(opts.readiness);
    if (opts?.injuryIndex != null) params.injuryIndex = String(opts.injuryIndex);

    console.log('getNext7Days params', params);
    return this.httpClient.get<TrainingPlan7dDto>(this.baseUri + '/next-7-days', { params });
  }
}
