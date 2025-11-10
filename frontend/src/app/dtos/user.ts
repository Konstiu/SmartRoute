interface BaseUser {
  firstname: string;
  lastname: string;
  email: string;
}

export interface CreateUserDto extends BaseUser {
  password: String;
}
