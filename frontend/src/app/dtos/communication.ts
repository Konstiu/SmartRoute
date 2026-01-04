export interface OneTimePreKeyDto {
  uuid: string;
  publicKey: string;
}

export interface KeysDto {
  identityKey: string,
  signedPreKey: string,
  signedPreKeySignature: string,
  oneTimePreKey: OneTimePreKeyDto | null
}