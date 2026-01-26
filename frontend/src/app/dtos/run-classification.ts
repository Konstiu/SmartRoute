export interface RunClassificationDto {
  runType: RunType;
  probabilities: Record<RunType, number>;
}

export enum RunType {
  EASY_RUN = 'EASY_RUN',
  LONG_RUN = 'LONG_RUN',
  INTERVAL_RUN = 'INTERVAL_RUN',
  TEMPO_RUN = 'TEMPO_RUN'
}

export const RunTypeLabel: Record<RunType, string> = {
  [RunType.EASY_RUN]: 'Easy Run',
  [RunType.LONG_RUN]: 'Long Run',
  [RunType.INTERVAL_RUN]: 'Interval Run',
  [RunType.TEMPO_RUN]: 'Tempo Run'
};
