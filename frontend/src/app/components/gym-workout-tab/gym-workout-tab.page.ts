import {Component, OnInit} from '@angular/core';
import {IonicModule} from '@ionic/angular';
import {CommonModule} from '@angular/common';
import {Router} from '@angular/router';
import {GymworkoutService} from '../../../services/gymworkout.service';
import {GymWorkoutDto} from '../../dtos/gymworkout';

@Component({
  selector: 'app-gym-workout-tab',
  templateUrl: './gym-workout-tab.page.html',
  styleUrls: ['./gym-workout-tab.page.scss'],
  standalone: true,
  imports: [IonicModule, CommonModule],
})
export class GymWorkoutTabPage implements OnInit {
  gymWorkouts: GymWorkoutDto[] = [];
  isLoading = false;
  error: string | null = null;

  constructor(private gymWorkoutService: GymworkoutService, private router: Router) {
  }

  ngOnInit() {
    this.loadWorkouts();
  }

  loadWorkouts(event?: any) {
    this.isLoading = true;
    this.error = null;

    this.gymWorkoutService.getAllGymWorkouts().subscribe({
      next: (data) => {
        this.gymWorkouts = data.sort((a, b) => b.id - a.id); // latest first
        this.isLoading = false;
        if (event) event.target.complete();
      },
      error: (err) => {
        console.error(err);
        this.error = 'Failed to load gym workouts.';
        this.isLoading = false;
        if (event) event.target.complete();
      },
    });
  }

  doRefresh(event: any) {
    this.loadWorkouts(event);
  }

  generateWorkout() {
    this.isLoading = true;
    this.gymWorkoutService.generateGymWorkout().subscribe({
      next: (newWorkout) => {
        this.gymWorkouts = [newWorkout, ...this.gymWorkouts];
        this.isLoading = false;
      },
      error: () => {
        this.error = 'Could not generate workout.';
        this.isLoading = false;
      },
    });
  }

  openWorkout(workout: GymWorkoutDto) {
    this.router.navigate(['/tabs/gym', workout.id]);
  }
}
