import {Globals} from "../global/globals";
import {HttpClient} from "@angular/common/http";
import {CreateUserDto, UserDto} from "../app/dtos/user";
import {Observable} from "rxjs";
import {inject, Injectable} from "@angular/core";
import {SendPasswordResetDto, ResetPasswordDto} from "../app/dtos/passwordReset";

@Injectable({providedIn: "root"})
export class UserService {
  private httpClient = inject(HttpClient);
  private globals = inject(Globals);
  private userUri: string = this.globals.backendUri + '/user';


  createUser(toCreate: CreateUserDto): Observable<UserDto> {
    return this.httpClient.post<UserDto>(this.userUri, toCreate, {responseType: 'json'});
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
}
