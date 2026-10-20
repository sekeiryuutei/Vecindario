import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { label, statusTone } from '../../core/labels';
import { AuditLogView, PageResultAuditLogView } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent, PaginationComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-audit-page',
  standalone: true,
  imports: [FormsModule, DatePipe, PageHeaderComponent, ModalComponent, PaginationComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Auditoría" subtitle="Trazabilidad de las acciones realizadas en la copropiedad, con actor, resultado y contexto." />

    <div class="filter-bar">
      <div class="field grow">
        <label for="action">Acción</label>
        <input id="action" name="action" class="input" [(ngModel)]="action" placeholder="Ej. VEHICLE_ENTRY, MEMBER_REVOKED" (keyup.enter)="load(0)">
      </div>
      <div class="field"><label for="from">Desde</label><input id="from" name="from" type="date" class="input" [(ngModel)]="from" (change)="load(0)"></div>
      <div class="field"><label for="to">Hasta</label><input id="to" name="to" type="date" class="input" [(ngModel)]="to" (change)="load(0)"></div>
      <button type="button" class="btn btn-secondary" (click)="load(0)">Filtrar</button>
      <button type="button" class="btn btn-ghost" (click)="clearFilters()">Limpiar</button>
    </div>

    <div class="card">
      <div class="card-header">
        <div><h2>Eventos auditados</h2><p>{{ totalElements() }} registro(s) encontrados.</p></div>
        <button type="button" class="btn btn-secondary btn-sm" (click)="load(page())">Actualizar</button>
      </div>
      @if (loading()) {
        <div class="loading"><span class="spinner"></span> Consultando auditoría…</div>
      } @else if (!items().length) {
        <app-empty-state symbol="⌕" title="Sin eventos" message="No hay registros que coincidan con los filtros seleccionados." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Fecha y hora</th><th>Actor</th><th>Acción</th><th>Entidad</th><th>Resultado</th><th>IP</th><th style="width:70px"></th></tr></thead>
            <tbody>
              @for (item of items(); track item.id) {
                <tr>
                  <td class="nowrap">{{ item.occurredAt | date:'dd/MM/yyyy HH:mm:ss' }}</td>
                  <td><div class="main-cell">{{ item.actorEmail || 'Sistema' }}</div><div class="muted small mono">{{ item.actorUserId || '—' }}</div></td>
                  <td class="mono">{{ item.action }}</td>
                  <td><div>{{ item.entity || '—' }}</div><div class="muted small mono">{{ item.entityId || '' }}</div></td>
                  <td><span class="badge" [class]="'badge ' + tone(item.result)">{{ label(item.result) }}</span></td>
                  <td class="mono">{{ item.ip || '—' }}</td>
                  <td><button type="button" class="btn-icon" title="Ver detalle" (click)="openDetail(item)">⌕</button></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-pagination [page]="page()" [totalPages]="totalPages()" [totalElements]="totalElements()" (pageChange)="load($event)" />
      }
    </div>

    <app-modal [open]="detailOpen()" title="Detalle del evento" [subtitle]="selected()?.action || ''" (closed)="detailOpen.set(false)">
      @if (selected(); as item) {
        <dl class="detail-list">
          <dt>Fecha y hora</dt><dd>{{ item.occurredAt | date:'dd/MM/yyyy HH:mm:ss' }}</dd>
          <dt>Actor</dt><dd>{{ item.actorEmail || 'Sistema' }}</dd>
          <dt>Identificador del actor</dt><dd class="mono">{{ item.actorUserId || '—' }}</dd>
          <dt>Acción</dt><dd class="mono">{{ item.action }}</dd>
          <dt>Entidad</dt><dd>{{ item.entity || '—' }}</dd>
          <dt>Identificador de entidad</dt><dd class="mono">{{ item.entityId || '—' }}</dd>
          <dt>Resultado</dt><dd><span class="badge" [class]="'badge ' + tone(item.result)">{{ label(item.result) }}</span></dd>
          <dt>IP</dt><dd class="mono">{{ item.ip || '—' }}</dd>
          <dt>Trace ID</dt><dd class="mono">{{ item.traceId || '—' }}</dd>
          <dt>Copropiedad</dt><dd class="mono">{{ item.tenantId || '—' }}</dd>
        </dl>
        <div class="divider"></div>
        <h3 class="section-title">Contexto</h3>
        <pre style="margin:0;white-space:pre-wrap;word-break:break-word;font-size:12px;line-height:1.6;background:#f8fafc;border:1px solid var(--line);border-radius:10px;padding:12px">{{ prettyDetails(item.details) }}</pre>
      }
    </app-modal>
  `
})
export class AuditPage {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly items = signal<AuditLogView[]>([]);
  readonly loading = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly detailOpen = signal(false);
  readonly selected = signal<AuditLogView | null>(null);
  action = '';
  from = '';
  to = '';

  constructor() {
    void this.load(0);
  }

  async load(page = 0): Promise<void> {
    this.loading.set(true);
    try {
      const result = await firstValueFrom(this.api.get<PageResultAuditLogView>('/audit', {
        action: this.action.trim(),
        from: this.from ? `${this.from}T00:00:00Z` : '',
        to: this.to ? `${this.to}T23:59:59Z` : '',
        page,
        size: 20
      }));
      this.items.set(result.content ?? []);
      this.page.set(result.page ?? 0);
      this.totalPages.set(result.totalPages ?? 0);
      this.totalElements.set(result.totalElements ?? 0);
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  clearFilters(): void {
    this.action = '';
    this.from = '';
    this.to = '';
    void this.load(0);
  }

  openDetail(item: AuditLogView): void {
    this.selected.set(item);
    this.detailOpen.set(true);
  }

  prettyDetails(details: string | undefined): string {
    if (!details) return 'Sin contexto adicional.';
    try {
      return JSON.stringify(JSON.parse(details), null, 2);
    } catch {
      return details;
    }
  }
}
