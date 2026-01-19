import { Component, OnInit, Input } from '@angular/core';
import { ModalController, ToastController } from '@ionic/angular';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { IonicModule } from '@ionic/angular';
import {FriendshipService} from "../../../services/friendship.service";
import {ChatMessageService} from "../../../services/chat-message.service";
import {AuthService} from "../../../services/auth.service";
import {RouteService} from "../../../services/route.service";

interface Friend {
  email: string;
  firstName: string;
  lastName: string;
  selected: boolean;
}

@Component({
  selector: 'app-friend-selector',
  templateUrl: './share-route-friends.page.html',
  styleUrls: ['./share-route-friends.page.scss'],
  standalone: true,
  imports: [IonicModule, CommonModule, FormsModule]
})
export class ShareRouteFriendsPage implements OnInit {
  @Input() routeId!: string;
  @Input() routeName!: string;
  @Input() routeDescription: string = 'Check out this route!';

  friends: Friend[] = [];
  filteredFriends: Friend[] = [];
  searchText: string = '';
  isLoading: boolean = true;
  isSending: boolean = false;

  constructor(
    private modalController: ModalController,
    private friendshipService: FriendshipService,
    private chatMessageService: ChatMessageService,
    private authService: AuthService,
    private toastController: ToastController,
    private routeService: RouteService
  ) {}

  ngOnInit() {
    this.loadFriends();
  }

  loadFriends() {
    const userEmail = this.authService.getUserEmail();

    this.friendshipService.getFriends().subscribe({
      next: (friendships) => {
        this.friends = friendships.map(friendship => {
          const friend = friendship.sender.email === userEmail
            ? friendship.receiver
            : friendship.sender;

          return {
            email: friend.email,
            firstName: friend.firstName,
            lastName: friend.lastName,
            selected: false
          };
        });

        this.filteredFriends = [...this.friends];
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Failed to load friends:', err);
        this.isLoading = false;
      }
    });
  }

  filterFriends() {
    const search = this.searchText.toLowerCase().trim();

    if (!search) {
      this.filteredFriends = [...this.friends];
      return;
    }

    this.filteredFriends = this.friends.filter(friend =>
      friend.firstName.toLowerCase().includes(search) ||
      friend.lastName.toLowerCase().includes(search) ||
      friend.email.toLowerCase().includes(search)
    );
  }

  get selectedCount(): number {
    return this.friends.filter(f => f.selected).length;
  }

  async shareWithSelected() {
    const selectedFriends = this.friends.filter(f => f.selected);

    if (selectedFriends.length === 0) {
      return;
    }

    this.isSending = true;

    try {
      // Send to each selected friend
      for (const friend of selectedFriends) {
        await this.chatMessageService.sendRouteMessage(
          this.routeId,
          this.routeName,
          this.routeDescription,
          friend.email
        );
      }
      const emails: string[] = selectedFriends.map(f => f.email);
      this.routeService.share(Number(this.routeId), emails).subscribe({
        next: () => console.log('share ok'),
        error: (e) => console.error('share failed', e),
      });
      // Show success message
      const toast = await this.toastController.create({
        message: `Route shared with ${selectedFriends.length} friend${selectedFriends.length !== 1 ? 's' : ''}!`,
        duration: 2000,
        color: 'success',
        position: 'top'
      });
      await toast.present();

      // Close modal
      this.dismiss(true);

    } catch (error) {
      console.error('Failed to share route:', error);

      const toast = await this.toastController.create({
        message: 'Failed to share route. Please try again.',
        duration: 2000,
        color: 'danger',
        position: 'top'
      });
      await toast.present();

      this.isSending = false;
    }
  }

  dismiss(success: boolean = false) {
    this.modalController.dismiss({ success });
  }
}
