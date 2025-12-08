import { Component, inject } from '@angular/core';
import { IonicModule, ToastController } from '@ionic/angular';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { GpxService } from '../../../services/gpx.service';
import { Router } from '@angular/router';
import { ActivitySyncNotificationService } from 'src/services/ActivitySyncNotificationService';

@Component({
  selector: 'app-import-gpx',
  templateUrl: './import-gpx.page.html',
  styleUrls: ['./import-gpx.page.scss'],
  standalone: true,
  imports: [IonicModule, FormsModule, CommonModule]
})
export class ImportGpxPage {
  files: File[] = [];
  errorMessage: string | null = null;
  readonly maxTotalBytes = 10 * 1024 * 1024; // 10 MB
  uploadInProgress = false;
  private gpxService = inject(GpxService);
  private toastCtrl = inject(ToastController);
  private router = inject(Router);
  private syncNotificationService: ActivitySyncNotificationService = inject(ActivitySyncNotificationService);

  onFileButtonClick(fileInput: HTMLInputElement) {
    fileInput.click();
  }

  onFilesSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files) return;
    const selected = Array.from(input.files);

    this.files = this.files.concat(selected);

    const total = this.getTotalBytes();
    if (total > this.maxTotalBytes) {
      this.errorMessage = `The selected files exceed the maximum total size of ${this.formatFileSize(this.maxTotalBytes)}.`;
    } else {
      this.errorMessage = null;
    }

    input.value = '';
  }

  removeFile(index: number) {
    if (index < 0 || index >= this.files.length) return;
    this.files.splice(index, 1);
    const total = this.files.reduce((s, f) => s + f.size, 0);
    if (total <= this.maxTotalBytes) {
      this.errorMessage = null;
    }
  }

  formatFileSize(bytes: number): string {
    const kb = bytes / 1024;
    if (kb >= 1000) {
      const mb = kb / 1024;
      return `${mb.toFixed(1)} MB`;
    }
    return `${kb.toFixed(1)} KB`;
  }

  getTotalBytes(): number {
    return this.files.reduce((sum, f) => sum + f.size, 0);
  }

  uploadFiles() {
    if (this.files.length === 0) return;
    this.uploadInProgress = true;
    this.errorMessage = null;

    this.gpxService.importStravaGpx(this.files).subscribe({
      next: (activities) => {
        console.log('Import successful, activities:', activities);
        this.uploadInProgress = false;
        this.errorMessage = null;
        const count = Array.isArray(activities) ? activities.length : 0;
        this.files = [];

        const message = `Import successful (${count} activities).`;
        this.toastCtrl.create({ message, color: 'success', duration: 3000, position: 'top' })
          .then(t => t.present())
          .then(() => this.syncNotificationService.notifySyncCompleted())
          .then(() => this.router.navigate(['/tabs/recentRuns']));
      },
      error: (err) => {
        console.error('Upload error', err);
        this.uploadInProgress = false;
        const status = err && err.status ? err.status : null;
        if (status === 415 || status === 400) {
          this.errorMessage = 'At least one file has an invalid format.';
        } else {
          this.errorMessage = 'Upload failed. Please try again later.';
        }
      }
    });
  }
}
