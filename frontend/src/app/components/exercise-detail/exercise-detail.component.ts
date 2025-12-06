import {Component, Input} from '@angular/core';
import {IonicModule, ModalController} from '@ionic/angular';
import {ExerciseDto} from '../../dtos/exercise';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-exercise-detail',
  templateUrl: './exercise-detail.component.html',
  styleUrls: ['./exercise-detail.component.scss'],
  imports: [
    IonicModule,
    CommonModule
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
