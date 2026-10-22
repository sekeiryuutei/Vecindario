import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { label } from '../../core/labels';
import { BoardMember } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError } from '../../core/utils';
import { EmptyStateComponent, PageHeaderComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-my-board-page',
  standalone: true,
  imports: [DatePipe, PageHeaderComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Junta directiva" subtitle="Integrantes que representan a la comunidad y sus datos de contacto." />
    @if (loading()) {
      <div class="card"><div class="loading"><span class="spinner"></span> Cargando junta directiva.</div></div>
    } @else if (!items().length) {
      <div class="card"><app-empty-state symbol="JD" title="Sin integrantes" message="La junta directiva aún no está registrada." /></div>
    } @else {
      <div class="grid grid-2">
        @for (item of items(); track item.id) {
          <div class="card">
            <div class="card-body">
              <span class="badge info">{{ label(item.position) }}</span>
              <h2 style="margin:12px 0 4px">{{ item.personName }}</h2>
              <div class="muted">{{ item.documentNumber || 'Sin documento' }}</div>
              <div style="margin-top:14px;display:grid;gap:5px">
                <div><strong>Correo:</strong> {{ item.email || 'No registrado' }}</div>
                <div><strong>Teléfono:</strong> {{ item.phone || 'No registrado' }}</div>
                <div><strong>Periodo:</strong> {{ item.startDate | date:'dd/MM/yyyy' }} – {{ item.endDate ? (item.endDate | date:'dd/MM/yyyy') : 'Vigente' }}</div>
              </div>
              @if (item.notes) { <p class="muted" style="margin-top:12px">{{ item.notes }}</p> }
            </div>
          </div>
        }
      </div>
    }
  `
})
export class MyBoardPage {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly items = signal<BoardMember[]>([]);
  readonly loading = signal(false);

  constructor() { void this.load(); }

  async load(): Promise<void> {
    this.loading.set(true);
    try {
      this.items.set(await firstValueFrom(this.api.get<BoardMember[]>('/board', { status: 'ACTIVE' })));
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }
}
