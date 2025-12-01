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
