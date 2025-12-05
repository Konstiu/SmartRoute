import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IonicModule, NavController } from '@ionic/angular';
import { Router } from '@angular/router';

@Component({
  selector: 'app-not-found',
  templateUrl: './not-found.page.html',
  styleUrls: ['./not-found.page.scss'],
  standalone: true,
  imports: [CommonModule, IonicModule]
})
export class NotFoundPage {
  constructor(private router: Router, private nav: NavController) {}

  goHome() {
    // Use NavController for a nicer transition; fall back to router
    try {
      this.nav.navigateRoot(['/']);
    } catch (e) {
      this.router.navigate(['/']);
    }
  }
}
