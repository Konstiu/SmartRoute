import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IonicModule } from '@ionic/angular';

import { ExploreContainerComponentModule } from '../explore-container/explore-container.module';

import { RoutePage } from './route.page';

describe('RoutePage', () => {
  let component: RoutePage;
  let fixture: ComponentFixture<RoutePage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [RoutePage],
      imports: [IonicModule.forRoot(), ExploreContainerComponentModule]
    }).compileComponents();

    fixture = TestBed.createComponent(RoutePage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
