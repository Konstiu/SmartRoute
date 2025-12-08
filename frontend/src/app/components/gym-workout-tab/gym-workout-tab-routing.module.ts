import {NgModule} from '@angular/core';
import {RouterModule, Routes} from '@angular/router';
import {GymWorkoutTabPage} from './gym-workout-tab.page';

const routes: Routes = [
  {
    path: '',
    component: GymWorkoutTabPage
  },
  {
    path: ':id',
    loadChildren: () => import('../gym-workout/gym-workout.module')
      .then(m => m.GymWorkoutPageModule)
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class GymWorkoutTabPageRoutingModule {

}
