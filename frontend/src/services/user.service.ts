import { Globals } from "../global/globals";
import { HttpClient } from "@angular/common/http";
import {CreateUserDto, PersonalDataDto, StatisticalData, UserDetailDto, UserDto} from "../app/dtos/user";
import { BehaviorSubject, first, Observable } from "rxjs";
import { inject, Injectable } from "@angular/core";
import { SendPasswordResetDto, ResetPasswordDto } from "../app/dtos/passwordReset";

@Injectable({ providedIn: "root" })
export class UserService {
  private httpClient = inject(HttpClient);
  private globals = inject(Globals);
  private userUri: string = this.globals.backendUri + '/user';

  // https://forum.ionicframework.com/t/refresh-data-after-redirect-go-back/211479
  private lastPersonalData = new BehaviorSubject<UserDetailDto | null>(null);

  /**
   * Update personal user data like height, weight, etc.
   */
  updatePersonalData(updateInformation: PersonalDataDto): Observable<UserDetailDto> {
    let data = this.httpClient.put<UserDetailDto>(this.userUri + "/personal-data", updateInformation, { responseType: 'json' })
    data.pipe(first()).subscribe({
      next: (data: UserDetailDto) => {
        this.lastPersonalData?.next(data);
      }
    });
    return data;
  }

  watchPersonalData(): BehaviorSubject<UserDetailDto | null> {
    return this.lastPersonalData;
  }

  /**
   * Retrieve the authenticated user's personal data from the backend.
   *
   * Performs an HTTP GET to `${this.userUri}/personal-data` and returns an Observable that emits the user's details.
   * Emits a single UserDetailDto on success and then completes.
   *
   * @returns {Observable<UserDetailDto>} An observable that yields the user's personal data.
   * @example
   * this.userService.getUserData().subscribe({
   *   next: data => console.log('User data', data),
   *   error: err => console.error('Failed to load user data', err)
   * });
   */
  getUserData(): Observable<UserDetailDto> {
    return this.httpClient.get<UserDetailDto>(this.userUri + "/personal-data");
  }

  /**
   * Create a new User
   * Endpoint: POST /api/v1/user/
   * Body: {
   *    "firstname": string,
   *    "lastname": string,
   *    "email: string",
   *    "password": string
   *    }
   */
  createUser(toCreate: CreateUserDto): Observable<UserDto> {
    return this.httpClient.post<UserDto>(this.userUri, toCreate, { responseType: 'json' });
  }

  /**
   * Request a password reset email
   */
  requestPasswordReset(dto: SendPasswordResetDto): Observable<void> {
    return this.httpClient.post<void>(`${this.userUri}/reset_password`, dto);
  }

  /**
   * Reset password with token
   * Endpoint: POST /api/v1/user/reset_password/{token}
   * Body: { "password": "string", "repeatPassword": "string" }
   */
  resetPasswordWithToken(dto: ResetPasswordDto): Observable<void> {
    return this.httpClient.post<void>(
      `${this.userUri}/reset_password/${dto.token}`,
      {
        password: dto.password,
        repeatPassword: dto.repeatPassword
      }
    );
  }

  deleteAccount(): Observable<any> {
    return this.httpClient.delete(`${this.userUri}/account`);
  }

  getStats(): Observable<StatisticalData>{
    return this.httpClient.get<StatisticalData>(`${this.userUri}/statistics`)
  }
}
