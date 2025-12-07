import { Component, OnInit } from '@angular/core';
import { IonicModule, AlertController, ToastController, PopoverController } from '@ionic/angular';
import { CommonModule } from '@angular/common';
import { FriendInfoDto, FriendRequestDto, FriendshipDetailDto } from 'src/app/dtos/friendship';
import { FriendshipService } from 'src/services/friendship.service';
import { AuthService } from 'src/services/auth.service';

@Component({
  selector: 'app-friends',
  templateUrl: './friends.page.html',
  styleUrls: ['./friends.page.scss'],
  standalone: true,
  imports: [IonicModule, CommonModule]
})
export class FriendsPage implements OnInit {
  friendships: FriendshipDetailDto[] = [];
  outgoingRequests: FriendshipDetailDto[] = [];
  incomingRequests: FriendshipDetailDto[] = [];

  constructor(
    private authService: AuthService,
    private friendshipService: FriendshipService,
    private alertCtrl: AlertController,
    private toastCtrl: ToastController,
    private popoverCtrl: PopoverController
  ) {}

  ngOnInit(): void {
    this.friendshipService.getFriends().subscribe({
      next: (result) => {
        this.friendships = result;
      },
      error: (err) => {
        console.error('Error fetching friends:', err);
      }
    });

    this.friendshipService.getOutgoingFriendRequests().subscribe({
      next: (result) => {
        this.outgoingRequests = result;
      },
      error: (err) => {
        console.error('Error fetching outgoing requests:', err);
      }
    });

    this.friendshipService.getIncomingFriendRequests().subscribe({
      next: (result) => {
        this.incomingRequests = result;
      },
      error: (err) => {
        console.error('Error fetching incoming requests:', err);
      }
    });
  }

  getFriendsDto(): FriendInfoDto[] {
    const myEmail = this.authService.getUserEmail();
    return this.friendships.map(friendship => 
      friendship.sender.email !== myEmail ? friendship.sender : friendship.receiver
    );
  }

  async showRemoveFriendDialog(friend: FriendInfoDto) {
    const friendship = this.friendships.find(f => 
      f.sender.email === friend.email || f.receiver.email === friend.email
    );
    if (!friendship) return;

    const alert = await this.alertCtrl.create({
      header: 'Remove Friend',
      message: `Are you sure you want to remove ${friend.firstName} ${friend.lastName} from your friends?`,
      buttons: [
        {
          text: 'Cancel',
          role: 'cancel'
        },
        {
          text: 'Remove',
          role: 'destructive',
          handler: () => {
            this.removeFriendship(friendship);
          }
        }
      ]
    });

    await alert.present();
  }

  removeFriendship(friendship: FriendshipDetailDto) {
    console.log('Removing friendship:', friendship);
    this.friendshipService.unfriendUser(friendship.friendshipId).subscribe({
      next: async () => {
        await this.popoverCtrl.dismiss();
        this.friendships = this.friendships.filter(f => f.friendshipId !== friendship.friendshipId);
        const toast = await this.toastCtrl.create({
          message: 'Friend removed successfully.',
          color: 'success',
          duration: 2000,
          position: 'top'
        });
        await toast.present();
      },
      error: async (err) => {
        console.error('Error removing friendship:', err);
        await this.popoverCtrl.dismiss();
        const message = 'Failed to remove friend. Please try again.';
        const toast = await this.toastCtrl.create({
          message,
          color: 'danger',
          duration: 3000,
          position: 'top'
        });
        await toast.present();
      }
    });
  }

  async showAddFriendDialog() {
    const alert = await this.alertCtrl.create({
      header: 'Add Friend',
      message: 'Enter the email address of the friend you want to add:',
      inputs: [
        {
          name: 'email',
          type: 'email',
          placeholder: 'friend@example.com'
        }
      ],
      buttons: [
        {
          text: 'Cancel',
          role: 'cancel'
        },
        {
          text: 'Add',
          handler: (data) => {
            if (data.email && data.email.trim()) {
              this.addFriend(data.email.trim());
            }
          }
        }
      ]
    });

    await alert.present();
  }

  async addFriend(receiverEmail: string) {
    const myEmail = this.authService.getUserEmail();
    if (receiverEmail === myEmail) {
      const toast = await this.toastCtrl.create({
        message: 'You cannot add yourself as a friend.',
        color: 'danger',
        duration: 3000,
        position: 'top'
      });
      await toast.present();
      return;
    }
    const friendRequest: FriendRequestDto = { receiverEmail };
    this.friendshipService.sendFriendRequest(friendRequest).subscribe({
      next: async (data) => {
        const matchingIncoming = this.incomingRequests.find(r => r.friendshipId === data.friendshipId);
        let toastMessage;
        if (!matchingIncoming) {
          this.outgoingRequests.push(data);
          toastMessage = 'Friend request sent successfully.';
        } else {
          this.incomingRequests = this.incomingRequests.filter(r => r.friendshipId !== data.friendshipId);
          this.friendships.push(data);
          toastMessage = 'Friend request accepted automatically as there was a pending request from this user.';
        }
        const toast = await this.toastCtrl.create({
            message: toastMessage,
            color: 'success',
            duration: 2000,
            position: 'top'
          });
        await toast.present();
      },
      error: async (err) => {
        console.error('Error sending friend request:', err);
        const message = err.error || 'Failed to send friend request. Please try again.';
        const toast = await this.toastCtrl.create({
          message,
          color: 'danger',
          duration: 3000,
          position: 'top'
        });
        await toast.present();
      }
    });
  }

  async showCancelRequestDialog(request: FriendshipDetailDto) {
    const alert = await this.alertCtrl.create({
      header: 'Remove Friend',
      message: `Are you sure you want to cancel the friend request to ${request.receiver.firstName} ${request.receiver.lastName}?`,
      buttons: [
        {
          text: 'Cancel',
          role: 'cancel'
        },
        {
          text: 'Retrieve Request',
          role: 'destructive',
          handler: () => {
            this.cancelRequest(request);
          }
        }
      ]
    });

    await alert.present();  
  }

  cancelRequest(request: FriendshipDetailDto) {
    this.friendshipService.cancelFriendRequest(request.friendshipId).subscribe({
      next: async () => {
        this.outgoingRequests = this.outgoingRequests.filter(r => r.friendshipId !== request.friendshipId);
        const toast = await this.toastCtrl.create({
          message: 'Friend request cancelled.',
          color: 'success',
          duration: 2000,
          position: 'top'
        });
        await toast.present();
      },
      error: async (err) => {
        console.error('Error cancelling friend request:', err);
        const message = err.error || 'Failed to cancel friend request. Please try again.';
        const toast = await this.toastCtrl.create({
          message,
          color: 'danger',
          duration: 3000,
          position: 'top'
        });
        await toast.present();
      }
    });
  }

  acceptRequest(request: FriendshipDetailDto) {
    this.friendshipService.acceptFriendRequest(request.friendshipId).subscribe({
      next: async (friendship) => {
        this.incomingRequests = this.incomingRequests.filter(r => r.friendshipId !== request.friendshipId);
        this.friendships.push(friendship);
        const toast = await this.toastCtrl.create({
          message: 'Friend request accepted.',
          color: 'success',
          duration: 2000,
          position: 'top'
        });
        await toast.present();
      },
      error: async (err) => {
        console.error('Error accepting friend request:', err);
        const message = err.error || 'Failed to accept friend request. Please try again.';
        const toast = await this.toastCtrl.create({
          message,
          color: 'danger',
          duration: 3000,
          position: 'top'
        });
        await toast.present();
      }
    });
  }

  rejectRequest(request: FriendshipDetailDto) {
    this.friendshipService.rejectFriendRequest(request.friendshipId).subscribe({
      next: async () => {
        this.incomingRequests = this.incomingRequests.filter(r => r.friendshipId !== request.friendshipId);
        const toast = await this.toastCtrl.create({
          message: 'Friend request rejected.',
          color: 'success',
          duration: 2000,
          position: 'top'
        });
        await toast.present();
      },
      error: async (err) => {
        console.error('Error rejecting friend request:', err);
        const message = err.error || 'Failed to reject friend request. Please try again.';
        const toast = await this.toastCtrl.create({
          message,
          color: 'danger',
          duration: 3000,
          position: 'top'
        });
        await toast.present();
      }
    });
  }

}
