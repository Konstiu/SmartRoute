interface BaseUser {
  firstname: string;
  lastname: string;
  email: string;
}

export interface CreateUserDto extends BaseUser {
  password: String;
}

export class UserDto implements BaseUser {
  id!: number;
  firstname!: string;
  lastname!: string;
  email!:string;




}
