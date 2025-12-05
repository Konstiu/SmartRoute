import {ExerciseDto} from "./exercise";

export interface GymWorkoutDto {
  id: number;
  exercises: ExerciseDto[];
  sets: number;
  reps: number;
}
