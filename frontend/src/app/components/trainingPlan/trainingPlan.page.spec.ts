import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IonicModule } from '@ionic/angular';

import { ExploreContainerComponentModule } from '../explore-container/explore-container.module';

import { TrainingPlanPage } from './trainingPlan.page';

describe('TrainingPlanPage', () => {
  let component: TrainingPlanPage;
  let fixture: ComponentFixture<TrainingPlanPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [TrainingPlanPage],
      imports: [IonicModule.forRoot(), ExploreContainerComponentModule]
    }).compileComponents();

    fixture = TestBed.createComponent(TrainingPlanPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
