import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ConfirmService } from '../../core/confirm.service';
import { MoneyPipe } from '../../core/format.pipes';
import { label, statusTone } from '../../core/labels';
import {
  AdjustmentRequest,
  BillingSettings,
  Charge,
  ChargeRequest,
  InterestRunSummary,
  LedgerEntry,
  PageResultCharge,
  PageResultLedgerEntry,
  PageResultPropertyUnit,
  PortfolioRow,
  PortfolioView,
  PropertyUnit,
  RunSummary,
  Statement
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { addDays, describeError, endOfMonth, startOfMonth, today } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent, PaginationComponent } from '../../shared/ui.components';

type FinanceTab = 'portfolio' | 'charges' | 'statement' | 'settings';
type ChargeType = 'ORDINARY' | 'EXTRAORDINARY' | 'INTEREST' | 'FINE' | 'OTHER';

@Component({
  selector: 'app-finance-page',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, MoneyPipe, PageHeaderComponent, ModalComponent, PaginationComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Cartera" subtitle="Cuotas, cartera vencida, estado de cuenta y configuración de cobro.">
      @if (auth.hasPermission('FINANCE_MANAGE')) { <button type="button" class="btn btn-primary" (click)="openCharge()">Nuevo cargo</button> }
      @if (auth.hasPermission('PAYMENTS_CREATE')) { <a class="btn btn-secondary" routerLink="/app/pagos">Registrar pago</a> }
    </app-page-header>

    <div class="tabs">
      <button type="button" class="tab" [class.active]="tab() === 'portfolio'" (click)="setTab('portfolio')">Cartera</button>
      <button type="button" class="tab" [class.active]="tab() === 'charges'" (click)="setTab('charges')">Cargos</button>
      <button type="button" class="tab" [class.active]="tab() === 'statement'" (click)="setTab('statement')">Estado de cuenta</button>
      @if (auth.hasPermission('FINANCE_MANAGE')) { <button type="button" class="tab" [class.active]="tab() === 'settings'" (click)="setTab('settings')">Configuración de cobro</button> }
    </div>

    @if (tab() === 'portfolio') {
      <div class="grid grid-4">
        <div class="stat accent"><div class="stat-label">Cartera pendiente</div><div class="stat-value">{{ portfolio()?.totalOutstanding | money }}</div></div>
        <div class="stat danger"><div class="stat-label">Cartera vencida</div><div class="stat-value">{{ portfolio()?.totalOverdue | money }}</div></div>
        <div class="stat"><div class="stat-label">Saldo a favor</div><div class="stat-value">{{ portfolio()?.totalCredit | money }}</div></div>
        <div class="stat"><div class="stat-label">Inmuebles con saldo</div><div class="stat-value">{{ portfolio()?.units?.totalElements ?? 0 }}</div></div>
      </div>
      <div class="card" style="margin-top:18px">
        <div class="card-header">
          <div><h2>Cartera por inmueble</h2><p>Saldos pendientes y vencidos consolidados.</p></div>
          <div class="row">
            <label class="check"><input type="checkbox" [(ngModel)]="overdueOnly" (change)="loadPortfolio(0)"> Solo vencidos</label>
            <button type="button" class="btn btn-secondary btn-sm" (click)="loadPortfolio(portfolioPage())">Actualizar</button>
          </div>
        </div>
        @if (loadingPortfolio()) {
          <div class="loading"><span class="spinner"></span> Calculando cartera…</div>
        } @else if (!portfolioRows().length) {
          <app-empty-state symbol="✓" title="Cartera al día" message="No hay inmuebles con saldos pendientes para el filtro seleccionado." />
        } @else {
          <div class="table-wrap">
            <table class="table">
              <thead><tr><th>Inmueble</th><th class="num">Pendiente</th><th class="num">Vencido</th><th class="num">A favor</th><th>Vencimiento más antiguo</th><th></th></tr></thead>
              <tbody>
                @for (row of portfolioRows(); track row.unitId) {
                  <tr>
                    <td class="main-cell">{{ row.unitIdentifier }}</td>
                    <td class="num">{{ row.outstanding | money }}</td>
                    <td class="num" [style.color]="(row.overdue || 0) > 0 ? '#b91c1c' : null">{{ row.overdue | money }}</td>
                    <td class="num">{{ row.creditBalance | money }}</td>
                    <td>{{ row.oldestDueDate | date:'dd/MM/yyyy' }}</td>
                    <td><button type="button" class="btn btn-secondary btn-sm" (click)="openStatement(row.unitId)">Estado de cuenta</button></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <app-pagination [page]="portfolioPage()" [totalPages]="portfolio()?.units?.totalPages ?? 0" [totalElements]="portfolio()?.units?.totalElements ?? 0" (pageChange)="loadPortfolio($event)" />
        }
      </div>
    }

    @if (tab() === 'charges') {
      <div class="filter-bar">
        <div class="field">
          <label for="chargeUnit">Inmueble</label>
          <select id="chargeUnit" name="chargeUnit" class="select" [(ngModel)]="chargeUnitId" (change)="loadCharges(0)">
            <option value="">Todos</option>
            @for (unit of units(); track unit.id) { <option [value]="unit.id">{{ unit.identifier }}</option> }
          </select>
        </div>
        <div class="field">
          <label for="chargeType">Tipo</label>
          <select id="chargeType" name="chargeType" class="select" [(ngModel)]="chargeType" (change)="loadCharges(0)">
            <option value="">Todos</option>
            @for (option of chargeTypes; track option) { <option [value]="option">{{ label(option) }}</option> }
          </select>
        </div>
        <label class="check" style="margin-bottom:9px"><input type="checkbox" [(ngModel)]="onlyPending" (change)="loadCharges(0)"> Solo pendientes</label>
        <button type="button" class="btn btn-secondary" (click)="loadCharges(0)">Filtrar</button>
      </div>
      <div class="card">
        <div class="card-header"><div><h2>Cargos</h2><p>{{ chargesTotal() }} cargo(s) registrados.</p></div></div>
        @if (loadingCharges()) {
          <div class="loading"><span class="spinner"></span> Cargando cargos…</div>
        } @else if (!charges().length) {
          <app-empty-state symbol="≡" title="Sin cargos" message="No hay cargos que coincidan con los filtros." />
        } @else {
          <div class="table-wrap">
            <table class="table">
              <thead><tr><th>Inmueble</th><th>Tipo</th><th>Descripción</th><th>Vence</th><th class="num">Valor</th><th class="num">Pagado</th><th class="num">Pendiente</th><th>Estado</th><th style="width:80px"></th></tr></thead>
              <tbody>
                @for (charge of charges(); track charge.id) {
                  <tr>
                    <td class="main-cell">{{ charge.unitIdentifier }}</td>
                    <td>{{ label(charge.type) }}</td>
                    <td>{{ charge.description }}</td>
                    <td>{{ charge.dueDate | date:'dd/MM/yyyy' }}</td>
                    <td class="num">{{ charge.amount | money }}</td>
                    <td class="num">{{ charge.paidAmount | money }}</td>
                    <td class="num">{{ charge.outstanding | money }}</td>
                    <td><span class="badge" [class]="'badge ' + tone(charge.state)">{{ label(charge.state) }}</span></td>
                    <td>
                      @if (auth.hasPermission('FINANCE_MANAGE') && !charge.voidedAt) {
                        <div class="table-actions">
                          <button type="button" class="btn-icon" title="Ajustar" (click)="openAdjustment(charge)">±</button>
                          <button type="button" class="btn-icon danger" title="Anular" (click)="openVoid(charge)">⊘</button>
                        </div>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <app-pagination [page]="chargesPage()" [totalPages]="chargesPages()" [totalElements]="chargesTotal()" (pageChange)="loadCharges($event)" />
        }
      </div>
    }

    @if (tab() === 'statement') {
      <div class="card">
        <div class="card-header">
          <div><h2>Estado de cuenta</h2><p>Movimientos, cuotas pendientes y saldo del inmueble.</p></div>
          <div class="row">
            <div class="field">
              <label for="statementUnit">Inmueble</label>
              <select id="statementUnit" name="statementUnit" class="select" [(ngModel)]="statementUnitId" (change)="loadStatement()">
                <option value="">Selecciona</option>
                @for (unit of units(); track unit.id) { <option [value]="unit.id">{{ unit.identifier }}</option> }
              </select>
            </div>
            <div class="field"><label for="from">Desde</label><input id="from" name="from" type="date" class="input" [(ngModel)]="statementFrom" (change)="loadStatement()"></div>
            <div class="field"><label for="to">Hasta</label><input id="to" name="to" type="date" class="input" [(ngModel)]="statementTo" (change)="loadStatement()"></div>
          </div>
        </div>
        @if (!statementUnitId) {
          <app-empty-state symbol="⌕" title="Selecciona un inmueble" message="Elige un inmueble para consultar su estado de cuenta y movimientos." />
        } @else if (loadingStatement()) {
          <div class="loading"><span class="spinner"></span> Generando estado de cuenta…</div>
        } @else if (statement()) {
          <div class="card-body">
            <div class="grid grid-4">
              <div class="stat"><div class="stat-label">Saldo inicial</div><div class="stat-value">{{ statement()!.openingBalance | money }}</div></div>
              <div class="stat danger"><div class="stat-label">Saldo final</div><div class="stat-value">{{ statement()!.closingBalance | money }}</div></div>
              <div class="stat warn"><div class="stat-label">Vencido</div><div class="stat-value">{{ statement()!.overdueAmount | money }}</div></div>
              <div class="stat"><div class="stat-label">A favor</div><div class="stat-value">{{ statement()!.creditBalance | money }}</div></div>
            </div>
            <div class="divider"></div>
            <h3 class="section-title">Movimientos</h3>
            <div class="table-wrap">
              <table class="table">
                <thead><tr><th>Fecha</th><th>Tipo</th><th>Concepto</th><th>Descripción</th><th class="num">Valor</th></tr></thead>
                <tbody>
                  @for (line of statement()!.lines; track line.id) {
                    <tr>
                      <td>{{ line.entryDate | date:'dd/MM/yyyy' }}</td>
                      <td>{{ label(line.entryType) }}</td>
                      <td>{{ label(line.conceptType) }}</td>
                      <td>{{ line.description || line.reason || '—' }}</td>
                      <td class="num">{{ line.amount | money }}</td>
                    </tr>
                  } @empty { <tr><td colspan="5" class="table-empty">Sin movimientos en el periodo.</td></tr> }
                </tbody>
              </table>
            </div>
            @if (statement()!.openCharges?.length) {
              <h3 class="section-title" style="margin-top:20px">Cuotas pendientes</h3>
              <div class="table-wrap">
                <table class="table">
                  <thead><tr><th>Concepto</th><th>Vence</th><th class="num">Valor</th><th class="num">Pagado</th><th class="num">Pendiente</th></tr></thead>
                  <tbody>
                    @for (charge of statement()!.openCharges; track charge.id) {
                      <tr><td>{{ charge.description }}</td><td>{{ charge.dueDate | date:'dd/MM/yyyy' }}</td><td class="num">{{ charge.amount | money }}</td><td class="num">{{ charge.paidAmount | money }}</td><td class="num">{{ charge.outstanding | money }}</td></tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </div>
        }
      </div>
    }

    @if (tab() === 'settings') {
      <div class="grid grid-2">
        <div class="card">
          <div class="card-header"><div><h2>Configuración de cartera</h2><p>Reglas de imputación, intereses y bloqueo de reservas.</p></div></div>
          <div class="card-body">
            @if (settingsError()) { <div class="alert alert-error" style="margin-bottom:14px">{{ settingsError() }}</div> }
            @if (loadingSettings()) {
              <div class="loading"><span class="spinner"></span> Cargando configuración…</div>
            } @else {
              <div class="field">
                <label>Orden de imputación de pagos</label>
                <div class="stack" style="gap:7px">
                  @for (type of settingsOrder; track type; let i = $index) {
                    <div class="row between" style="border:1px solid var(--line);border-radius:8px;padding:6px 10px">
                      <span style="font-size:12.5px">{{ i + 1 }}. {{ label(type) }}</span>
                      <span class="row" style="gap:2px">
                        <button type="button" class="btn-icon" [disabled]="i === 0" (click)="moveOrder(i, -1)">↑</button>
                        <button type="button" class="btn-icon" [disabled]="i === settingsOrder.length - 1" (click)="moveOrder(i, 1)">↓</button>
                      </span>
                    </div>
                  }
                </div>
              </div>
              <div class="divider"></div>
              <div class="form-grid">
                <div class="field"><label class="check"><input type="checkbox" [(ngModel)]="settingsForm.interestEnabled"> Cobrar intereses de mora</label></div>
                <div class="field"><label class="check"><input type="checkbox" [(ngModel)]="settingsForm.oldestFirst"> Imputar primero lo más antiguo</label></div>
                <div class="field"><label for="rate">Tasa mensual de interés</label><input id="rate" name="rate" type="number" step="0.0001" class="input" [(ngModel)]="settingsForm.interestMonthlyRate"><span class="hint">Ej. 0.015 equivale a 1,5 % mensual.</span></div>
                <div class="field"><label for="grace">Días de gracia</label><input id="grace" name="grace" type="number" class="input" [(ngModel)]="settingsForm.graceDays"></div>
                <div class="field"><label class="check"><input type="checkbox" [(ngModel)]="settingsForm.blockReservationsWhenOverdue"> Bloquear reservas con cartera vencida</label></div>
                <div class="field"><label for="overdueDays">Días de mora para bloqueo</label><input id="overdueDays" name="overdueDays" type="number" class="input" [(ngModel)]="settingsForm.overdueDaysForBlock"></div>
              </div>
              <div class="form-actions">
                <button type="button" class="btn btn-primary" [disabled]="savingSettings()" (click)="saveSettings()">Guardar configuración</button>
              </div>
            }
          </div>
        </div>
        <div class="card">
          <div class="card-header"><div><h2>Procesos de cartera</h2><p>Generación de cuotas ordinarias e intereses.</p></div></div>
          <div class="card-body">
            <h3 class="section-title">Cuotas ordinarias</h3>
            <p class="muted small" style="margin-bottom:12px">Genera la cuota del periodo para todos los inmuebles con coeficiente. Puedes usar el presupuesto total o un valor fijo por inmueble.</p>
            <div class="form-grid">
              <div class="field"><label for="period">Periodo</label><input id="period" name="period" type="month" class="input" [(ngModel)]="runForm.period"></div>
              <div class="field"><label for="dueDate">Vencimiento</label><input id="dueDate" name="dueDate" type="date" class="input" [(ngModel)]="runForm.dueDate"></div>
              <div class="field"><label for="totalBudget">Presupuesto total</label><input id="totalBudget" name="totalBudget" type="number" class="input" [(ngModel)]="runForm.totalBudget"></div>
              <div class="field"><label for="fixedAmount">Valor fijo por inmueble</label><input id="fixedAmount" name="fixedAmount" type="number" class="input" [(ngModel)]="runForm.fixedAmount"></div>
            </div>
            <div class="form-actions"><button type="button" class="btn btn-primary" [disabled]="running()" (click)="runOrdinary()">Generar cuotas</button></div>
            @if (lastRun()) { <div class="alert alert-success" style="margin-top:12px">Se generaron {{ lastRun()!.created }} cuotas por {{ lastRun()!.totalAmount | money }}. Omitidas: {{ lastRun()!.skippedExisting }} existentes, {{ lastRun()!.skippedNoCoefficient }} sin coeficiente.</div> }
            <div class="divider"></div>
            <h3 class="section-title">Intereses de mora</h3>
            <p class="muted small" style="margin-bottom:12px">Calcula y carga intereses a los inmuebles con obligaciones vencidas a la fecha indicada.</p>
            <div class="form-grid">
              <div class="field"><label for="asOf">Corte</label><input id="asOf" name="asOf" type="date" class="input" [(ngModel)]="interestForm.asOf"></div>
            </div>
            <div class="form-actions"><button type="button" class="btn btn-secondary" [disabled]="runningInterest()" (click)="runInterest()">Generar intereses</button></div>
            @if (lastInterest()) { <div class="alert alert-success" style="margin-top:12px">Se cargaron intereses a {{ lastInterest()!.unitsCharged }} inmuebles por {{ lastInterest()!.totalInterest | money }}.</div> }
          </div>
        </div>
      </div>
    }

    <app-modal [open]="chargeOpen()" title="Nuevo cargo" subtitle="Registra una cuota extraordinaria, multa u otro concepto." (closed)="chargeOpen.set(false)">
      @if (chargeError()) { <div class="alert alert-error" style="margin-bottom:14px">{{ chargeError() }}</div> }
      <form (ngSubmit)="createCharge()" novalidate>
        <div class="form-grid">
          <div class="field">
            <label for="newUnit">Inmueble *</label>
            <select id="newUnit" name="newUnit" class="select" required [(ngModel)]="chargeForm.unitId">
              <option value="">Selecciona</option>
              @for (unit of units(); track unit.id) { <option [value]="unit.id">{{ unit.identifier }}</option> }
            </select>
          </div>
          <div class="field">
            <label for="newType">Tipo *</label>
            <select id="newType" name="newType" class="select" [(ngModel)]="chargeForm.type">
              @for (option of chargeTypes; track option) { <option [value]="option">{{ label(option) }}</option> }
            </select>
          </div>
          <div class="field span-2"><label for="desc">Descripción *</label><input id="desc" name="desc" class="input" required [(ngModel)]="chargeForm.description"></div>
          <div class="field"><label for="issue">Fecha de emisión</label><input id="issue" name="issue" type="date" class="input" [(ngModel)]="chargeForm.issueDate"></div>
          <div class="field"><label for="due">Fecha de vencimiento *</label><input id="due" name="due" type="date" class="input" required [(ngModel)]="chargeForm.dueDate"></div>
          <div class="field"><label for="period">Periodo</label><input id="period" name="period" type="month" class="input" [(ngModel)]="chargeForm.period"></div>
          <div class="field"><label for="amount">Valor *</label><input id="amount" name="amount" type="number" min="0.01" step="0.01" class="input" required [(ngModel)]="chargeForm.amount"></div>
        </div>
        <div class="form-actions">
          <button type="button" class="btn btn-secondary" (click)="chargeOpen.set(false)">Cancelar</button>
          <button type="submit" class="btn btn-primary" [disabled]="chargeSaving()">Crear cargo</button>
        </div>
      </form>
    </app-modal>

    <app-modal [open]="voidOpen()" title="Anular cargo" subtitle="La anulación conserva el historial y requiere un motivo." [narrow]="true" (closed)="voidOpen.set(false)">
      <div class="field"><label for="voidReason">Motivo *</label><textarea id="voidReason" name="voidReason" class="textarea" [(ngModel)]="voidReason"></textarea></div>
      <div class="form-actions">
        <button type="button" class="btn btn-secondary" (click)="voidOpen.set(false)">Cancelar</button>
        <button type="button" class="btn btn-danger" [disabled]="actionSaving()" (click)="confirmVoid()">Anular cargo</button>
      </div>
    </app-modal>

    <app-modal [open]="adjustOpen()" title="Ajustar cargo" subtitle="Aplica un crédito o débito justificado sobre el cargo." [narrow]="true" (closed)="adjustOpen.set(false)">
      <div class="form-grid">
        <div class="field">
          <label for="kind">Tipo</label>
          <select id="kind" name="kind" class="select" [(ngModel)]="adjustForm.kind"><option value="CREDIT">Crédito</option><option value="DEBIT">Débito</option></select>
        </div>
        <div class="field"><label for="adjustAmount">Valor</label><input id="adjustAmount" name="adjustAmount" type="number" min="0.01" step="0.01" class="input" [(ngModel)]="adjustForm.amount"></div>
        <div class="field span-2"><label for="adjustReason">Motivo</label><textarea id="adjustReason" name="adjustReason" class="textarea" [(ngModel)]="adjustForm.reason"></textarea></div>
      </div>
      <div class="form-actions">
        <button type="button" class="btn btn-secondary" (click)="adjustOpen.set(false)">Cancelar</button>
        <button type="button" class="btn btn-primary" [disabled]="actionSaving()" (click)="confirmAdjustment()">Aplicar ajuste</button>
      </div>
    </app-modal>
  `
})
export class FinancePage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly chargeTypes: ChargeType[] = ['ORDINARY', 'EXTRAORDINARY', 'INTEREST', 'FINE', 'OTHER'];
  readonly tab = signal<FinanceTab>('portfolio');
  readonly portfolio = signal<PortfolioView | null>(null);
  readonly loadingPortfolio = signal(false);
  readonly portfolioPage = signal(0);
  readonly units = signal<PropertyUnit[]>([]);
  readonly charges = signal<Charge[]>([]);
  readonly chargesPage = signal(0);
  readonly chargesPages = signal(0);
  readonly chargesTotal = signal(0);
  readonly loadingCharges = signal(false);
  readonly statement = signal<Statement | null>(null);
  readonly loadingStatement = signal(false);
  readonly loadingSettings = signal(false);
  readonly savingSettings = signal(false);
  readonly settingsError = signal('');
  readonly chargeOpen = signal(false);
  readonly chargeSaving = signal(false);
  readonly chargeError = signal('');
  readonly voidOpen = signal(false);
  readonly adjustOpen = signal(false);
  readonly actionSaving = signal(false);
  readonly running = signal(false);
  readonly runningInterest = signal(false);
  readonly lastRun = signal<RunSummary | null>(null);
  readonly lastInterest = signal<InterestRunSummary | null>(null);
  overdueOnly = false;
  chargeUnitId = '';
  chargeType = '';
  onlyPending = false;
  statementUnitId = '';
  statementFrom = startOfMonth();
  statementTo = endOfMonth();
  voidReason = '';
  voidCharge: Charge | null = null;
  adjustCharge: Charge | null = null;
  adjustForm: AdjustmentRequest = { kind: 'CREDIT', amount: 0, reason: '' };
  settingsOrder: ChargeType[] = ['ORDINARY', 'EXTRAORDINARY', 'INTEREST', 'FINE', 'OTHER'];
  settingsForm = {
    oldestFirst: true,
    interestEnabled: false,
    interestMonthlyRate: 0,
    graceDays: 0,
    blockReservationsWhenOverdue: false,
    overdueDaysForBlock: 0
  };
  chargeForm: ChargeRequest = {
    unitId: '',
    type: 'EXTRAORDINARY',
    description: '',
    issueDate: today(),
    dueDate: addDays(15),
    amount: 0
  };
  runForm = { period: startOfMonth().slice(0, 7), dueDate: addDays(10), totalBudget: undefined as number | undefined, fixedAmount: undefined as number | undefined };
  interestForm = { asOf: today() };

  constructor() {
    void this.loadUnits();
    void this.loadPortfolio(0);
  }

  setTab(tab: FinanceTab): void {
    this.tab.set(tab);
    if (tab === 'portfolio' && !this.portfolio()) void this.loadPortfolio(0);
    if (tab === 'charges' && !this.charges().length) void this.loadCharges(0);
    if (tab === 'settings' && !this.settingsLoaded) void this.loadSettings();
  }

  private settingsLoaded = false;

  async loadUnits(): Promise<void> {
    try {
      const result = await firstValueFrom(this.api.get<PageResultPropertyUnit>('/properties', { size: 200 }));
      this.units.set(result.content ?? []);
      if (!this.statementUnitId && result.content?.length) {
        this.statementUnitId = result.content[0].id ?? '';
      }
    } catch {
      // Los selectores se reintentan al abrir el formulario.
    }
  }

  async loadPortfolio(page = 0): Promise<void> {
    this.loadingPortfolio.set(true);
    try {
      const result = await firstValueFrom(this.api.get<PortfolioView>('/billing/portfolio', {
        overdueOnly: this.overdueOnly,
        page,
        size: 20
      }));
      this.portfolio.set(result);
      this.portfolioPage.set(result.units?.page ?? 0);
    } finally {
      this.loadingPortfolio.set(false);
    }
  }

  portfolioRows(): PortfolioRow[] {
    return this.portfolio()?.units?.content ?? [];
  }

  async loadCharges(page = 0): Promise<void> {
    this.loadingCharges.set(true);
    try {
      const result = await firstValueFrom(this.api.get<PageResultCharge>('/billing/charges', {
        unitId: this.chargeUnitId,
        pending: this.onlyPending,
        type: this.chargeType,
        page,
        size: 20
      }));
      this.charges.set(result.content ?? []);
      this.chargesPage.set(result.page ?? 0);
      this.chargesPages.set(result.totalPages ?? 0);
      this.chargesTotal.set(result.totalElements ?? 0);
    } finally {
      this.loadingCharges.set(false);
    }
  }

  openStatement(unitId: string | undefined): void {
    if (!unitId) return;
    this.statementUnitId = unitId;
    this.tab.set('statement');
    void this.loadStatement();
  }

  async loadStatement(): Promise<void> {
    if (!this.statementUnitId) {
      this.statement.set(null);
      return;
    }
    this.loadingStatement.set(true);
    try {
      const result = await firstValueFrom(this.api.get<Statement>(`/billing/units/${this.statementUnitId}/statement`, {
        from: this.statementFrom,
        to: this.statementTo
      }));
      this.statement.set(result);
    } finally {
      this.loadingStatement.set(false);
    }
  }

  async loadSettings(): Promise<void> {
    this.settingsLoaded = true;
    this.loadingSettings.set(true);
    this.settingsError.set('');
    try {
      const settings = await firstValueFrom(this.api.get<BillingSettings>('/billing/settings'));
      if (settings.allocationOrder?.length) this.settingsOrder = [...settings.allocationOrder];
      this.settingsForm = {
        oldestFirst: settings.oldestFirst ?? true,
        interestEnabled: settings.interestEnabled ?? false,
        interestMonthlyRate: settings.interestMonthlyRate ?? 0,
        graceDays: settings.graceDays ?? 0,
        blockReservationsWhenOverdue: settings.blockReservationsWhenOverdue ?? false,
        overdueDaysForBlock: settings.overdueDaysForBlock ?? 0
      };
    } catch {
      // Si aún no existe configuración, se muestran los valores iniciales editables.
    } finally {
      this.loadingSettings.set(false);
    }
  }

  moveOrder(index: number, delta: number): void {
    const target = index + delta;
    if (target < 0 || target >= this.settingsOrder.length) return;
    const copy = [...this.settingsOrder];
    [copy[index], copy[target]] = [copy[target], copy[index]];
    this.settingsOrder = copy;
  }

  async saveSettings(): Promise<void> {
    this.settingsError.set('');
    this.savingSettings.set(true);
    try {
      await firstValueFrom(this.api.put<BillingSettings>('/billing/settings', {
        ...this.settingsForm,
        allocationOrder: this.settingsOrder
      }));
      this.toast.success('Configuración de cartera guardada.');
    } catch (error) {
      this.settingsError.set(describeError(error));
    } finally {
      this.savingSettings.set(false);
    }
  }

  openCharge(): void {
    this.chargeForm = {
      unitId: this.units()[0]?.id ?? '',
      type: 'EXTRAORDINARY',
      description: '',
      issueDate: today(),
      dueDate: addDays(15),
      period: undefined,
      amount: 0
    };
    this.chargeError.set('');
    this.chargeOpen.set(true);
  }

  async createCharge(): Promise<void> {
    this.chargeError.set('');
    if (!this.chargeForm.unitId || !this.chargeForm.description.trim() || !this.chargeForm.dueDate || !this.chargeForm.amount) {
      this.chargeError.set('Completa inmueble, descripción, vencimiento y valor.');
      return;
    }
    this.chargeSaving.set(true);
    try {
      await firstValueFrom(this.api.post<Charge>('/billing/charges', {
        ...this.chargeForm,
        period: this.chargeForm.period ? `${this.chargeForm.period}-01` : undefined
      }));
      this.toast.success('Cargo creado.');
      this.chargeOpen.set(false);
      await this.loadCharges(0);
      await this.loadPortfolio(0);
    } catch (error) {
      this.chargeError.set(describeError(error));
    } finally {
      this.chargeSaving.set(false);
    }
  }

  openVoid(charge: Charge): void {
    this.voidCharge = charge;
    this.voidReason = '';
    this.voidOpen.set(true);
  }

  async confirmVoid(): Promise<void> {
    if (!this.voidCharge?.id || !this.voidReason.trim()) {
      this.toast.warning('Ingresa el motivo de la anulación.');
      return;
    }
    this.actionSaving.set(true);
    try {
      await firstValueFrom(this.api.post<Charge>(`/billing/charges/${this.voidCharge.id}/void`, { reason: this.voidReason.trim() }));
      this.toast.success('Cargo anulado.');
      this.voidOpen.set(false);
      await this.loadCharges(this.chargesPage());
      await this.loadPortfolio(0);
    } finally {
      this.actionSaving.set(false);
    }
  }

  openAdjustment(charge: Charge): void {
    this.adjustCharge = charge;
    this.adjustForm = { kind: 'CREDIT', amount: 0, reason: '' };
    this.adjustOpen.set(true);
  }

  async confirmAdjustment(): Promise<void> {
    if (!this.adjustCharge?.id || !this.adjustForm.amount || !this.adjustForm.reason.trim()) {
      this.toast.warning('Completa el valor y el motivo del ajuste.');
      return;
    }
    this.actionSaving.set(true);
    try {
      await firstValueFrom(this.api.post<Charge>(`/billing/charges/${this.adjustCharge.id}/adjustments`, this.adjustForm));
      this.toast.success('Ajuste aplicado.');
      this.adjustOpen.set(false);
      await this.loadCharges(this.chargesPage());
      await this.loadPortfolio(0);
    } finally {
      this.actionSaving.set(false);
    }
  }

  async runOrdinary(): Promise<void> {
    if (!this.runForm.period || !this.runForm.dueDate) {
      this.toast.warning('Indica el periodo y la fecha de vencimiento.');
      return;
    }
    const accepted = await this.confirm.confirm({
      title: 'Generar cuotas ordinarias',
      message: `Se generarán cuotas para el periodo ${this.runForm.period}. Los inmuebles con cuota existente se omitirán.`,
      confirmLabel: 'Generar'
    });
    if (!accepted) return;
    this.running.set(true);
    try {
      const result = await firstValueFrom(this.api.post<RunSummary>('/billing/runs/ordinary', {
        period: `${this.runForm.period}-01`,
        dueDate: this.runForm.dueDate,
        totalBudget: this.runForm.totalBudget || undefined,
        fixedAmount: this.runForm.fixedAmount || undefined
      }));
      this.lastRun.set(result);
      this.toast.success('Proceso de cuotas ejecutado.');
      await this.loadPortfolio(0);
    } finally {
      this.running.set(false);
    }
  }

  async runInterest(): Promise<void> {
    const accepted = await this.confirm.confirm({
      title: 'Generar intereses',
      message: `Se calcularán intereses de mora con corte ${this.interestForm.asOf}.`,
      confirmLabel: 'Generar'
    });
    if (!accepted) return;
    this.runningInterest.set(true);
    try {
      const result = await firstValueFrom(this.api.post<InterestRunSummary>('/billing/runs/interest', { asOf: this.interestForm.asOf }));
      this.lastInterest.set(result);
      this.toast.success('Intereses generados.');
      await this.loadPortfolio(0);
    } finally {
      this.runningInterest.set(false);
    }
  }
}
