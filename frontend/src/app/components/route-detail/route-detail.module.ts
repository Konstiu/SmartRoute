import {NgModule} from "@angular/core";
import {CommonModule} from "@angular/common";
import {FormsModule} from "@angular/forms";
import {IonicModule} from "@ionic/angular";
import {RouterModule} from "@angular/router";
import {GymWorkoutPage} from "../gym-workout/gym-workout.page";
import {ExerciseDetailComponent} from "../exercise-detail/exercise-detail.component";
import {RouteDetailPage} from "./route-detail.page";

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    RouterModule.forChild([{path: '', component: RouteDetailPage}]),
    GymWorkoutPage,
    ExerciseDetailComponent,
  ],
  declarations: [],
})
export class RouteDetailModule {
}
