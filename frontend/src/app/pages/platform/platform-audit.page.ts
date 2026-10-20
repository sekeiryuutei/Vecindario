import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { label, statusTone } from '../../core/labels';
import {
  AuditLogView,
  PageResultAuditLogView,
  PageResultTenantView,
  TenantView
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError, toIsoInstant } from '../../core/utils';
import { EmptyStateComponent, PageHeaderComponent, PaginationComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-platform-audit-page',
  standalone: true,
  imports: [FormsModule, DatePipe, PageHeaderComponent, PaginationComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Auditoría global" subtitle="Consulta los eventos de todas las copropiedades y filtra por empresa, acción o fecha." />

    <div class="grid grid-4">
      <div class="stat accent"><div class="stat-label">Eventos</div><div class="stat-value">{{ totalElements() }}</div><div class="stat-hint">Según los filtros actuales</div></div>
      <div class="stat"><div class="stat-label">Exitosos</div><div class="stat-value">{{ successCount() }}</div><div class="stat-hint">En esta página</div></div>
      <div class="stat danger"><div class="stat-label">Fallidos</div><div class="stat-value">{{ failedCount() }}</div><div class="stat-hint">En esta página</div></div>
      <div class="stat"><div class="stat-label">Copropiedades</div><div class="stat-value">{{ tenants().length }}</div><div class="stat-hint">Disponibles para filtrar</div></div>
    </div>

    <div class="filter-bar">
      <div class="field">
        <label for="tenantFilter">Copropiedad</label>
        <select id="tenantFilter" name="tenantFilter" class="select" [(ngModel)]="tenantId" (change)="load(0)">
          <option value="">Todas</option>
          @for (tenant of tenants(); track tenant.id) { <option [value]="tenant.id">{{ tenant.name }}</option> }
        </select>
      </div>
      <div class="field">
        <label for="actionFilter">Acción</label>
        <input id="actionFilter" name="actionFilter" class="input" maxlength="100" [(ngModel)]="action" placeholder="Ej. PAYMENT_CREATED">
      </div>
      <div class="field"><label for="fromFilter">Desde</label><input id="fromFilter" name="fromFilter" type="date" class="input" [(ngModel)]="from"></div>
      <div class="field"><label for="toFilter">Hasta</label><input id="toFilter" name="toFilter" type="date" class="input" [(ngModel)]="to"></div>
      <button type="button" class="btn btn-secondary" (click)="load(0)">Filtrar</button>
      <button type="button" class="btn btn-ghost" (click)="clearFilters()">Limpiar</button>
    </div>

    <div class="card">
      <div class="card-header"><div><h2>Eventos auditados</h2><p>{{ totalElements() }} evento(s) encontrados.</p></div><button type="button" class="btn btn-secondary btn-sm" (click)="load(page())">Actualizar</button></div>
      @if (loading()) {
        <div class="loading"><span class="spinner"></span> Consultando la auditoría…</div>
      } @else if (!items().length) {
        <app-empty-state symbol="▤" title="Sin eventos" message="No hay registros de auditoría para los filtros seleccionados." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Fecha</th><th>Copropiedad</th><th>Actor</th><th>Acción</th><th>Entidad</th><th>Resultado</th><th>Origen</th></tr></thead>
            <tbody>
              @for (event of items(); track event.id) {
                <tr>
                  <td class="nowrap">{{ event.occurredAt | date:'dd/MM/yyyy HH:mm:ss' }}</td>
                  <td>{{ tenantName(event.tenantId) }}</td>
                  <td><div class="main-cell">{{ event.actorEmail || 'Sistema' }}</div><div class="muted small">{{ event.actorUserId || '—' }}</div></td>
                  <td><div class="main-cell">{{ event.action }}</div><div class="muted small">{{ event.details || '' }}</div></td>
                  <td>{{ event.entity || '—' }}@if (event.entityId) { <div class="muted small mono">{{ event.entityId }}</div> }</td>
                  <td><span class="badge" [class]="'badge ' + tone(event.result)">{{ label(event.result) }}</span></td>
                  <td><div>{{ event.ip || '—' }}</div><div class="muted small mono">{{ event.traceId || '—' }}</div></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-pagination [page]="page()" [totalPages]="totalPages()" [totalElements]="totalElements()" (pageChange)="load($event)" />
      }
    </div>
  `
})
export class PlatformAuditPage {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly tenants = signal<TenantView[]>([]);
  readonly items = signal<AuditLogView[]>([]);
  readonly loading = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly successCount = computed(() => this.items().filter((item) => item.result === 'SUCCESS').length);
  readonly failedCount = computed(() => this.items().filter((item) => item.result === 'FAILURE' || item.result === 'FAILED' || item.result === 'ERROR').length);
  tenantId = '';
  action = '';
  from = '';
  to = '';

  constructor() {
    void this.loadTenants();
    void this.load(0);
  }

  tenantName(id: string | undefined): string {
    if (!id) return 'Plataforma';
    return this.tenants().find((tenant) => tenant.id === id)?.name ?? id;
  }

  async loadTenants(): Promise<void> {
    try {
      const result = await firstValueFrom(this.api.get<PageResultTenantView>('/platform/tenants', { page: 0, size: 200 }));
      this.tenants.set(result.content ?? []);
    } catch {
      this.tenants.set([]);
    }
  }

  async load(page = 0): Promise<void> {
    this.loading.set(true);
    try {
      const result = await firstValueFrom(this.api.get<PageResultAuditLogView>('/platform/audit', {
        tenantId: this.tenantId,
        action: this.action.trim(),
        from: this.from ? toIsoInstant(`${this.from}T00:00`) : '',
        to: this.to ? toIsoInstant(`${this.to}T23:59`) : '',
        page,
        size: 25
      }));
      this.items.set(result.content ?? []);
      this.page.set(result.page ?? 0);
      this.totalPages.set(result.totalPages ?? 0);
      this.totalElements.set(result.totalElements ?? 0);
    } catch (error) {
      this.items.set([]);
      this.toast.error(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  clearFilters(): void {
    this.tenantId = '';
    this.action = '';
    this.from = '';
    this.to = '';
    void this.load(0);
  }
}
