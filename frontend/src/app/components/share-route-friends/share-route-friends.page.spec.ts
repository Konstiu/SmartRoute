import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ShareRouteFriendsPage } from './share-route-friends.page';

describe('ShareRouteFriendsPage', () => {
  let component: ShareRouteFriendsPage;
  let fixture: ComponentFixture<ShareRouteFriendsPage>;

  beforeEach(() => {
    fixture = TestBed.createComponent(ShareRouteFriendsPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
