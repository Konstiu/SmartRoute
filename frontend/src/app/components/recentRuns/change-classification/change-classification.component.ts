import {Component, inject, Input, OnInit} from '@angular/core';
import {IonicModule, ModalController, ToastController} from "@ionic/angular";
import {ReactiveFormsModule} from "@angular/forms";
import {RunClassificationDto, RunType, RunTypeLabel} from "../../../dtos/run-classification";
import {PercentPipe} from "@angular/common";
import {ActivitiesService} from "../../../../services/activities.service";

@Component({
  selector: 'app-change-classification',
  templateUrl: './change-classification.component.html',
  styleUrls: ['./change-classification.component.scss'],
  imports: [
    IonicModule,
    ReactiveFormsModule,
    PercentPipe
  ]
})
export class ChangeClassificationComponent implements OnInit {

  private readonly modalController: ModalController = inject(ModalController);
  private readonly toastController: ToastController = inject(ToastController);
  private readonly service: ActivitiesService = inject(ActivitiesService);

  @Input()
  activityId!: number;

  @Input()
  dto!: RunClassificationDto;

  private originalRunType!: RunType;

  readonly runTypes: RunType[] = Object.values(RunType) as RunType[];
  readonly runTypeLabel = RunTypeLabel;

  readonly runTypeIcon: Record<RunType, string> = {
    [RunType.EASY_RUN]: 'walk-outline',
    [RunType.LONG_RUN]: 'trail-sign-outline',
    [RunType.TEMPO_RUN]: 'speedometer-outline',
    [RunType.INTERVAL_RUN]: 'pulse-outline'
  };

  ngOnInit() {
    this.originalRunType = this.dto.runType;
  }

  dismiss() {
    if (this.hasChanges) {
      this.dto.runType = this.originalRunType;
    }
    this.modalController.dismiss();
  }

  selectRunType(type: RunType) {
    this.dto.runType = type;
  }

  confirm() {
    this.service.updateClassification(this.activityId, this.dto.runType).subscribe({
      next: () => {
        this.showToast("Classification updated successfully", "success");

        this.modalController.dismiss({
          updatedClassification: this.dto
        });
      },
      error: (err) => {
        console.log(err);
        this.showToast("Failed to update classification", "danger");
        this.modalController.dismiss();
      }
    });
  }

  get hasChanges(): boolean {
    return this.dto.runType !== this.originalRunType;
  }

  label(type: RunType): string {
    return this.runTypeLabel[type];
  }

  icon(type: RunType): string {
    return this.runTypeIcon[type];
  }

  probability(type: RunType): number {
    return this.dto.probabilities[type] ?? 0;
  }

  async showToast(message: string, color: string) {
    const toast = await this.toastController.create({
      message,
      duration: 2000,
      color,
      position: 'top'
    });
    await toast.present();
  }

  protected readonly RunTypeLabel = RunTypeLabel;
  protected readonly RunType = RunType;
}
