import {NgModule} from '@angular/core';
import {RouterModule, Routes} from '@angular/router';
import {RoutePage} from './route.page';
import {GymWorkoutTabPage} from "../gym-workout-tab/gym-workout-tab.page";

const routes: Routes = [
  {
    path: '',
    component: RoutePage
  },
  {
    path: ':id',
    loadChildren: () => import('../route-detail/route-detail.module')
      .then(m => m.RouteDetailModule)
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class RoutePageRoutingModule {
}
