interface BaseUser {
  firstname: string;
  lastname: string;
  email: string;
}

export interface CreateUserDto extends BaseUser {
  password: string;
}

export class UserDto implements BaseUser {
  id!: number;
  firstname!: string;
  lastname!: string;
  email!: string;
}

export class PersonalDataDto {
  sex!: string;
  height!: number;
  weight!: number;
  birthdate!: Date;
  experienceLevel!: string;
  activeWeekdays!: [string];
}

export class UserDetailDto {
  firstname!: string;
  lastname!: string;
  email!: string;
  sex!: string;
  height!: number;
  weight!: number;
  birthdate!: Date;
  experienceLevel!: string;
  activeWeekdays!: [string];
}



export interface ConsistencyData {
  finalScore: number;
  frequencyConsistency: number;
  regularityConsistency: number;
}


export interface Injury {
  injuryId?: number;
  injuryIndex: number;
  affectedArea: string;
  lastHealthyDate: string;
  lastInjuryDate: string;
}
export interface InjuryHistory {
  noOfInjuries?: number;
  injuriesList: Injury[];
}

export interface StatisticalData {
  consistencyHistory: {
    consistencyHistory: { [key: string]: ConsistencyData };
    ctlHistory: { [key: string]: number };
    atlHistory: { [key: string]: number };
    tsbHistory: { [key: string]: number };
  };
  injuryHistory: {
    injuriesList: Injury[];
  };
  gymHistory: any;
}
