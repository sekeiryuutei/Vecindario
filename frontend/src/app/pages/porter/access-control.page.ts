import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { label } from '../../core/labels';
import {
  AccessEvent,
  PageResultAccessEvent,
  PageResultSecurityAlert,
  SecurityAlert,
  SecuritySummary,
  Vehicle,
  VehicleLookup
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError } from '../../core/utils';
import { EmptyStateComponent, PageHeaderComponent, PaginationComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-access-control-page',
  standalone: true,
  imports: [FormsModule, DatePipe, PageHeaderComponent, PaginationComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Control de acceso" subtitle="Consulta vehículos por placa, registra entradas y salidas y atiende las alertas del turno.">
      <button type="button" class="btn btn-secondary" (click)="refresh()">Actualizar</button>
    </app-page-header>

    <div class="grid grid-4">
      <div class="stat accent"><div class="stat-label">Vehículos dentro</div><div class="stat-value">{{ summary()?.vehiclesInside ?? inside().length }}</div><div class="stat-hint">{{ summary()?.vehicleEntriesToday ?? 0 }} entradas hoy</div></div>
      <div class="stat"><div class="stat-label">Visitantes dentro</div><div class="stat-value">{{ summary()?.visitorsInside ?? 0 }}</div><div class="stat-hint">{{ summary()?.pendingVisitRequests ?? 0 }} solicitudes pendientes</div></div>
      <div class="stat warn"><div class="stat-label">Paquetes pendientes</div><div class="stat-value">{{ summary()?.packagesPending ?? 0 }}</div><div class="stat-hint">Por entregar</div></div>
      <div class="stat danger"><div class="stat-label">Alertas abiertas</div><div class="stat-value">{{ summary()?.openAlerts ?? alerts().length }}</div><div class="stat-hint">{{ summary()?.openIncidents ?? 0 }} novedades abiertas</div></div>
    </div>

    <div class="grid grid-2" style="margin-top:18px">
      <div class="card">
        <div class="card-header"><div><h2>Consulta por placa</h2><p>Verifica la autorización del vehículo antes de registrar el movimiento.</p></div></div>
        <div class="card-body">
          <div class="row" style="align-items:flex-end">
            <div class="field grow" style="flex:1">
              <label for="plate">Placa</label>
              <input id="plate" name="plate" class="input mono" [(ngModel)]="plate" (keyup.enter)="lookup()" placeholder="ABC123" maxlength="12" autocomplete="off" style="text-transform:uppercase">
            </div>
            <button type="button" class="btn btn-primary" [disabled]="searching()" (click)="lookup()">{{ searching() ? 'Consultando…' : 'Consultar' }}</button>
          </div>
          @if (lookupError()) { <div class="alert alert-error" style="margin-top:14px">{{ lookupError() }}</div> }
          @if (lookupResult(); as vehicle) {
            <div class="divider"></div>
            <div class="row between">
              <div>
                <div class="mono" style="font-size:22px;font-weight:750;letter-spacing:.02em">{{ vehicle.plate }}</div>
                <div class="muted small">{{ label(vehicle.typeCode) }} · {{ vehicle.brand || 'Sin marca' }} · {{ vehicle.color || 'Sin color' }}</div>
              </div>
              <span class="badge" [class]="'badge ' + (vehicle.presence === 'INSIDE' ? 'info' : 'neutral')">{{ label(vehicle.presence) }}</span>
            </div>
            <dl class="detail-list" style="margin-top:14px">
              <dt>Inmueble</dt><dd>{{ vehicle.unitIdentifier || '—' }}</dd>
              <dt>Estado</dt><dd><span class="badge" [class]="'badge ' + (vehicle.status === 'AUTHORIZED' ? 'success' : 'danger')">{{ label(vehicle.status) }}</span></dd>
            </dl>
            <div class="form-actions">
              @if (auth.hasPermission('VEHICLES_REGISTER_ENTRY')) {
                <button type="button" class="btn btn-primary" [disabled]="acting() || vehicle.presence === 'INSIDE'" (click)="entry()">Registrar entrada</button>
              }
              @if (auth.hasPermission('VEHICLES_REGISTER_EXIT')) {
                <button type="button" class="btn btn-secondary" [disabled]="acting() || vehicle.presence !== 'INSIDE'" (click)="exit()">Registrar salida</button>
              }
            </div>
          }
        </div>
      </div>

      <div class="card">
        <div class="card-header"><div><h2>Vehículos dentro</h2><p>Presencia actual en la copropiedad.</p></div><span class="badge info">{{ inside().length }}</span></div>
        @if (!inside().length) {
          <app-empty-state symbol="▣" title="Sin vehículos dentro" message="No hay vehículos registrados dentro de la copropiedad en este momento." />
        } @else {
          <div class="table-wrap">
            <table class="table">
              <thead><tr><th>Placa</th><th>Vehículo</th><th>Inmueble</th><th style="width:90px"></th></tr></thead>
              <tbody>
                @for (vehicle of inside(); track vehicle.id) {
                  <tr>
                    <td class="main-cell mono">{{ vehicle.plate }}</td>
                    <td>{{ label(vehicle.typeCode) }} · {{ vehicle.brand || '—' }} {{ vehicle.color || '' }}</td>
                    <td>{{ vehicle.unitIdentifier || '—' }}</td>
                    <td><button type="button" class="btn btn-secondary btn-sm" (click)="select(vehicle.plate)">Consultar</button></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>
    </div>

    <div class="card">
      <div class="card-header">
        <div><h2>Historial de accesos</h2><p>Entradas y salidas registradas por portería.</p></div>
        <div class="row">
          <div class="field" style="min-width:150px">
            <label for="eventPlate">Placa</label>
            <input id="eventPlate" name="eventPlate" class="input mono" [(ngModel)]="eventPlate" (keyup.enter)="loadEvents(0)" placeholder="Todas">
          </div>
          <button type="button" class="btn btn-secondary btn-sm" (click)="loadEvents(0)">Filtrar</button>
        </div>
      </div>
      @if (loadingEvents()) {
        <div class="loading"><span class="spinner"></span> Cargando accesos…</div>
      } @else if (!events().length) {
        <app-empty-state symbol="⇄" title="Sin movimientos" message="No hay entradas ni salidas registradas para el filtro." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Fecha y hora</th><th>Placa</th><th>Evento</th><th>Método</th><th>Inmueble</th></tr></thead>
            <tbody>
              @for (event of events(); track event.id) {
                <tr>
                  <td>{{ event.occurredAt | date:'dd/MM/yyyy HH:mm:ss' }}</td>
                  <td class="main-cell mono">{{ event.plate }}</td>
                  <td><span class="badge" [class]="'badge ' + (event.eventType === 'ENTRY' ? 'success' : 'neutral')">{{ event.eventType === 'ENTRY' ? 'Entrada' : 'Salida' }}</span></td>
                  <td>{{ label(event.method) }}</td>
                  <td>{{ unitLabel(event.unitId) }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-pagination [page]="eventsPage()" [totalPages]="eventsPages()" [totalElements]="eventsTotal()" (pageChange)="loadEvents($event)" />
      }
    </div>

    @if (auth.hasPermission('SECURITY_ALERTS_VIEW')) {
      <div class="card">
        <div class="card-header"><div><h2>Alertas de seguridad</h2><p>Intentos rechazados o situaciones que requieren revisión.</p></div><span class="badge danger">{{ alerts().length }} abierta(s)</span></div>
        @if (!alerts().length) {
          <app-empty-state symbol="✓" title="Sin alertas abiertas" message="No hay alertas pendientes de atención." />
        } @else {
          <div class="table-wrap">
            <table class="table">
              <thead><tr><th>Fecha</th><th>Tipo</th><th>Placa</th><th>Mensaje</th><th style="width:100px"></th></tr></thead>
              <tbody>
                @for (alert of alerts(); track alert.id) {
                  <tr>
                    <td class="nowrap">{{ alert.createdAt | date:'dd/MM HH:mm' }}</td>
                    <td class="mono">{{ alert.alertType }}</td>
                    <td class="main-cell mono">{{ alert.plate || '—' }}</td>
                    <td>{{ alert.message }}</td>
                    <td><button type="button" class="btn btn-secondary btn-sm" [disabled]="resolving() === alert.id" (click)="resolve(alert)">Resolver</button></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>
    }
  `
})
export class AccessControlPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly summary = signal<SecuritySummary | null>(null);
  readonly inside = signal<Vehicle[]>([]);
  readonly lookupResult = signal<VehicleLookup | null>(null);
  readonly lookupError = signal('');
  readonly events = signal<AccessEvent[]>([]);
  readonly alerts = signal<SecurityAlert[]>([]);
  readonly searching = signal(false);
  readonly acting = signal(false);
  readonly loadingEvents = signal(false);
  readonly eventsPage = signal(0);
  readonly eventsPages = signal(0);
  readonly eventsTotal = signal(0);
  readonly resolving = signal('');
  plate = '';
  eventPlate = '';

  constructor() {
    void this.refresh();
  }

  async refresh(): Promise<void> {
    await Promise.all([this.loadSummary(), this.loadInside(), this.loadEvents(0), this.loadAlerts()]);
  }

  async loadSummary(): Promise<void> {
    try {
      this.summary.set(await firstValueFrom(this.api.get<SecuritySummary>('/security/summary')));
    } catch {
      this.summary.set(null);
    }
  }

  async loadInside(): Promise<void> {
    try {
      this.inside.set(await firstValueFrom(this.api.get<Vehicle[]>('/access/vehicles/inside')));
    } catch {
      this.inside.set([]);
    }
  }

  async loadEvents(page = 0): Promise<void> {
    this.loadingEvents.set(true);
    try {
      const result = await firstValueFrom(this.api.get<PageResultAccessEvent>('/access/events', {
        plate: this.eventPlate.trim(),
        page,
        size: 20
      }));
      this.events.set(result.content ?? []);
      this.eventsPage.set(result.page ?? 0);
      this.eventsPages.set(result.totalPages ?? 0);
      this.eventsTotal.set(result.totalElements ?? 0);
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.loadingEvents.set(false);
    }
  }

  async loadAlerts(): Promise<void> {
    if (!this.auth.hasPermission('SECURITY_ALERTS_VIEW')) return;
    try {
      const result = await firstValueFrom(this.api.get<PageResultSecurityAlert>('/access/alerts', { onlyOpen: true, size: 20 }));
      this.alerts.set(result.content ?? []);
    } catch {
      this.alerts.set([]);
    }
  }

  async lookup(): Promise<void> {
    this.lookupError.set('');
    this.lookupResult.set(null);
    if (!this.plate.trim()) {
      this.lookupError.set('Ingresa la placa del vehículo.');
      return;
    }
    this.searching.set(true);
    try {
      this.lookupResult.set(await firstValueFrom(this.api.get<VehicleLookup>('/access/vehicles/lookup', { plate: this.plate.trim() })));
    } catch (error) {
      this.lookupError.set(describeError(error));
    } finally {
      this.searching.set(false);
    }
  }

  select(plate: string | undefined): void {
    if (!plate) return;
    this.plate = plate;
    void this.lookup();
  }

  async entry(): Promise<void> {
    await this.move('entry');
  }

  async exit(): Promise<void> {
    await this.move('exit');
  }

  async resolve(alert: SecurityAlert): Promise<void> {
    if (!alert.id) return;
    this.resolving.set(alert.id);
    try {
      await firstValueFrom(this.api.post<void>(`/access/alerts/${alert.id}/resolve`));
      this.toast.success('Alerta resuelta.');
      await Promise.all([this.loadAlerts(), this.loadSummary()]);
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.resolving.set('');
    }
  }

  unitLabel(_unitId: string | undefined): string {
    return '—';
  }

  private async move(action: 'entry' | 'exit'): Promise<void> {
    const vehicle = this.lookupResult();
    if (!vehicle?.plate) return;
    this.acting.set(true);
    try {
      await firstValueFrom(this.api.post<AccessEvent>(`/access/${action}`, { plate: vehicle.plate }));
      this.toast.success(action === 'entry' ? 'Entrada registrada.' : 'Salida registrada.');
      await Promise.all([this.lookup(), this.loadInside(), this.loadEvents(0), this.loadSummary(), this.loadAlerts()]);
    } catch (error) {
      this.lookupError.set(describeError(error));
    } finally {
      this.acting.set(false);
    }
  }
}
