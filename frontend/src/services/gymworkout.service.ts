import {inject, Injectable} from "@angular/core";
import {HttpClient} from "@angular/common/http";
import {Globals} from "../global/globals";
import {Observable} from "rxjs";
import {GymWorkoutDto} from "../app/dtos/gymworkout";

@Injectable({providedIn: 'root'})
export class GymworkoutService {
    private http = inject(HttpClient);
    private globals = inject(Globals);

    private baseUri = this.globals.backendUri + '/gym';

    /**
     * Get all Gym Workouts from the user.
     * Endpoint: POST {backendUri}/gym/
     *
     */
    getAllGymWorkouts(): Observable<GymWorkoutDto[]> {
        const url = `${this.baseUri}`;

        return this.http.get<GymWorkoutDto[]>(url);
    }

    /**
     * Generate a new Gym workout for the user
     * Endpoint: POST {backendUri}/gym/generate
     */
    generateGymWorkout(): Observable<GymWorkoutDto> {
        const url = `${this.baseUri}/generate`;

        return this.http.get<GymWorkoutDto>(url);
    }

    /**
     * Get one Gymworkout by id
     * @param id the id of the gymworkout
     */
    getGymWorkoutById(id: Number): Observable<GymWorkoutDto> {
        const url = `${this.baseUri}/get/${id}`;
        return this.http.get<GymWorkoutDto>(url);
    }
}
