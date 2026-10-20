import { Injectable, signal } from '@angular/core';

export type ToastColor = 'success' | 'danger' | 'warning' | 'primary';

@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly message = signal('');
  readonly open = signal(false);
  readonly color = signal<ToastColor>('success');

  show(message: string, color: ToastColor = 'success'): void {
    this.message.set(message);
    this.color.set(color);
    this.open.set(true);
  }

  success(message: string): void {
    this.show(message, 'success');
  }

  error(message: string): void {
    this.show(message, 'danger');
  }

  warning(message: string): void {
    this.show(message, 'warning');
  }

  clear(): void {
    this.open.set(false);
  }
}
