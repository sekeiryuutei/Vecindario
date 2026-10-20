import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { label, statusTone } from '../core/labels';

@Component({
  selector: 'app-page-header',
  standalone: true,
  template: `
    <div class="page-head">
      <div>
        <h1>{{ title }}</h1>
        @if (subtitle) { <p>{{ subtitle }}</p> }
      </div>
      <div class="page-actions"><ng-content></ng-content></div>
    </div>
  `
})
export class PageHeaderComponent {
  @Input({ required: true }) title = '';
  @Input() subtitle = '';
}

@Component({
  selector: 'app-modal',
  standalone: true,
  template: `
    @if (open) {
      <div class="modal-backdrop" role="presentation" (click)="requestClose()">
        <section class="modal" [class.wide]="wide" [class.narrow]="narrow" role="dialog" aria-modal="true" [attr.aria-label]="title" (click)="$event.stopPropagation()">
          <header class="modal-header">
            <div>
              <h2>{{ title }}</h2>
              @if (subtitle) { <p>{{ subtitle }}</p> }
            </div>
            <button type="button" class="btn-icon" aria-label="Cerrar" (click)="requestClose()">
              <span aria-hidden="true" style="font-size:20px;line-height:1">×</span>
            </button>
          </header>
          <div class="modal-body"><ng-content></ng-content></div>
        </section>
      </div>
    }
  `
})
export class ModalComponent {
  @Input() open = false;
  @Input() title = '';
  @Input() subtitle = '';
  @Input() wide = false;
  @Input() narrow = false;
  @Output() readonly closed = new EventEmitter<void>();

  requestClose(): void {
    this.closed.emit();
  }
}

@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [],
  template: `
    <div class="empty">
      <div style="font-size:34px;line-height:1;margin-bottom:10px" aria-hidden="true">{{ symbol }}</div>
      <h3>{{ title }}</h3>
      <p>{{ message }}</p>
    </div>
  `
})
export class EmptyStateComponent {
  @Input() symbol = '·';
  @Input() title = 'Sin registros';
  @Input() message = 'No hay información para mostrar con los filtros actuales.';
}

@Component({
  selector: 'app-pagination',
  standalone: true,
  imports: [],
  template: `
    @if (totalPages > 1) {
      <div class="pagination">
        <span>{{ totalElements }} registro(s) · Página {{ page + 1 }} de {{ totalPages }}</span>
        <div class="pages">
          <button type="button" class="btn btn-secondary btn-sm" [disabled]="page <= 0" (click)="go(page - 1)">Anterior</button>
          <button type="button" class="btn btn-secondary btn-sm" [disabled]="page >= totalPages - 1" (click)="go(page + 1)">Siguiente</button>
        </div>
      </div>
    }
  `
})
export class PaginationComponent {
  @Input() page = 0;
  @Input() totalPages = 0;
  @Input() totalElements = 0;
  @Output() readonly pageChange = new EventEmitter<number>();

  go(page: number): void {
    if (page < 0 || page >= this.totalPages) return;
    this.pageChange.emit(page);
  }
}

@Component({
  selector: 'app-status-badge',
  standalone: true,
  template: `<span class="badge" [class]="'badge ' + tone">{{ text }}</span>`
})
export class StatusBadgeComponent implements OnInit {
  @Input({ required: true }) status: string | null | undefined = '';
  @Input() text = '';
  @Input() tone = '';

  ngOnInit(): void {
    if (!this.text) this.text = label(this.status);
    if (!this.tone) this.tone = statusTone(this.status);
  }
}
