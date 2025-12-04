import {ComponentFixture, TestBed} from '@angular/core/testing';
import {IonicModule} from '@ionic/angular';

import {ExploreContainerComponentModule} from '../explore-container/explore-container.module';

import {GymWorkoutTabPage} from './gym-workout-tab.page';

describe('GymWorkoutTabPage', () => {
  let component: GymWorkoutTabPage;
  let fixture: ComponentFixture<GymWorkoutTabPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [GymWorkoutTabPage],
      imports: [IonicModule.forRoot(), ExploreContainerComponentModule]
    }).compileComponents();

    fixture = TestBed.createComponent(GymWorkoutTabPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
