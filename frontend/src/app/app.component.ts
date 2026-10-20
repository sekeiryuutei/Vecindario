import { Component, inject } from '@angular/core';
import { IonApp, IonRouterOutlet, IonToast } from '@ionic/angular/standalone';
import { ToastService } from './core/toast.service';
import { ConfirmDialogComponent } from './shared/confirm-dialog.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [IonApp, IonRouterOutlet, IonToast, ConfirmDialogComponent],
  template: `
    <ion-app>
      <ion-router-outlet></ion-router-outlet>
      <ion-toast
        [isOpen]="toast.open()"
        [message]="toast.message()"
        [color]="toast.color()"
        [duration]="3800"
        position="top"
        (didDismiss)="toast.clear()">
      </ion-toast>
      <app-confirm-dialog />
    </ion-app>
  `
})
export class AppComponent {
  readonly toast = inject(ToastService);
}
