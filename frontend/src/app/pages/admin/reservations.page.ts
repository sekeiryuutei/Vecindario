import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ConfirmService } from '../../core/confirm.service';
import { MoneyPipe } from '../../core/format.pipes';
import { label, statusTone } from '../../core/labels';
import {
  CommonArea,
  CommonAreaRequest,
  PageResultPropertyUnit,
  PageResultReservation,
  PropertyUnit,
  Reservation
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError, timeString, toLocalTime, today } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent, PaginationComponent } from '../../shared/ui.components';

type ReservationsTab = 'reservations' | 'areas';

interface AreaForm {
  name: string;
  description: string;
  capacity: number;
  requiresPayment: boolean;
  pricePerSlot: number;
  depositAmount: number;
  requiresApproval: boolean;
  opening: string;
  closing: string;
  slotDurationMinutes: number;
  maxSlotsPerReservation: number;
  maxActiveReservationsPerUnit: number;
  minAdvanceHours: number;
  maxAdvanceDays: number;
  cancellationDeadlineHours: number;
}

@Component({
  selector: 'app-reservations-page',
  standalone: true,
  imports: [FormsModule, DatePipe, MoneyPipe, PageHeaderComponent, ModalComponent, PaginationComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Reservas y zonas comunes" subtitle="Configura zonas, horarios y reglas de reserva; aprueba o cancela solicitudes.">
      @if (auth.hasPermission('COMMON_AREAS_MANAGE')) {
        <button type="button" class="btn btn-primary" (click)="openArea()">Nueva zona común</button>
      }
    </app-page-header>

    <div class="tabs">
      <button type="button" class="tab" [class.active]="tab() === 'reservations'" (click)="setTab('reservations')">Reservas</button>
      <button type="button" class="tab" [class.active]="tab() === 'areas'" (click)="setTab('areas')">Zonas comunes</button>
    </div>

    @if (tab() === 'reservations') {
      <div class="filter-bar">
        <div class="field">
          <label for="areaFilter">Zona</label>
          <select id="areaFilter" name="areaFilter" class="select" [(ngModel)]="areaFilter" (change)="loadReservations(0)">
            <option value="">Todas</option>
            @for (area of areas(); track area.id) { <option [value]="area.id">{{ area.name }}</option> }
          </select>
        </div>
        <div class="field">
          <label for="unitFilter">Inmueble</label>
          <select id="unitFilter" name="unitFilter" class="select" [(ngModel)]="unitFilter" (change)="loadReservations(0)">
            <option value="">Todos</option>
            @for (unit of units(); track unit.id) { <option [value]="unit.id">{{ unit.identifier }}</option> }
          </select>
        </div>
        <div class="field">
          <label for="statusFilter">Estado</label>
          <select id="statusFilter" name="statusFilter" class="select" [(ngModel)]="statusFilter" (change)="loadReservations(0)">
            <option value="">Todos</option>
            @for (option of statuses; track option) { <option [value]="option">{{ label(option) }}</option> }
          </select>
        </div>
        <div class="field"><label for="from">Desde</label><input id="from" name="from" type="date" class="input" [(ngModel)]="from" (change)="loadReservations(0)"></div>
        <div class="field"><label for="to">Hasta</label><input id="to" name="to" type="date" class="input" [(ngModel)]="to" (change)="loadReservations(0)"></div>
        <button type="button" class="btn btn-secondary" (click)="loadReservations(0)">Filtrar</button>
        <button type="button" class="btn btn-ghost" (click)="clearFilters()">Limpiar</button>
      </div>

      <div class="card">
        <div class="card-header"><div><h2>Solicitudes y reservas</h2><p>{{ totalElements() }} reserva(s) encontradas.</p></div><button type="button" class="btn btn-secondary btn-sm" (click)="loadReservations(page())">Actualizar</button></div>
        @if (loading()) {
          <div class="loading"><span class="spinner"></span> Cargando reservas…</div>
        } @else if (!reservations().length) {
          <app-empty-state symbol="▦" title="Sin reservas" message="No hay reservas para los filtros seleccionados." />
        } @else {
          <div class="table-wrap">
            <table class="table">
              <thead><tr><th>Zona</th><th>Inmueble</th><th>Inicio</th><th>Fin</th><th class="num">Valor</th><th>Estado</th><th style="width:150px"></th></tr></thead>
              <tbody>
                @for (item of reservations(); track item.id) {
                  <tr>
                    <td class="main-cell">{{ areaName(item.commonAreaId) }}</td>
                    <td>{{ unitName(item.unitId) }}</td>
                    <td>{{ item.startTime | date:'dd/MM/yyyy HH:mm' }}</td>
                    <td>{{ item.endTime | date:'dd/MM/yyyy HH:mm' }}</td>
                    <td class="num">{{ item.totalPrice | money }}</td>
                    <td><span class="badge" [class]="'badge ' + tone(item.status)">{{ label(item.status) }}</span></td>
                    <td>
                      <div class="table-actions">
                        @if (item.status === 'PENDING_APPROVAL' && auth.hasPermission('RESERVATIONS_APPROVE')) {
                          <button type="button" class="btn btn-primary btn-sm" (click)="approve(item)">Aprobar</button>
                          <button type="button" class="btn btn-secondary btn-sm" (click)="openDecision(item, 'reject')">Rechazar</button>
                        }
                        @if ((item.status === 'PENDING_APPROVAL' || item.status === 'APPROVED') && auth.hasPermission('RESERVATIONS_APPROVE')) {
                          <button type="button" class="btn btn-ghost btn-sm" (click)="openDecision(item, 'cancel')">Cancelar</button>
                        }
                        @if (item.rejectionReason) { <span class="muted small" [title]="item.rejectionReason">Ver motivo</span> }
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <app-pagination [page]="page()" [totalPages]="totalPages()" [totalElements]="totalElements()" (pageChange)="loadReservations($event)" />
        }
      </div>
    }

    @if (tab() === 'areas') {
      <div class="grid grid-2">
        @for (area of areas(); track area.id) {
          <div class="card">
            <div class="card-header">
              <div><h2>{{ area.name }}</h2><p>{{ area.description || 'Zona común de la copropiedad' }}</p></div>
              <span class="badge" [class]="area.isActive ? 'badge success' : 'badge neutral'">{{ area.isActive ? 'Activa' : 'Inactiva' }}</span>
            </div>
            <div class="card-body">
              <dl class="detail-list">
                <dt>Horario</dt><dd>{{ time(area.openingTime) }} a {{ time(area.closingTime) }}</dd>
                <dt>Capacidad</dt><dd>{{ area.capacity }} personas</dd>
                <dt>Franjas</dt><dd>{{ area.slotDurationMinutes }} min · hasta {{ area.maxSlotsPerReservation }} por reserva</dd>
                <dt>Reglas</dt><dd>{{ area.requiresApproval ? 'Requiere aprobación' : 'Aprobación automática' }} · {{ area.maxActiveReservationsPerUnit }} activas por inmueble</dd>
                <dt>Tarifa</dt><dd>{{ area.requiresPayment ? (area.pricePerSlot | money) + ' por franja' : 'Sin cobro' }}@if (area.depositAmount) { · depósito {{ area.depositAmount | money }} }</dd>
              </dl>
              @if (auth.hasPermission('COMMON_AREAS_MANAGE')) {
                <div class="form-actions">
                  <button type="button" class="btn btn-secondary btn-sm" (click)="openArea(area)">Editar</button>
                  <button type="button" class="btn btn-danger btn-sm" (click)="deactivate(area)">Inactivar</button>
                </div>
              }
            </div>
          </div>
        } @empty {
          <div class="card"><app-empty-state symbol="⌂" title="Sin zonas comunes" message="Configura la primera zona para habilitar reservas." /></div>
        }
      </div>
    }

    <app-modal [open]="areaOpen()" [title]="editingArea() ? 'Editar zona común' : 'Nueva zona común'" subtitle="Define horarios, tarifas y límites de uso." [wide]="true" (closed)="areaOpen.set(false)">
      @if (error()) { <div class="alert alert-error" style="margin-bottom:14px">{{ error() }}</div> }
      <form (ngSubmit)="saveArea()" novalidate>
        <div class="form-grid">
          <div class="field span-2"><label for="areaName">Nombre *</label><input id="areaName" name="areaName" class="input" required [(ngModel)]="areaForm.name" placeholder="Ej. Salón social"></div>
          <div class="field span-2"><label for="areaDescription">Descripción</label><textarea id="areaDescription" name="areaDescription" class="textarea" [(ngModel)]="areaForm.description"></textarea></div>
          <div class="field"><label for="capacity">Capacidad *</label><input id="capacity" name="capacity" type="number" min="1" class="input" [(ngModel)]="areaForm.capacity"></div>
          <div class="field"><label for="slot">Duración de franja (min) *</label><input id="slot" name="slot" type="number" min="15" step="15" class="input" [(ngModel)]="areaForm.slotDurationMinutes"></div>
          <div class="field"><label for="opening">Hora de apertura *</label><input id="opening" name="opening" type="time" class="input" [(ngModel)]="areaForm.opening"></div>
          <div class="field"><label for="closing">Hora de cierre *</label><input id="closing" name="closing" type="time" class="input" [(ngModel)]="areaForm.closing"></div>
          <div class="field"><label for="maxSlots">Franjas por reserva *</label><input id="maxSlots" name="maxSlots" type="number" min="1" class="input" [(ngModel)]="areaForm.maxSlotsPerReservation"></div>
          <div class="field"><label for="maxActive">Reservas activas por inmueble *</label><input id="maxActive" name="maxActive" type="number" min="1" class="input" [(ngModel)]="areaForm.maxActiveReservationsPerUnit"></div>
          <div class="field"><label for="minAdvance">Anticipación mínima (horas) *</label><input id="minAdvance" name="minAdvance" type="number" min="0" class="input" [(ngModel)]="areaForm.minAdvanceHours"></div>
          <div class="field"><label for="maxAdvance">Anticipación máxima (días) *</label><input id="maxAdvance" name="maxAdvance" type="number" min="1" class="input" [(ngModel)]="areaForm.maxAdvanceDays"></div>
          <div class="field"><label for="cancelDeadline">Cancelación hasta (horas antes) *</label><input id="cancelDeadline" name="cancelDeadline" type="number" min="0" class="input" [(ngModel)]="areaForm.cancellationDeadlineHours"></div>
          <div class="field"><label class="check"><input type="checkbox" [(ngModel)]="areaForm.requiresApproval"> Requiere aprobación de administración</label></div>
          <div class="field"><label class="check"><input type="checkbox" [(ngModel)]="areaForm.requiresPayment"> Cobrar por uso</label></div>
          <div class="field"><label for="price">Valor por franja</label><input id="price" name="price" type="number" min="0" step="0.01" class="input" [(ngModel)]="areaForm.pricePerSlot" [disabled]="!areaForm.requiresPayment"></div>
          <div class="field"><label for="deposit">Depósito</label><input id="deposit" name="deposit" type="number" min="0" step="0.01" class="input" [(ngModel)]="areaForm.depositAmount"></div>
        </div>
        <div class="form-actions">
          <button type="button" class="btn btn-secondary" (click)="areaOpen.set(false)">Cancelar</button>
          <button type="submit" class="btn btn-primary" [disabled]="saving()">{{ editingArea() ? 'Guardar cambios' : 'Crear zona' }}</button>
        </div>
      </form>
    </app-modal>

    <app-modal [open]="decisionOpen()" [title]="decisionType() === 'reject' ? 'Rechazar reserva' : 'Cancelar reserva'" subtitle="El motivo queda registrado en el historial." [narrow]="true" (closed)="decisionOpen.set(false)">
      <div class="field"><label for="reason">Motivo</label><textarea id="reason" name="reason" class="textarea" [(ngModel)]="decisionReason" placeholder="Explica la decisión a la comunidad."></textarea></div>
      <div class="form-actions">
        <button type="button" class="btn btn-secondary" (click)="decisionOpen.set(false)">Volver</button>
        <button type="button" class="btn" [class.btn-danger]="decisionType() === 'reject'" [class.btn-primary]="decisionType() === 'cancel'" [disabled]="saving()" (click)="submitDecision()">{{ decisionType() === 'reject' ? 'Rechazar' : 'Cancelar reserva' }}</button>
      </div>
    </app-modal>
  `
})
export class ReservationsPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly statuses = ['PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'CANCELLED', 'COMPLETED'];
  readonly tab = signal<ReservationsTab>('reservations');
  readonly reservations = signal<Reservation[]>([]);
  readonly areas = signal<CommonArea[]>([]);
  readonly units = signal<PropertyUnit[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly areaOpen = signal(false);
  readonly editingArea = signal<CommonArea | null>(null);
  readonly error = signal('');
  readonly decisionOpen = signal(false);
  readonly decisionType = signal<'reject' | 'cancel'>('reject');
  areaFilter = '';
  unitFilter = '';
  statusFilter = '';
  from = '';
  to = '';
  decisionReason = '';
  private selectedReservation: Reservation | null = null;
  areaForm: AreaForm = this.emptyArea();

  constructor() {
    void this.loadAreas();
    void this.loadUnits();
    void this.loadReservations(0);
  }

  setTab(tab: ReservationsTab): void {
    this.tab.set(tab);
    if (tab === 'areas' && !this.areas().length) void this.loadAreas();
  }

  time(value: { hour?: number; minute?: number } | undefined): string {
    return timeString(value) || '—';
  }

  areaName(id: string | undefined): string {
    return this.areas().find((area) => area.id === id)?.name ?? '—';
  }

  unitName(id: string | undefined): string {
    return this.units().find((unit) => unit.id === id)?.identifier ?? '—';
  }

  async loadAreas(): Promise<void> {
    try {
      const result = await firstValueFrom(this.api.get<CommonArea[]>('/common-areas', { onlyActive: false }));
      this.areas.set(result);
    } catch {
      // El interceptor global presenta el error.
    }
  }

  async loadUnits(): Promise<void> {
    try {
      const result = await firstValueFrom(this.api.get<PageResultPropertyUnit>('/properties', { size: 200 }));
      this.units.set(result.content ?? []);
    } catch {
      // El selector se reintenta al abrir filtros.
    }
  }

  async loadReservations(page = 0): Promise<void> {
    this.loading.set(true);
    try {
      const result = await firstValueFrom(this.api.get<PageResultReservation>('/reservations', {
        commonAreaId: this.areaFilter,
        unitId: this.unitFilter,
        status: this.statusFilter,
        from: this.from ? `${this.from}T00:00:00` : '',
        to: this.to ? `${this.to}T23:59:59` : '',
        page,
        size: 20
      }));
      this.reservations.set(result.content ?? []);
      this.page.set(result.page ?? 0);
      this.totalPages.set(result.totalPages ?? 0);
      this.totalElements.set(result.totalElements ?? 0);
    } finally {
      this.loading.set(false);
    }
  }

  clearFilters(): void {
    this.areaFilter = '';
    this.unitFilter = '';
    this.statusFilter = '';
    this.from = '';
    this.to = '';
    void this.loadReservations(0);
  }

  openArea(area?: CommonArea): void {
    this.error.set('');
    this.editingArea.set(area ?? null);
    this.areaForm = area ? {
      name: area.name ?? '',
      description: area.description ?? '',
      capacity: area.capacity ?? 1,
      requiresPayment: area.requiresPayment ?? false,
      pricePerSlot: area.pricePerSlot ?? 0,
      depositAmount: area.depositAmount ?? 0,
      requiresApproval: area.requiresApproval ?? false,
      opening: timeString(area.openingTime) || '08:00',
      closing: timeString(area.closingTime) || '22:00',
      slotDurationMinutes: area.slotDurationMinutes ?? 60,
      maxSlotsPerReservation: area.maxSlotsPerReservation ?? 1,
      maxActiveReservationsPerUnit: area.maxActiveReservationsPerUnit ?? 2,
      minAdvanceHours: area.minAdvanceHours ?? 0,
      maxAdvanceDays: area.maxAdvanceDays ?? 30,
      cancellationDeadlineHours: area.cancellationDeadlineHours ?? 24
    } : this.emptyArea();
    this.areaOpen.set(true);
  }

  async saveArea(): Promise<void> {
    this.error.set('');
    if (!this.areaForm.name.trim() || !this.areaForm.opening || !this.areaForm.closing) {
      this.error.set('Completa el nombre y el horario de la zona.');
      return;
    }
    if (this.areaForm.opening >= this.areaForm.closing) {
      this.error.set('La hora de apertura debe ser anterior a la de cierre.');
      return;
    }
    const body: CommonAreaRequest = {
      name: this.areaForm.name.trim(),
      description: this.areaForm.description.trim() || undefined,
      capacity: Number(this.areaForm.capacity),
      requiresPayment: this.areaForm.requiresPayment,
      pricePerSlot: this.areaForm.requiresPayment ? Number(this.areaForm.pricePerSlot) : 0,
      depositAmount: Number(this.areaForm.depositAmount || 0),
      requiresApproval: this.areaForm.requiresApproval,
      openingTime: toLocalTime(this.areaForm.opening),
      closingTime: toLocalTime(this.areaForm.closing),
      slotDurationMinutes: Number(this.areaForm.slotDurationMinutes),
      maxSlotsPerReservation: Number(this.areaForm.maxSlotsPerReservation),
      maxActiveReservationsPerUnit: Number(this.areaForm.maxActiveReservationsPerUnit),
      minAdvanceHours: Number(this.areaForm.minAdvanceHours),
      maxAdvanceDays: Number(this.areaForm.maxAdvanceDays),
      cancellationDeadlineHours: Number(this.areaForm.cancellationDeadlineHours)
    };
    this.saving.set(true);
    try {
      const editing = this.editingArea();
      if (editing?.id) {
        await firstValueFrom(this.api.put<CommonArea>(`/common-areas/${editing.id}`, body));
        this.toast.success('Zona común actualizada.');
      } else {
        await firstValueFrom(this.api.post<CommonArea>('/common-areas', body));
        this.toast.success('Zona común creada.');
      }
      this.areaOpen.set(false);
      await this.loadAreas();
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async deactivate(area: CommonArea): Promise<void> {
    if (!area.id) return;
    const accepted = await this.confirm.confirm({
      title: 'Inactivar zona común',
      message: `¿Deseas inactivar ${area.name}? No se aceptarán nuevas reservas para esta zona.`,
      confirmLabel: 'Inactivar',
      danger: true
    });
    if (!accepted) return;
    await firstValueFrom(this.api.delete<void>(`/common-areas/${area.id}`));
    this.toast.success('Zona común inactivada.');
    await this.loadAreas();
  }

  async approve(item: Reservation): Promise<void> {
    if (!item.id) return;
    await firstValueFrom(this.api.post<Reservation>(`/reservations/${item.id}/approve`));
    this.toast.success('Reserva aprobada.');
    await this.loadReservations(this.page());
  }

  openDecision(item: Reservation, type: 'reject' | 'cancel'): void {
    this.selectedReservation = item;
    this.decisionType.set(type);
    this.decisionReason = '';
    this.decisionOpen.set(true);
  }

  async submitDecision(): Promise<void> {
    const item = this.selectedReservation;
    if (!item?.id) return;
    this.saving.set(true);
    try {
      const path = this.decisionType() === 'reject' ? 'reject' : 'cancel';
      await firstValueFrom(this.api.post<Reservation>(`/reservations/${item.id}/${path}`, { reason: this.decisionReason.trim() || undefined }));
      this.toast.success(this.decisionType() === 'reject' ? 'Reserva rechazada.' : 'Reserva cancelada.');
      this.decisionOpen.set(false);
      await this.loadReservations(this.page());
    } finally {
      this.saving.set(false);
    }
  }

  private emptyArea(): AreaForm {
    return {
      name: '',
      description: '',
      capacity: 10,
      requiresPayment: false,
      pricePerSlot: 0,
      depositAmount: 0,
      requiresApproval: false,
      opening: '08:00',
      closing: '22:00',
      slotDurationMinutes: 60,
      maxSlotsPerReservation: 2,
      maxActiveReservationsPerUnit: 2,
      minAdvanceHours: 2,
      maxAdvanceDays: 30,
      cancellationDeadlineHours: 24
    };
  }

  protected readonly today = today();
}
