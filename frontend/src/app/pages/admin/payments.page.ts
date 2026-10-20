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
  PageResultPayment,
  PageResultPropertyUnit,
  Payment,
  PaymentResult,
  PaymentRequest,
  PropertyUnit
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { addDays, describeError, today } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent, PaginationComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-payments-page',
  standalone: true,
  imports: [FormsModule, DatePipe, MoneyPipe, PageHeaderComponent, ModalComponent, PaginationComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Pagos" subtitle="Registro manual de recaudos, imputación automática y reversos con trazabilidad.">
      @if (canCreate()) { <button type="button" class="btn btn-primary" (click)="openCreate()">Registrar pago</button> }
    </app-page-header>

    <div class="filter-bar">
      <div class="field">
        <label for="filterUnit">Inmueble</label>
        <select id="filterUnit" name="filterUnit" class="select" [(ngModel)]="filterUnit" (change)="load(0)">
          <option value="">Todos</option>
          @for (unit of units(); track unit.id) { <option [value]="unit.id">{{ unit.identifier }}</option> }
        </select>
      </div>
      <div class="field"><label for="from">Desde</label><input id="from" name="from" type="date" class="input" [(ngModel)]="from" (change)="load(0)"></div>
      <div class="field"><label for="to">Hasta</label><input id="to" name="to" type="date" class="input" [(ngModel)]="to" (change)="load(0)"></div>
      <button type="button" class="btn btn-secondary" (click)="load(0)">Filtrar</button>
      <button type="button" class="btn btn-ghost" (click)="clearFilters()">Limpiar</button>
    </div>

    <div class="card">
      <div class="card-header"><div><h2>Pagos registrados</h2><p>{{ totalElements() }} pago(s) en el periodo consultado.</p></div></div>
      @if (loading()) {
        <div class="loading"><span class="spinner"></span> Cargando pagos…</div>
      } @else if (!payments().length) {
        <app-empty-state symbol="¢" title="Sin pagos" message="No hay pagos registrados con los filtros actuales." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Fecha</th><th>Inmueble</th><th>Pagador</th><th>Método</th><th class="num">Valor</th><th class="num">Sin aplicar</th><th>Estado</th><th style="width:100px"></th></tr></thead>
            <tbody>
              @for (payment of payments(); track payment.id) {
                <tr>
                  <td>{{ payment.paymentDate | date:'dd/MM/yyyy' }}</td>
                  <td class="main-cell">{{ payment.unitIdentifier }}</td>
                  <td>{{ payment.payerName || '—' }}</td>
                  <td>{{ label(payment.method) }}</td>
                  <td class="num">{{ payment.amount | money }}</td>
                  <td class="num">{{ payment.unappliedAmount | money }}</td>
                  <td><span class="badge" [class]="'badge ' + tone(payment.status)">{{ label(payment.status) }}</span></td>
                  <td>
                    <div class="table-actions">
                      <button type="button" class="btn-icon" title="Ver detalle" (click)="openDetail(payment)">👁</button>
                      @if (canReverse() && !payment.reversedAt) { <button type="button" class="btn-icon danger" title="Reversar" (click)="openReverse(payment)">↺</button> }
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-pagination [page]="page()" [totalPages]="totalPages()" [totalElements]="totalElements()" (pageChange)="load($event)" />
      }
    </div>

    <app-modal [open]="createOpen()" title="Registrar pago" subtitle="El pago se imputa automáticamente según la configuración de cartera." (closed)="createOpen.set(false)">
      @if (createError()) { <div class="alert alert-error" style="margin-bottom:14px">{{ createError() }}</div> }
      <form (ngSubmit)="create()" novalidate>
        <div class="form-grid">
          <div class="field">
            <label for="unit">Inmueble *</label>
            <select id="unit" name="unit" class="select" required [(ngModel)]="form.unitId">
              <option value="">Selecciona</option>
              @for (unit of units(); track unit.id) { <option [value]="unit.id">{{ unit.identifier }}</option> }
            </select>
          </div>
          <div class="field">
            <label for="method">Método *</label>
            <select id="method" name="method" class="select" [(ngModel)]="form.method">
              <option value="CASH">Efectivo</option><option value="TRANSFER">Transferencia</option><option value="WOMPI">Wompi</option><option value="OTHER">Otro</option>
            </select>
          </div>
          <div class="field"><label for="amount">Valor *</label><input id="amount" name="amount" type="number" min="0.01" step="0.01" class="input" required [(ngModel)]="form.amount"></div>
          <div class="field"><label for="paymentDate">Fecha del pago *</label><input id="paymentDate" name="paymentDate" type="date" class="input" required [(ngModel)]="form.paymentDate"></div>
          <div class="field"><label for="payerName">Pagador</label><input id="payerName" name="payerName" class="input" [(ngModel)]="form.payerName"></div>
          <div class="field"><label for="reference">Referencia</label><input id="reference" name="reference" class="input" [(ngModel)]="form.reference"></div>
          <div class="field span-2"><label for="notes">Notas</label><textarea id="notes" name="notes" class="textarea" [(ngModel)]="form.notes"></textarea></div>
        </div>
        <div class="form-actions">
          <button type="button" class="btn btn-secondary" (click)="createOpen.set(false)">Cancelar</button>
          <button type="submit" class="btn btn-primary" [disabled]="saving()">{{ saving() ? 'Registrando…' : 'Registrar pago' }}</button>
        </div>
      </form>
    </app-modal>

    <app-modal [open]="detailOpen()" title="Detalle del pago" subtitle="Imputaciones aplicadas al pago." [wide]="true" (closed)="detailOpen.set(false)">
      @if (detail()) {
        <dl class="detail-list">
          <dt>Inmueble</dt><dd>{{ detail()!.payment?.unitIdentifier }}</dd>
          <dt>Pagador</dt><dd>{{ detail()!.payment?.payerName || '—' }}</dd>
          <dt>Método</dt><dd>{{ label(detail()!.payment?.method) }}</dd>
          <dt>Fecha</dt><dd>{{ detail()!.payment?.paymentDate | date:'dd/MM/yyyy' }}</dd>
          <dt>Valor</dt><dd>{{ detail()!.payment?.amount | money }}</dd>
          <dt>Sin aplicar</dt><dd>{{ detail()!.payment?.unappliedAmount | money }}</dd>
          <dt>Referencia</dt><dd class="mono">{{ detail()!.payment?.reference || '—' }}</dd>
          <dt>Estado</dt><dd>{{ label(detail()!.payment?.status) }}</dd>
        </dl>
        <div class="divider"></div>
        <h3 class="section-title">Imputaciones</h3>
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Concepto</th><th>Descripción</th><th class="num">Valor aplicado</th><th>Reversado</th></tr></thead>
            <tbody>
              @for (allocation of detail()!.allocations; track allocation.chargeId) {
                <tr><td>{{ label(allocation.conceptType) }}</td><td>{{ allocation.chargeDescription }}</td><td class="num">{{ allocation.amount | money }}</td><td>{{ allocation.reversedAt ? (allocation.reversedAt | date:'dd/MM/yyyy') : 'No' }}</td></tr>
              } @empty { <tr><td colspan="4" class="table-empty">El pago no tiene imputaciones registradas.</td></tr> }
            </tbody>
          </table>
        </div>
      }
    </app-modal>

    <app-modal [open]="reverseOpen()" title="Reversar pago" subtitle="El reverso libera las imputaciones y conserva el historial." [narrow]="true" (closed)="reverseOpen.set(false)">
      <div class="field"><label for="reverseReason">Motivo *</label><textarea id="reverseReason" name="reverseReason" class="textarea" [(ngModel)]="reverseReason"></textarea></div>
      <div class="form-actions">
        <button type="button" class="btn btn-secondary" (click)="reverseOpen.set(false)">Cancelar</button>
        <button type="button" class="btn btn-danger" [disabled]="saving()" (click)="reverse()">Reversar pago</button>
      </div>
    </app-modal>
  `
})
export class PaymentsPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly payments = signal<Payment[]>([]);
  readonly units = signal<PropertyUnit[]>([]);
  readonly loading = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly createOpen = signal(false);
  readonly createError = signal('');
  readonly saving = signal(false);
  readonly detailOpen = signal(false);
  readonly detail = signal<PaymentResult | null>(null);
  readonly reverseOpen = signal(false);
  reverseReason = '';
  reversePayment: Payment | null = null;
  filterUnit = '';
  from = addDays(-30);
  to = today();
  form: PaymentRequest = { unitId: '', amount: 0, paymentDate: today(), method: 'CASH' };

  constructor() {
    void this.loadUnits();
    void this.load();
  }

  canCreate(): boolean {
    return this.auth.hasPermission('PAYMENTS_CREATE', 'FINANCE_MANAGE');
  }

  canReverse(): boolean {
    return this.auth.hasPermission('FINANCE_MANAGE');
  }

  async loadUnits(): Promise<void> {
    try {
      const result = await firstValueFrom(this.api.get<PageResultPropertyUnit>('/properties', { size: 200 }));
      this.units.set(result.content ?? []);
    } catch {
      // El selector se reintenta al abrir el formulario.
    }
  }

  async load(page = 0): Promise<void> {
    this.loading.set(true);
    try {
      const result = await firstValueFrom(this.api.get<PageResultPayment>('/payments', {
        unitId: this.filterUnit,
        from: this.from,
        to: this.to,
        page,
        size: 20
      }));
      this.payments.set(result.content ?? []);
      this.page.set(result.page ?? 0);
      this.totalPages.set(result.totalPages ?? 0);
      this.totalElements.set(result.totalElements ?? 0);
    } finally {
      this.loading.set(false);
    }
  }

  clearFilters(): void {
    this.filterUnit = '';
    this.from = addDays(-30);
    this.to = today();
    void this.load(0);
  }

  openCreate(): void {
    this.form = { unitId: this.units()[0]?.id ?? '', amount: 0, paymentDate: today(), method: 'CASH' };
    this.createError.set('');
    this.createOpen.set(true);
  }

  async create(): Promise<void> {
    this.createError.set('');
    if (!this.form.unitId || !this.form.amount || !this.form.paymentDate) {
      this.createError.set('Completa inmueble, valor y fecha del pago.');
      return;
    }
    const accepted = await this.confirm.confirm({
      title: 'Registrar pago',
      message: `Se registrará un pago por ${new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(this.form.amount)}.`,
      confirmLabel: 'Registrar'
    });
    if (!accepted) return;
    this.saving.set(true);
    try {
      const key = crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`;
      const result = await firstValueFrom(this.api.post<PaymentResult>('/payments', this.form, {}, { 'Idempotency-Key': key }));
      this.toast.success(`Pago registrado${result.payment?.unappliedAmount ? ' con saldo sin aplicar' : ''}.`);
      this.createOpen.set(false);
      await this.load(0);
    } catch (error) {
      this.createError.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async openDetail(payment: Payment): Promise<void> {
    if (!payment.id) return;
    try {
      const result = await firstValueFrom(this.api.get<PaymentResult>(`/payments/${payment.id}`));
      this.detail.set(result);
      this.detailOpen.set(true);
    } catch {
      // El interceptor global informa el error.
    }
  }

  openReverse(payment: Payment): void {
    this.reversePayment = payment;
    this.reverseReason = '';
    this.reverseOpen.set(true);
  }

  async reverse(): Promise<void> {
    if (!this.reversePayment?.id || !this.reverseReason.trim()) {
      this.toast.warning('Ingresa el motivo del reverso.');
      return;
    }
    this.saving.set(true);
    try {
      await firstValueFrom(this.api.post<PaymentResult>(`/payments/${this.reversePayment.id}/reverse`, { reason: this.reverseReason.trim() }));
      this.toast.success('Pago reversado.');
      this.reverseOpen.set(false);
      await this.load(this.page());
    } finally {
      this.saving.set(false);
    }
  }
}
