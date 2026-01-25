import {Component, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {IonicModule, ModalController, AlertController, ToastController} from '@ionic/angular';
import {InjuryService} from '../../../services/injury.service';
import {ViewInjuryDto} from '../../dtos/injuries';
import {AddInjuryComponent} from './add-injury/add-injury.page';

@Component({
  selector: 'app-injuries',
  templateUrl: './injuries.page.html',
  styleUrls: ['./injuries.page.scss'],
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    IonicModule
  ]
})
export class InjuriesPage implements OnInit {
  injuries: ViewInjuryDto[] = [];
  allInjuries: ViewInjuryDto[] = [];
  loading = false;
  showOldInjuries = false;

  bodyPartLabels: { [key: string]: string } = {
    NECK_REGION: 'Neck',
    UPPER_REGION: 'Upper Body',
    CORE_REGION: 'Core',
    KNEE_REGION: 'Knee',
    FEET_REGION: 'Feet',
    UPPER_LEG_REGION: 'Upper Leg',
    LOWER_LEG_REGION: 'Lower Leg',
    RESPIRATION_REGION: 'Respiratory',
    SPINAL_INJURY: 'Spinal Injury',
    BONE_FRACTURE: 'Bone Fracture',
    HIP: 'Hip'
  };

  constructor(
    private injuryService: InjuryService,
    private modalController: ModalController,
    private alertController: AlertController,
    private toastController: ToastController
  ) {
  }

  ngOnInit() {
    this.loadInjuries();
  }

  ionViewWillEnter() {
    this.loadInjuries();
  }

  async loadInjuries() {
    this.loading = true;
    try {
      this.allInjuries = await this.injuryService.getInjuries();

      // Sort by start date (most recent first)
      this.allInjuries.sort((a, b) => {
        const dateA = new Date(a.lastHealthyDate).getTime();
        const dateB = new Date(b.lastHealthyDate).getTime();
        return dateB - dateA; // Descending order (newest first)
      });

      this.filterInjuries();
    } catch (error) {
      console.error('Error loading injuries:', error);
      await this.showToast('Failed to load injuries', 'danger');
    } finally {
      this.loading = false;
    }
  }

  filterInjuries() {
    if (this.showOldInjuries) {
      this.injuries = [...this.allInjuries];
    } else {
      const fourteenDaysAgo = new Date();
      fourteenDaysAgo.setDate(fourteenDaysAgo.getDate() - 14);

      this.injuries = this.allInjuries.filter(injury => {
        // Show if end date is within last 14 days, or if still ongoing (no end date)
        if (!injury.lastInjuryDate) {
          return true; // Always show ongoing injuries
        }
        const endDate = new Date(injury.lastInjuryDate);
        return endDate >= fourteenDaysAgo;
      });
    }
  }

  toggleOldInjuries() {
    this.showOldInjuries = !this.showOldInjuries;
    this.filterInjuries();
  }

  getOldInjuriesCount(): number {
    const fourteenDaysAgo = new Date();
    fourteenDaysAgo.setDate(fourteenDaysAgo.getDate() - 14);

    return this.allInjuries.filter(injury => {
      if (!injury.lastInjuryDate) {
        return false; // Ongoing injuries are not "old"
      }
      const endDate = new Date(injury.lastInjuryDate);
      return endDate < fourteenDaysAgo;
    }).length;
  }

  async openAddInjuryModal() {
    const modal = await this.modalController.create({
      component: AddInjuryComponent
    });

    await modal.present();

    const {data} = await modal.onWillDismiss();
    if (data?.reload) {
      this.loadInjuries();
      this.markTrainingPlanDirty();
    }
  }

  async editInjury(injury: ViewInjuryDto) {
    const modal = await this.modalController.create({
      component: AddInjuryComponent,
      componentProps: {
        injury: injury
      }
    });

    await modal.present();

    const {data} = await modal.onWillDismiss();
    if (data?.reload) {
      this.loadInjuries();
      this.markTrainingPlanDirty();
    }
  }

  async deleteInjury(injuryId: number) {
    const alert = await this.alertController.create({
      header: 'Delete Injury',
      message: 'Are you sure you want to delete this injury record?',
      buttons: [
        {
          text: 'Cancel',
          role: 'cancel'
        },
        {
          text: 'Delete',
          role: 'destructive',
          handler: async () => {
            try {
              await this.injuryService.deleteInjury(injuryId);
              await this.showToast('Injury deleted successfully', 'success');
              this.loadInjuries();
              this.markTrainingPlanDirty();
            } catch (error) {
              await this.showToast('Failed to delete injury', 'danger');
            }
          }
        }
      ]
    });

    await alert.present();
  }

  getBodyPartLabel(bodyPart: string): string {
    return this.bodyPartLabels[bodyPart] || bodyPart;
  }

  getSeverityColor(injuryIndex: number): string {
    if (injuryIndex < 0.33) return 'success';
    if (injuryIndex < 0.67) return 'warning';
    return 'danger';
  }

  getSeverityLabel(injuryIndex: number): string {
    if (injuryIndex < 0.33) return 'Mild';
    if (injuryIndex < 0.67) return 'Moderate';
    return 'Severe';
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

  private readonly trainingPlanInjuryChangedFlagKey: string = 'trainingPlanInjuryChanged';

  private markTrainingPlanDirty(): void
  {
    localStorage.setItem(this.trainingPlanInjuryChangedFlagKey, 'true');
  }
}
