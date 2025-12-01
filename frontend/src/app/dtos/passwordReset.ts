export interface SendPasswordResetDto {
  email: string;
}

export interface ResetPasswordDto {
  token: string;
  password: string;
  repeatPassword: string;
}
