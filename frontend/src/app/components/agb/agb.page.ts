import { Component, OnInit } from '@angular/core';
import {IonicModule} from "@ionic/angular";
import {CommonModule} from "@angular/common";

@Component({
    selector: 'app-agb',
    templateUrl: './agb.page.html',
    styleUrls: ['./agb.page.scss'],
    imports: [
        IonicModule,
        CommonModule
    ]
})
export class AgbPage implements OnInit {

  constructor() { }

  ngOnInit() {
  }

}
