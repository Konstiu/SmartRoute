export type SyncState = 'RUNNING' | 'SUCCESS' | 'FAILED';

export interface SyncStatusDto {
  state: SyncState;
  message?: string | null;
}

export type SyncOutcome =
  | { kind: 'success' }
  | { kind: 'running' }
  | { kind: 'failed'; message?: string | null }
  | { kind: 'unknown' }; // couldn't confirm (status endpoint failed)

