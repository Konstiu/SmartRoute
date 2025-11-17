import { Globals } from "../global/globals";
import { HttpClient } from "@angular/common/http";
import { CreateUserDto, PersonalDataDto, UserDetailDto, UserDto } from "../app/dtos/user";
import { Observable } from "rxjs";
import { inject, Injectable } from "@angular/core";

@Injectable({ providedIn: "root" })
export class UserService {
  private httpClient = inject(HttpClient);
  private globals = inject(Globals);
  private userUri: string = this.globals.backendUri + '/user';


  createUser(toCreate: CreateUserDto): Observable<UserDto> {
    return this.httpClient.post<UserDto>(this.userUri, toCreate, { responseType: 'json' });
  }

  updatePersonalData(updateInformation: PersonalDataDto): Observable<UserDetailDto> {
    return this.httpClient.put<UserDetailDto>(this.userUri + "/personal-data", updateInformation, { responseType: 'json' });
  }
}
