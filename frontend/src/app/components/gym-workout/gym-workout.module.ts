import {NgModule} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {IonicModule} from '@ionic/angular';
import {GymWorkoutPage} from './gym-workout.page';
import {RouterModule} from '@angular/router';
import {ExerciseDetailComponent} from '../exercise-detail/exercise-detail.component';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    RouterModule.forChild([{path: '', component: GymWorkoutPage}]),
    GymWorkoutPage,
    ExerciseDetailComponent,
  ],
  declarations: [],
})
export class GymWorkoutPageModule {
}
