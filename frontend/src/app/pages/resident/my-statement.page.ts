import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService, saveBlob } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { MoneyPipe } from '../../core/format.pipes';
import { label, statusTone } from '../../core/labels';
import { IntentResponse, MyUnit, Statement } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError, endOfMonth, startOfMonth } from '../../core/utils';
import { EmptyStateComponent, PageHeaderComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-my-statement-page',
  standalone: true,
  imports: [FormsModule, MoneyPipe, PageHeaderComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Estado de cuenta" subtitle="Consulta los movimientos, obligaciones y pagos de tus inmuebles.">
      @if (statement()) {
        <button type="button" class="btn btn-secondary" [disabled]="downloading()" (click)="download()">{{ downloading() ? 'Generando…' : 'Descargar PDF' }}</button>
      }
    </app-page-header>

    <div class="filter-bar">
      <div class="field">
        <label for="unit">Inmueble</label>
        <select id="unit" name="unit" class="select" [(ngModel)]="unitId" (change)="loadStatement()">
          @for (unit of units(); track unit.unitId) { <option [value]="unit.unitId">{{ unit.identifier }} · {{ unit.tower || 'Sin torre' }}</option> }
        </select>
      </div>
      <div class="field"><label for="from">Desde</label><input id="from" name="from" type="date" class="input" [(ngModel)]="from"></div>
      <div class="field"><label for="to">Hasta</label><input id="to" name="to" type="date" class="input" [(ngModel)]="to"></div>
      <button type="button" class="btn btn-secondary" [disabled]="loading() || !unitId" (click)="loadStatement()">Consultar</button>
    </div>

    @if (loading()) {
      <div class="loading"><span class="spinner"></span> Consultando el estado de cuenta…</div>
    } @else if (!units().length) {
      <div class="card"><app-empty-state symbol="⌂" title="Sin inmuebles asociados" message="Tu usuario no tiene inmuebles vinculados para consultar la cartera." /></div>
    } @else if (error()) {
      <div class="alert alert-error">{{ error() }}</div>
    } @else if (statement(); as data) {
      <div class="grid grid-4">
        <div class="stat accent"><div class="stat-label">Saldo pendiente</div><div class="stat-value">{{ data.closingBalance | money }}</div><div class="stat-hint">Al cierre del período</div></div>
        <div class="stat danger"><div class="stat-label">Valor vencido</div><div class="stat-value">{{ data.overdueAmount | money }}</div><div class="stat-hint">Obligaciones vencidas</div></div>
        <div class="stat"><div class="stat-label">Saldo a favor</div><div class="stat-value">{{ data.creditBalance | money }}</div><div class="stat-hint">Crédito disponible</div></div>
        <div class="stat"><div class="stat-label">Próximo vencimiento</div><div class="stat-value" style="font-size:20px">{{ data.nextDueDate || 'Sin pendientes' }}</div><div class="stat-hint">{{ data.unitIdentifier }}</div></div>
      </div>

      <div class="grid grid-2" style="margin-top:18px">
        <div class="card">
          <div class="card-header"><div><h2>Obligaciones abiertas</h2><p>Cuotas y cargos pendientes del inmueble.</p></div><span class="badge warning">{{ data.openCharges?.length ?? 0 }}</span></div>
          @if (!data.openCharges?.length) {
            <app-empty-state symbol="✓" title="Sin obligaciones abiertas" message="El inmueble no tiene cargos pendientes de pago." />
          } @else {
            <div class="table-wrap">
              <table class="table">
                <thead><tr><th>Concepto</th><th>Período</th><th>Vence</th><th class="num">Saldo</th><th>Estado</th></tr></thead>
                <tbody>
                  @for (charge of data.openCharges; track charge.id) {
                    <tr>
                      <td><div class="main-cell">{{ charge.description }}</div><div class="muted small">{{ label(charge.type) }}</div></td>
                      <td>{{ charge.period || '—' }}</td>
                      <td class="nowrap">{{ charge.dueDate || '—' }}</td>
                      <td class="num">{{ (charge.outstanding ?? charge.amount) | money }}</td>
                      <td><span class="badge" [class]="'badge ' + tone(charge.state)">{{ label(charge.state) }}</span></td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </div>

        <div class="card">
          <div class="card-header"><div><h2>Pago en línea</h2><p>Abona a las obligaciones del inmueble con la pasarela habilitada.</p></div></div>
          <div class="card-body">
            @if (!auth.hasPermission('PAYMENTS_ONLINE_CREATE')) {
              <div class="notice">Tu rol no tiene habilitado el pago en línea. Puedes realizar el pago directamente en la administración.</div>
            } @else if ((data.closingBalance ?? 0) <= 0) {
              <div class="notice">No tienes saldo pendiente. Si realizas un abono, quedará como saldo a favor.</div>
              <div class="field" style="margin-top:14px">
                <label for="amount">Valor del abono</label>
                <input id="amount" name="amount" type="number" min="1000" step="100" class="input" [(ngModel)]="amount">
              </div>
              <div class="form-actions">
                <button type="button" class="btn btn-primary" [disabled]="paying() || amount < 1000" (click)="payOnline()">{{ paying() ? 'Creando pago…' : 'Pagar en línea' }}</button>
              </div>
            } @else {
              <div class="row" style="align-items:flex-end">
                <div class="field" style="flex:1">
                  <label for="amount">Valor a pagar</label>
                  <input id="amount" name="amount" type="number" min="1000" step="100" class="input" [(ngModel)]="amount">
                  <span class="hint">Saldo pendiente: {{ data.closingBalance | money }}</span>
                </div>
                <button type="button" class="btn btn-secondary" (click)="amount = data.closingBalance ?? 0">Pagar todo</button>
              </div>
              <div class="field" style="margin-top:12px">
                <label for="notes">Nota (opcional)</label>
                <input id="notes" name="notes" class="input" maxlength="300" [(ngModel)]="notes" placeholder="Referencia o comentario del pago">
              </div>
              <div class="form-actions">
                <button type="button" class="btn btn-primary" [disabled]="paying() || amount < 1000" (click)="payOnline()">{{ paying() ? 'Creando pago…' : 'Ir a pagar' }}</button>
              </div>
              <div class="notice" style="margin-top:14px">Serás dirigido a la pasarela de pagos. Al finalizar podrás consultar el resultado en este portal.</div>
            }
            @if (paymentError()) { <div class="alert alert-error" style="margin-top:14px">{{ paymentError() }}</div> }
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-header"><div><h2>Movimientos del período</h2><p>{{ data.from || from }} a {{ data.to || to }} · {{ data.lines?.length ?? 0 }} movimiento(s).</p></div></div>
        @if (!data.lines?.length) {
          <app-empty-state symbol="≡" title="Sin movimientos" message="No se registraron movimientos en el período consultado." />
        } @else {
          <div class="table-wrap">
            <table class="table">
              <thead><tr><th>Fecha</th><th>Tipo</th><th>Concepto</th><th>Descripción</th><th class="num">Valor</th></tr></thead>
              <tbody>
                @for (line of data.lines; track line.id) {
                  <tr>
                    <td class="nowrap">{{ line.entryDate }}</td>
                    <td>{{ label(line.entryType) }}</td>
                    <td>{{ label(line.conceptType) }}</td>
                    <td>{{ line.description || line.reason || '—' }}</td>
                    <td class="num" [style.color]="(line.amount ?? 0) < 0 ? 'var(--success)' : 'var(--ink)'">{{ line.amount | money }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>
    } @else if (!loading()) {
      <div class="card"><app-empty-state symbol="≡" title="Selecciona un inmueble" message="Elige uno de tus inmuebles para consultar su estado de cuenta." /></div>
    }
  `
})
export class MyStatementPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly units = signal<MyUnit[]>([]);
  readonly statement = signal<Statement | null>(null);
  readonly loading = signal(false);
  readonly downloading = signal(false);
  readonly paying = signal(false);
  readonly error = signal('');
  readonly paymentError = signal('');
  unitId = '';
  from = startOfMonth();
  to = endOfMonth();
  amount = 0;
  notes = '';

  constructor() {
    void this.loadUnits();
  }

  async loadUnits(): Promise<void> {
    try {
      const units = await firstValueFrom(this.api.get<MyUnit[]>('/my/units'));
      this.units.set(units);
      const first = units[0]?.unitId ?? '';
      if (first) {
        this.unitId = first;
        await this.loadStatement();
      }
    } catch (error) {
      this.error.set(describeError(error));
    }
  }

  async loadStatement(): Promise<void> {
    if (!this.unitId) return;
    this.loading.set(true);
    this.error.set('');
    this.paymentError.set('');
    try {
      const result = await firstValueFrom(this.api.get<Statement>('/my/statement', { unitId: this.unitId, from: this.from, to: this.to }));
      this.statement.set(result);
      this.amount = Math.max(0, Number(result.closingBalance ?? 0));
    } catch (error) {
      this.statement.set(null);
      this.error.set(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  async download(): Promise<void> {
    if (!this.unitId) return;
    this.downloading.set(true);
    try {
      const response = await firstValueFrom(this.api.download('/my/certificates/statement', { unitId: this.unitId, from: this.from, to: this.to }, 'estado-cuenta.pdf'));
      saveBlob(response, 'estado-cuenta.pdf');
      this.toast.success('Estado de cuenta descargado.');
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.downloading.set(false);
    }
  }

  async payOnline(): Promise<void> {
    if (!this.unitId || this.amount < 1000) {
      this.paymentError.set('El valor mínimo del pago en línea es de $1.000.');
      return;
    }
    this.paying.set(true);
    this.paymentError.set('');
    try {
      const result = await firstValueFrom(this.api.post<IntentResponse>('/my/payments/online', {
        unitId: this.unitId,
        amount: Number(this.amount),
        notes: this.notes.trim() || undefined
      }));
      if (!result.checkoutUrl) {
        this.paymentError.set('La pasarela no devolvió una dirección de pago. Intenta nuevamente o contacta a la administración.');
        return;
      }
      if (result.reference) sessionStorage.setItem('vecindad.wompi.reference', result.reference);
      window.location.assign(result.checkoutUrl);
    } catch (error) {
      this.paymentError.set(describeError(error));
    } finally {
      this.paying.set(false);
    }
  }
}
