import { Component, HostListener, inject } from '@angular/core';
import { ConfirmService } from '../core/confirm.service';

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  template: `
    @if (confirm.state().open) {
      <div class="modal-backdrop" role="presentation" (click)="confirm.close(false)">
        <section class="modal narrow" role="alertdialog" aria-modal="true" [attr.aria-label]="confirm.state().title" (click)="$event.stopPropagation()">
          <header class="modal-header">
            <h2>{{ confirm.state().title }}</h2>
          </header>
          <div class="modal-body">
            <p style="font-size:13.5px;line-height:1.6;color:#334155">{{ confirm.state().message }}</p>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" (click)="confirm.close(false)">{{ confirm.state().cancelLabel }}</button>
            <button type="button" class="btn" [class.btn-danger]="confirm.state().danger" [class.btn-primary]="!confirm.state().danger" (click)="confirm.close(true)">
              {{ confirm.state().confirmLabel }}
            </button>
          </div>
        </section>
      </div>
    }
  `
})
export class ConfirmDialogComponent {
  readonly confirm = inject(ConfirmService);

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.confirm.state().open) this.confirm.close(false);
  }
}
