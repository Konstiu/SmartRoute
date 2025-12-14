import {Component, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {GymworkoutService} from '../../../services/gymworkout.service';
import {GymWorkoutDto} from '../../dtos/gymworkout';
import {IonicModule, ModalController} from '@ionic/angular';
import {CommonModule} from '@angular/common';
import {ExerciseDto} from "../../dtos/exercise";
import {ExerciseDetailComponent} from "../exercise-detail/exercise-detail.component";

@Component({
  selector: 'app-gym-workout',
  templateUrl: './gym-workout.page.html',
  styleUrls: ['./gym-workout.page.scss'],
  standalone: true,
  imports: [IonicModule, CommonModule],
})
export class GymWorkoutPage implements OnInit {
  workout: GymWorkoutDto | null = null;
  isLoading = true;
  error: string | null = null;
  day1Exercises: any[] = [];
  day2Exercises: any[] = [];

  constructor(private route: ActivatedRoute, private workoutService: GymworkoutService, private modalCtrl: ModalController) {
  }

  ngOnInit() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!id) {
      this.error = 'Invalid workout ID.';
      this.isLoading = false;
      return;
    }

    this.workoutService.getGymWorkoutById(id).subscribe({
      next: (data) => {
        console.log(data);
        this.workout = data;
        this.isLoading = false;
        if (this.workout.exercises.length > 6) {
          const half = Math.ceil(this.workout.exercises.length / 2);
          this.day1Exercises = this.workout.exercises.slice(0, half);
          this.day2Exercises = this.workout.exercises.slice(half);
        } else {
          this.day1Exercises = this.workout.exercises;
          this.day2Exercises = [];
        }
      },
      error: (err) => {
        console.error(err);
        this.error = 'Failed to load workout details.';
        this.isLoading = false;
      },
    });
  }

  onImageError(event: Event) {
    const imgElement = event.target as HTMLImageElement;
    imgElement.src = 'assets/noimage.png';
  }

  async openExercise(exercise: ExerciseDto) {
    const modal = await this.modalCtrl.create({
      component: ExerciseDetailComponent,
      componentProps: {exercise},

    });
    await modal.present();
  }


}
