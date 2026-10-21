import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ConfirmService } from '../../core/confirm.service';
import { MoneyPipe } from '../../core/format.pipes';
import { label, statusTone } from '../../core/labels';
import {
  CancelMyReservationRequest,
  CommonArea,
  CreateMyReservationRequest,
  IntentResponse,
  MyUnit,
  OnlinePaymentQuote,
  Reservation,
  TimeSlot
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { addDays, describeError, timeString, today } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-my-reservations-page',
  standalone: true,
  imports: [FormsModule, DatePipe, MoneyPipe, PageHeaderComponent, ModalComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Reservas de zonas comunes" subtitle="Consulta la disponibilidad de las zonas y reserva franjas para tus inmuebles.">
      @if (auth.hasPermission('RESERVATIONS_MANAGE_OWN')) {
        <button type="button" class="btn btn-primary" [disabled]="!units().length || !areas().length" (click)="openCreate()">Nueva reserva</button>
      }
    </app-page-header>

    <div class="grid grid-4">
      <div class="stat accent"><div class="stat-label">Reservas activas</div><div class="stat-value">{{ activeReservations() }}</div><div class="stat-hint">Aprobadas o pendientes</div></div>
      <div class="stat"><div class="stat-label">Próximas</div><div class="stat-value">{{ upcomingReservations() }}</div><div class="stat-hint">Programadas desde hoy</div></div>
      <div class="stat"><div class="stat-label">Zonas disponibles</div><div class="stat-value">{{ areas().length }}</div><div class="stat-hint">Habilitadas por la administración</div></div>
      <div class="stat"><div class="stat-label">Inmuebles</div><div class="stat-value">{{ units().length }}</div><div class="stat-hint">Asociados a tu usuario</div></div>
    </div>

    <div class="filter-bar">
      <div class="field">
        <label for="unitFilter">Inmueble</label>
        <select id="unitFilter" name="unitFilter" class="select" [(ngModel)]="unitFilter" (change)="loadReservations()">
          @for (unit of units(); track unit.unitId) { <option [value]="unit.unitId">{{ unit.identifier }} · {{ unit.tower || 'Sin torre' }}</option> }
        </select>
      </div>
      <button type="button" class="btn btn-secondary" [disabled]="!unitFilter" (click)="loadReservations()">Actualizar</button>
    </div>

    <div class="card">
      <div class="card-header"><div><h2>Mis reservas</h2><p>Historial y reservas vigentes del inmueble seleccionado.</p></div><span class="badge info">{{ reservations().length }}</span></div>
      @if (loading()) {
        <div class="loading"><span class="spinner"></span> Cargando reservas…</div>
      } @else if (!units().length) {
        <app-empty-state symbol="⌂" title="Sin inmuebles asociados" message="Tu usuario no tiene inmuebles vinculados para gestionar reservas." />
      } @else if (!reservations().length) {
        <app-empty-state symbol="▦" title="Sin reservas" message="No hay reservas registradas para el inmueble seleccionado." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Zona</th><th>Inicio</th><th>Fin</th><th class="num">Valor</th><th>Estado</th><th>Observaciones</th><th style="width:110px"></th></tr></thead>
            <tbody>
              @for (item of reservations(); track item.id) {
                <tr>
                  <td><div class="main-cell">{{ areaName(item.commonAreaId) }}</div><div class="muted small">{{ unitName(item.unitId) }}</div></td>
                  <td class="nowrap">{{ item.startTime | date:'dd/MM/yyyy HH:mm' }}</td>
                  <td class="nowrap">{{ item.endTime | date:'dd/MM/yyyy HH:mm' }}</td>
                  <td class="num">{{ item.totalPrice | money }}@if (item.depositAmount) { <div class="muted small">Depósito {{ item.depositAmount | money }}</div> }</td>
                  <td><span class="badge" [class]="'badge ' + tone(item.status)">{{ label(item.status) }}</span></td>
                  <td>
                    @if (item.cancellationReason) { <span class="muted small">Cancelación: {{ item.cancellationReason }}</span> }
                    @else if (item.rejectionReason) { <span class="muted small">Rechazo: {{ item.rejectionReason }}</span> }
                    @else { {{ item.notes || '—' }} }
                  </td>
                  <td>
                    <div class="table-actions">
                      @if ((item.status === 'PENDING_APPROVAL' || item.status === 'APPROVED' || item.status === 'PENDING_PAYMENT') && auth.hasPermission('RESERVATIONS_MANAGE_OWN')) {
                        @if (item.status === 'PENDING_PAYMENT') {
                          <button type="button" class="btn btn-primary btn-sm" [disabled]="saving()" (click)="openPay(item)">Pagar</button>
                        }
                        <button type="button" class="btn btn-ghost btn-sm" (click)="openCancel(item)">Cancelar</button>
                      }
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>

    @if (error()) { <div class="alert alert-error" style="margin-top:16px">{{ error() }}</div> }

    <app-modal [open]="createOpen()" title="Nueva reserva" subtitle="Selecciona la zona, la fecha y las franjas horarias que deseas reservar." [wide]="true" (closed)="createOpen.set(false)">
      @if (formError()) { <div class="alert alert-error" style="margin-bottom:14px">{{ formError() }}</div> }
      <div class="form-grid">
        <div class="field">
          <label for="reservationUnit">Inmueble *</label>
          <select id="reservationUnit" name="reservationUnit" class="select" [(ngModel)]="form.unitId">
            <option value="">Selecciona el inmueble</option>
            @for (unit of units(); track unit.unitId) { <option [value]="unit.unitId">{{ unit.identifier }} · {{ unit.tower || 'Sin torre' }}</option> }
          </select>
        </div>
        <div class="field">
          <label for="reservationArea">Zona común *</label>
          <select id="reservationArea" name="reservationArea" class="select" [(ngModel)]="form.commonAreaId" (change)="loadAvailability()">
            <option value="">Selecciona la zona</option>
            @for (area of areas(); track area.id) { <option [value]="area.id">{{ area.name }}</option> }
          </select>
        </div>
        <div class="field">
          <label for="reservationDate">Fecha *</label>
          <input id="reservationDate" name="reservationDate" type="date" class="input" [min]="todayDate" [(ngModel)]="date" (change)="loadAvailability()">
        </div>
        <div class="field">
          <label for="reservationNotes">Notas</label>
          <input id="reservationNotes" name="reservationNotes" class="input" maxlength="500" [(ngModel)]="form.notes" placeholder="Motivo o requerimiento especial">
        </div>
      </div>

      @if (selectedArea(); as area) {
        <div class="notice" style="margin-top:14px">
          <strong>{{ area.name }}</strong> · {{ time(area.openingTime) }} a {{ time(area.closingTime) }} · franjas de {{ area.slotDurationMinutes }} min.
          @if (area.requiresPayment) { Valor por franja: {{ area.pricePerSlot | money }}. } @else { Sin cobro. }
          @if (area.requiresApproval) { La reserva queda pendiente de aprobación. }
          Selecciona hasta {{ area.maxSlotsPerReservation }} franja(s) continua(s).
        </div>
        @if (area.terms) {
          <div class="notice" style="margin-top:12px">
            <strong>Términos y condiciones de uso</strong>
            <div class="small" style="white-space:pre-line;margin-top:6px">{{ area.terms }}</div>
            <label class="check" style="margin-top:12px"><input type="checkbox" name="termsAccepted" [(ngModel)]="termsAccepted"> Acepto los términos y condiciones de esta zona</label>
          </div>
        }
      }

      <div class="divider"></div>
      <div class="row between">
        <h3 class="section-title">Disponibilidad</h3>
        @if (loadingAvailability()) { <span class="muted small"><span class="spinner"></span> Consultando…</span> }
      </div>

      @if (!form.commonAreaId || !date) {
        <p class="muted small">Selecciona una zona y una fecha para consultar las franjas disponibles.</p>
      } @else if (!loadingAvailability() && !slots().length) {
        <p class="muted small">La zona no tiene franjas configuradas para la fecha seleccionada.</p>
      } @else {
        <div class="slot-grid">
          @for (slot of slots(); track slot.startTime) {
            <button
              type="button"
              class="slot"
              [class.selected]="isSelected(slot)"
              [class.unavailable]="!slot.available"
              [disabled]="!slot.available"
              (click)="toggleSlot(slot)">
              <span class="slot-time">{{ slot.startTime | date:'HH:mm' }}</span>
              <span class="slot-state">{{ slot.available ? (isSelected(slot) ? 'Seleccionada' : 'Disponible') : 'Ocupada' }}</span>
            </button>
          }
        </div>
      }

      @if (selectedSlots().length) {
        <div class="notice" style="margin-top:14px">
          {{ selectedSlots().length }} franja(s) seleccionada(s) ·
          {{ selectedSlots()[0].startTime | date:'HH:mm' }} a {{ selectedSlots()[selectedSlots().length - 1].endTime | date:'HH:mm' }}
          @if (selectedArea()?.requiresPayment) { · total {{ totalPrice() | money }} }
        </div>
      }

      <div class="form-actions">
        <button type="button" class="btn btn-secondary" (click)="createOpen.set(false)">Cancelar</button>
        <button type="button" class="btn btn-primary" [disabled]="saving() || !selectedSlots().length || (!!selectedArea()?.terms && !termsAccepted)" (click)="create()">{{ saving() ? 'Reservando…' : 'Confirmar reserva' }}</button>
      </div>
    </app-modal>

    <app-modal [open]="cancelOpen()" title="Cancelar reserva" subtitle="El motivo queda registrado en el historial." [narrow]="true" (closed)="cancelOpen.set(false)">
      @if (cancelError()) { <div class="alert alert-error" style="margin-bottom:14px">{{ cancelError() }}</div> }
      @if (cancelTarget(); as item) {
        <div class="notice" style="margin-bottom:14px">{{ areaName(item.commonAreaId) }} · {{ item.startTime | date:'dd/MM/yyyy HH:mm' }} a {{ item.endTime | date:'HH:mm' }}</div>
      }
      <div class="field">
        <label for="cancelReason">Motivo de la cancelación</label>
        <textarea id="cancelReason" name="cancelReason" class="textarea" maxlength="500" [(ngModel)]="cancelReason" placeholder="Explica por qué cancelas la reserva."></textarea>
      </div>
      <div class="form-actions">
        <button type="button" class="btn btn-secondary" (click)="cancelOpen.set(false)">Volver</button>
        <button type="button" class="btn btn-danger" [disabled]="saving()" (click)="confirmCancel()">{{ saving() ? 'Cancelando…' : 'Cancelar reserva' }}</button>
      </div>
    </app-modal>

    <app-modal [open]="payOpen()" title="Pagar reserva" subtitle="El valor incluye la comisión de la pasarela y la tarifa de la plataforma." [narrow]="true" (closed)="payOpen.set(false)">
      @if (payError()) { <div class="alert alert-error" style="margin-bottom:14px">{{ payError() }}</div> }
      @if (payTarget(); as item) {
        <div class="notice" style="margin-bottom:14px">{{ areaName(item.commonAreaId) }} · {{ item.startTime | date:'dd/MM/yyyy HH:mm' }} a {{ item.endTime | date:'HH:mm' }}</div>
      }
      @if (payQuote(); as q) {
        <div class="detail-list">
          <dt>Valor de la reserva</dt><dd>{{ (q.netAmountInCents ?? 0) / 100 | money }}</dd>
          <dt>Comisión de la pasarela</dt><dd>{{ (q.commissionInCents ?? 0) / 100 | money }}</dd>
          <dt>Tarifa de la plataforma</dt><dd>{{ (q.platformFeeInCents ?? 0) / 100 | money }}</dd>
          <dt>Total a pagar</dt><dd><b>{{ (q.totalInCents ?? 0) / 100 | money }}</b></dd>
        </div>
      }
      <div class="form-actions">
        <button type="button" class="btn btn-secondary" (click)="payOpen.set(false)">Volver</button>
        <button type="button" class="btn btn-primary" [disabled]="saving() || !payQuote()" (click)="confirmPay()">{{ saving() ? 'Redirigiendo…' : 'Ir a pagar' }}</button>
      </div>
    </app-modal>
  `
})
export class MyReservationsPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly units = signal<MyUnit[]>([]);
  readonly areas = signal<CommonArea[]>([]);
  readonly reservations = signal<Reservation[]>([]);
  readonly slots = signal<TimeSlot[]>([]);
  readonly loading = signal(false);
  readonly loadingAvailability = signal(false);
  readonly saving = signal(false);
  readonly createOpen = signal(false);
  readonly cancelOpen = signal(false);
  readonly cancelTarget = signal<Reservation | null>(null);
  readonly payOpen = signal(false);
  readonly payTarget = signal<Reservation | null>(null);
  readonly payQuote = signal<OnlinePaymentQuote | null>(null);
  readonly payError = signal('');
  readonly error = signal('');
  readonly formError = signal('');
  readonly cancelError = signal('');
  readonly todayDate = today();
  readonly selectedArea = computed(() => this.areas().find((area) => area.id === this.form.commonAreaId) ?? null);
  readonly selectedSlots = computed(() => {
    const indexes = [...this.selection].sort((left, right) => left - right);
    const slots = this.slots();
    return indexes.map((index) => slots[index]).filter((slot): slot is TimeSlot => Boolean(slot));
  });
  readonly totalPrice = computed(() => {
    const area = this.selectedArea();
    if (!area?.requiresPayment) return 0;
    return Number(area.pricePerSlot ?? 0) * this.selectedSlots().length;
  });
  readonly activeReservations = computed(() =>
    this.reservations().filter((item) => item.status === 'APPROVED' || item.status === 'PENDING_APPROVAL' || item.status === 'PENDING_PAYMENT').length
  );
  readonly upcomingReservations = computed(() => {
    const now = Date.now();
    return this.reservations().filter((item) => {
      const start = item.startTime ? new Date(item.startTime).getTime() : 0;
      return start >= now && item.status !== 'CANCELLED' && item.status !== 'REJECTED';
    }).length;
  });
  unitFilter = '';
  date = addDays(1);
  termsAccepted = false;
  cancelReason = '';
  form: CreateMyReservationRequest = this.emptyForm();
  private selection: number[] = [];

  constructor() {
    void this.loadUnits();
    void this.loadAreas();
  }

  time(value: { hour?: number; minute?: number } | undefined): string {
    return timeString(value) || '—';
  }

  areaName(id: string | undefined): string {
    return this.areas().find((area) => area.id === id)?.name ?? '—';
  }

  unitName(id: string | undefined): string {
    return this.units().find((unit) => unit.unitId === id)?.identifier ?? '—';
  }

  async loadUnits(): Promise<void> {
    try {
      const units = await firstValueFrom(this.api.get<MyUnit[]>('/my/units'));
      this.units.set(units);
      this.unitFilter = units[0]?.unitId ?? '';
      await this.loadReservations();
    } catch (error) {
      this.error.set(describeError(error));
    }
  }

  async loadAreas(): Promise<void> {
    try {
      this.areas.set(await firstValueFrom(this.api.get<CommonArea[]>('/common-areas', { onlyActive: true })));
    } catch {
      this.areas.set([]);
    }
  }

  async loadReservations(): Promise<void> {
    if (!this.unitFilter) {
      this.reservations.set([]);
      return;
    }
    this.loading.set(true);
    this.error.set('');
    try {
      this.reservations.set(await firstValueFrom(this.api.get<Reservation[]>('/my/reservations', { unitId: this.unitFilter })));
    } catch (error) {
      this.reservations.set([]);
      this.error.set(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  async openCreate(): Promise<void> {
    this.form = this.emptyForm();
    this.form.unitId = this.units().length === 1 ? this.units()[0].unitId ?? '' : this.unitFilter;
    this.form.commonAreaId = this.areas().length === 1 ? this.areas()[0].id ?? '' : '';
    this.date = addDays(1);
    this.selection = [];
    this.slots.set([]);
    this.termsAccepted = false;
    this.formError.set('');
    this.createOpen.set(true);
    if (this.form.commonAreaId) await this.loadAvailability();
  }

  async loadAvailability(): Promise<void> {
    this.selection = [];
    this.slots.set([]);
    this.termsAccepted = false;
    if (!this.form.commonAreaId || !this.date) return;
    this.loadingAvailability.set(true);
    this.formError.set('');
    try {
      this.slots.set(await firstValueFrom(this.api.get<TimeSlot[]>(`/common-areas/${this.form.commonAreaId}/availability`, { date: this.date })));
    } catch (error) {
      this.formError.set(describeError(error));
    } finally {
      this.loadingAvailability.set(false);
    }
  }

  isSelected(slot: TimeSlot): boolean {
    return this.slots().findIndex((item) => item.startTime === slot.startTime) >= 0
      && this.selection.includes(this.slots().findIndex((item) => item.startTime === slot.startTime));
  }

  toggleSlot(slot: TimeSlot): void {
    if (!slot.available) return;
    const index = this.slots().findIndex((item) => item.startTime === slot.startTime);
    if (index < 0) return;
    const current = this.selection.indexOf(index);
    if (current >= 0) {
      this.selection.splice(current, 1);
      return;
    }
    const area = this.selectedArea();
    const maximum = area?.maxSlotsPerReservation ?? 1;
    if (this.selection.length >= maximum) {
      this.toast.warning(`Puedes seleccionar hasta ${maximum} franja(s) por reserva.`);
      return;
    }
    this.selection.push(index);
  }

  async create(): Promise<void> {
    this.formError.set('');
    const slots = this.selectedSlots();
    if (!this.form.unitId || !this.form.commonAreaId || !slots.length) {
      this.formError.set('Selecciona el inmueble, la zona y al menos una franja disponible.');
      return;
    }
    const indexes = [...this.selection].sort((left, right) => left - right);
    const contiguous = indexes.every((value, position) => position === 0 || value === indexes[position - 1] + 1);
    if (!contiguous) {
      this.formError.set('Las franjas seleccionadas deben ser continuas.');
      return;
    }
    const start = slots[0]?.startTime;
    const end = slots[slots.length - 1]?.endTime;
    if (!start || !end) {
      this.formError.set('No fue posible determinar el horario de la reserva.');
      return;
    }
    const body: CreateMyReservationRequest = {
      unitId: this.form.unitId,
      commonAreaId: this.form.commonAreaId,
      startTime: start,
      endTime: end,
      paymentMethod: 'NONE',
      notes: this.form.notes?.trim() || undefined,
      acceptedTerms: this.termsAccepted
    };
    const area = this.selectedArea();
    if (area?.terms && !this.termsAccepted) {
      this.formError.set('Debes aceptar los términos y condiciones de la zona para continuar.');
      return;
    }
    this.saving.set(true);
    try {
      const reservation = await firstValueFrom(this.api.post<Reservation>('/my/reservations', body));
      this.createOpen.set(false);
      this.unitFilter = body.unitId;
      await this.loadReservations();
      if (reservation.status === 'PENDING_PAYMENT') {
        this.toast.success('Reserva registrada. Completa el pago para confirmarla.');
        await this.pay(reservation);
      } else if (reservation.status === 'PENDING_APPROVAL') {
        this.toast.success('Reserva registrada. Queda pendiente de aprobación de la administración.');
      } else {
        this.toast.success('Reserva confirmada.');
      }
    } catch (error) {
      this.formError.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async pay(item: Reservation): Promise<void> {
    if (!item.id) return;
    this.saving.set(true);
    this.payError.set('');
    try {
      const intent = await firstValueFrom(this.api.post<IntentResponse>(`/my/reservations/${item.id}/checkout`, {}));
      if (intent.checkoutUrl) {
        window.location.assign(intent.checkoutUrl);
        return;
      }
      this.toast.error('No fue posible iniciar el pago en línea.');
    } catch (error) {
      this.payError.set(describeError(error));
      this.toast.error(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  /** Muestra el desglose del valor que se cobrará antes de enviar a la pasarela. */
  async openPay(item: Reservation): Promise<void> {
    this.payTarget.set(item);
    this.payError.set('');
    this.payQuote.set(null);
    this.payOpen.set(true);
    try {
      const quote = await firstValueFrom(this.api.get<OnlinePaymentQuote>('/my/payments/online/quote', { amount: item.totalPrice ?? 0 }));
      this.payQuote.set(quote);
    } catch (error) {
      this.payError.set(describeError(error));
    }
  }

  async confirmPay(): Promise<void> {
    const item = this.payTarget();
    if (!item) return;
    await this.pay(item);
    if (!this.payError()) {
      this.payOpen.set(false);
    }
  }

  openCancel(item: Reservation): void {
    this.cancelTarget.set(item);
    this.cancelReason = '';
    this.cancelError.set('');
    this.cancelOpen.set(true);
  }

  async confirmCancel(): Promise<void> {
    const item = this.cancelTarget();
    if (!item?.id) return;
    const accepted = await this.confirm.confirm({
      title: 'Cancelar reserva',
      message: 'La reserva dejará de estar vigente y la franja quedará disponible para otros inmuebles.',
      confirmLabel: 'Continuar'
    });
    if (!accepted) return;
    this.saving.set(true);
    this.cancelError.set('');
    try {
      const body: CancelMyReservationRequest = { reason: this.cancelReason.trim() || undefined };
      await firstValueFrom(this.api.post<Reservation>(`/my/reservations/${item.id}/cancel`, body));
      this.toast.success('Reserva cancelada.');
      this.cancelOpen.set(false);
      await this.loadReservations();
    } catch (error) {
      this.cancelError.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  private emptyForm(): CreateMyReservationRequest {
    return { unitId: '', commonAreaId: '', startTime: '', endTime: '', paymentMethod: 'NONE', notes: '', acceptedTerms: false };
  }
}
