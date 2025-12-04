import {Component, Input, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {
  FormsModule,
  ReactiveFormsModule,
  FormBuilder,
  FormGroup,
  Validators,
  ValidatorFn,
  AbstractControl, ValidationErrors
} from '@angular/forms';
import {IonicModule, ModalController, ToastController} from '@ionic/angular';
import {InjuryService} from '../../../../services/injury.service';
import {
  CreateInjuryStateDto,
  UpdateInjuryDto,
  ViewInjuryDto,
  BODY_PARTS,
  BodyPartInfo
} from '../../../dtos/injuries';


@Component({
  selector: 'app-add-injury',
  templateUrl: './add-injury.page.html',
  styleUrls: ['./add-injury.page.scss'],
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    IonicModule
  ]
})
export class AddInjuryComponent implements OnInit {
  @Input() injury?: ViewInjuryDto;

  injuryForm!: FormGroup;
  submitting = false;
  isEditMode = false;
  maxDate: string;

  bodyParts: BodyPartInfo[] = BODY_PARTS;

  constructor(
    private formBuilder: FormBuilder,
    private modalController: ModalController,
    private injuryService: InjuryService,
    private toastController: ToastController
  ) {
    // Set max date to today
    this.maxDate = new Date().toISOString().split('T')[0];
  }

  // Formatter for range pin display
  pinFormatter = (value: number) => {
    return `${(value * 100).toFixed(0)}%`;
  };

  ngOnInit() {
    this.isEditMode = !!this.injury;
    this.initForm();
  }

  initForm() {
    this.injuryForm = this.formBuilder.group({
      affectedArea: [this.injury?.affectedArea || '', Validators.required],
      injuryIndex: [
        this.injury?.injuryIndex || 0.5,
        [Validators.required, Validators.min(0), Validators.max(1)]
      ],
      lastInjuryDate: [
        this.injury?.lastInjuryDate || '',
      ],
      lastHealthyDate: [this.injury?.lastHealthyDate || new Date().toISOString().split('T')[0],
        Validators.required
      ]
    }, {
      validators: this.dateRangeValidator()
    });
  }


  dateRangeValidator(): ValidatorFn {
    return (formGroup: AbstractControl): ValidationErrors | null => {
      const lastHealthyDate = formGroup.get('lastHealthyDate')?.value;
      const lastInjuryDate = formGroup.get('lastInjuryDate')?.value;

      // If lastInjuryDate is null or empty, it's valid (still injured)
      if (!lastInjuryDate) {
        return null;
      }

      // If lastHealthyDate is empty, we can't validate
      if (!lastHealthyDate) {
        return null;
      }

      const startDate = new Date(lastHealthyDate);
      const endDate = new Date(lastInjuryDate);

      // End date must be after or equal to start date
      if (endDate < startDate) {
        return {dateRange: true};
      }

      return null;
    };
  }

  getSeverityRangeColor(): string {
    const value = this.injuryForm.get('injuryIndex')?.value || 0;
    if (value < 0.33) return 'success';
    if (value < 0.67) return 'warning';
    return 'danger';
  }

  selectBodyPart(bodyPart: string) {
    this.injuryForm.patchValue({affectedArea: bodyPart});
  }

  onInjuryIndexChange(event: any) {
    const value = parseFloat(event.detail.value);
    this.injuryForm.patchValue({injuryIndex: value});
  }

  getInjurySeverityLabel(): string {
    const index = this.injuryForm.get('injuryIndex')?.value || 0;
    if (index < 0.33) return 'Mild';
    if (index < 0.67) return 'Moderate';
    return 'Severe';
  }

  async submit() {
    if (this.injuryForm.invalid) {
      Object.keys(this.injuryForm.controls).forEach(key => {
        this.injuryForm.get(key)?.markAsTouched();
      });
      return;
    }

    this.submitting = true;

    try {
      const formValue = this.injuryForm.value;

      if (this.isEditMode && this.injury) {
        const updateDto: UpdateInjuryDto = {
          injuryId: this.injury.injuryId,
          injuryIndex: formValue.injuryIndex,
          affectedArea: formValue.affectedArea,
          lastInjuryDate: formValue.lastInjuryDate || null,
          lastHealthyDate: formValue.lastHealthyDate
        };
        await this.injuryService.updateInjuries([updateDto]);
        await this.showToast('Injury updated successfully', 'success');
      } else {
        const createDto: CreateInjuryStateDto = {
          injuryIndex: formValue.injuryIndex,
          affectedArea: formValue.affectedArea,
          lastInjuryDate: formValue.lastInjuryDate || null,
          lastHealthyDate: formValue.lastHealthyDate
        };
        await this.injuryService.createInjuries([createDto]);
        await this.showToast('Injury added successfully', 'success');
      }

      this.modalController.dismiss({reload: true});
    } catch (error) {
      console.error('Error submitting injury:', error);
      await this.showToast(
        `Failed to ${this.isEditMode ? 'update' : 'add'} injury`,
        'danger'
      );
    } finally {
      this.submitting = false;
    }
  }

  dismiss() {
    this.modalController.dismiss();
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
}
