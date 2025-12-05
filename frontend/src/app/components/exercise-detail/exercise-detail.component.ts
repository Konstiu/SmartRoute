import {Component, Input} from '@angular/core';
import {IonicModule, ModalController} from '@ionic/angular';
import {ExerciseDto} from '../../dtos/exercise';

@Component({
  selector: 'app-exercise-detail',
  templateUrl: './exercise-detail.component.html',
  styleUrls: ['./exercise-detail.component.scss'],
  imports: [
    IonicModule
  ]
})
export class ExerciseDetailComponent {
  @Input() exercise!: ExerciseDto;

  constructor(private modalCtrl: ModalController) {
  }

  dismiss() {
    this.modalCtrl.dismiss();
  }
}
