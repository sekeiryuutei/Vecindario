import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { MoneyPipe } from '../../core/format.pipes';
import { label, statusTone } from '../../core/labels';
import {
  AnnualBudget,
  BudgetExecution,
  BudgetItem,
  BudgetSummary,
  CreateBudgetRequest,
  CreateItemRequest,
  ExecuteBudgetRequest
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { currentYear, describeError, today } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent } from '../../shared/ui.components';

type BudgetType = 'INCOME' | 'EXPENSE';

@Component({
  selector: 'app-budget-page',
  standalone: true,
  imports: [FormsModule, DatePipe, MoneyPipe, PageHeaderComponent, ModalComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Presupuesto" subtitle="Planea ingresos y gastos del año, controla la ejecución por rubro y consulta el balance.">
      @if (canManage()) { <button type="button" class="btn btn-primary" (click)="openBudget()">Nuevo presupuesto</button> }
    </app-page-header>

    <div class="grid grid-4">
      <div class="stat accent">
        <div class="stat-label">Ingresos presupuestados</div>
        <div class="stat-value">{{ summary()?.totalIncomeBudgeted | money }}</div>
        <div class="stat-hint">Ejecutado: {{ summary()?.totalIncomeExecuted | money }}</div>
      </div>
      <div class="stat">
        <div class="stat-label">Gastos presupuestados</div>
        <div class="stat-value">{{ summary()?.totalExpenseBudgeted | money }}</div>
        <div class="stat-hint">Ejecutado: {{ summary()?.totalExpenseExecuted | money }}</div>
      </div>
      <div class="stat warn">
        <div class="stat-label">Neto presupuestado</div>
        <div class="stat-value">{{ summary()?.netBudgeted | money }}</div>
        <div class="stat-hint">Diferencia entre ingresos y gastos</div>
      </div>
      <div class="stat" [class.danger]="(summary()?.netExecuted ?? 0) < 0">
        <div class="stat-label">Neto ejecutado</div>
        <div class="stat-value">{{ summary()?.netExecuted | money }}</div>
        <div class="stat-hint">Año {{ summary()?.budget?.year || year }}</div>
      </div>
    </div>

    <div class="card" style="margin-top:18px">
      <div class="card-header">
        <div><h2>Presupuestos anuales</h2><p>Selecciona un presupuesto para revisar sus rubros y ejecuciones.</p></div>
        <div class="row">
          <div class="field" style="min-width:110px">
            <label for="summaryYear">Año del resumen</label>
            <input id="summaryYear" name="summaryYear" type="number" class="input" [(ngModel)]="year" (change)="loadSummary()">
          </div>
          <button type="button" class="btn btn-secondary btn-sm" (click)="load()">Actualizar</button>
        </div>
      </div>
      @if (loading()) {
        <div class="loading"><span class="spinner"></span> Cargando presupuestos…</div>
      } @else if (!budgets().length) {
        <app-empty-state symbol="◫" title="Sin presupuestos" message="Crea el primer presupuesto anual para controlar ingresos y gastos." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Año</th><th>Título</th><th>Estado</th><th class="num">Ingresos</th><th class="num">Gastos</th><th class="num">Neto</th><th style="width:110px"></th></tr></thead>
            <tbody>
              @for (budget of budgets(); track budget.id) {
                <tr>
                  <td class="main-cell">{{ budget.year }}</td>
                  <td>{{ budget.title }}</td>
                  <td><span class="badge" [class]="'badge ' + tone(budget.status)">{{ label(budget.status) }}</span></td>
                  <td class="num">{{ budget.totalIncomeBudgeted | money }}</td>
                  <td class="num">{{ budget.totalExpenseBudgeted | money }}</td>
                  <td class="num">{{ (budget.totalIncomeBudgeted || 0) - (budget.totalExpenseBudgeted || 0) | money }}</td>
                  <td><button type="button" class="btn btn-secondary btn-sm" (click)="selectBudget(budget)">Abrir</button></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>

    @if (selected(); as budget) {
      <div class="card">
        <div class="card-header">
          <div><h2>{{ budget.title }} · {{ budget.year }}</h2><p>Rubros presupuestales y ejecución registrada.</p></div>
          <div class="row">
            <span class="badge" [class]="'badge ' + tone(budget.status)">{{ label(budget.status) }}</span>
            @if (canManage()) { <button type="button" class="btn btn-primary btn-sm" (click)="openItem()">Agregar rubro</button> }
          </div>
        </div>
        @if (loadingItems()) {
          <div class="loading"><span class="spinner"></span> Cargando rubros…</div>
        } @else if (!items().length) {
          <app-empty-state symbol="≡" title="Sin rubros" message="Agrega rubros de ingreso y gasto para este presupuesto." />
        } @else {
          <div class="table-wrap">
            <table class="table">
              <thead><tr><th>Código</th><th>Rubro</th><th>Tipo</th><th class="num">Presupuestado</th><th class="num">Ejecutado</th><th class="num">Disponible</th><th style="width:210px"></th></tr></thead>
              <tbody>
                @for (item of items(); track item.id) {
                  <tr>
                    <td class="mono">{{ item.code }}</td>
                    <td class="main-cell">{{ item.name }}</td>
                    <td>{{ label(item.type) }}</td>
                    <td class="num">{{ item.budgetedAmount | money }}</td>
                    <td class="num">{{ item.executedAmount | money }}</td>
                    <td class="num">{{ (item.budgetedAmount || 0) - (item.executedAmount || 0) | money }}</td>
                    <td>
                      <div class="table-actions">
                        <button type="button" class="btn btn-secondary btn-sm" (click)="openExecutions(item)">Ejecuciones</button>
                        @if (canManage()) { <button type="button" class="btn btn-primary btn-sm" (click)="openExecution(item)">Registrar gasto</button> }
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
              <tfoot>
                <tr>
                  <td colspan="3">Totales del presupuesto</td>
                  <td class="num">{{ totalBudgeted() | money }}</td>
                  <td class="num">{{ totalExecuted() | money }}</td>
                  <td class="num">{{ totalBudgeted() - totalExecuted() | money }}</td>
                  <td></td>
                </tr>
              </tfoot>
            </table>
          </div>
        }
      </div>
    }

    <app-modal [open]="budgetOpen()" title="Nuevo presupuesto anual" subtitle="Define el año y el nombre del presupuesto." [narrow]="true" (closed)="budgetOpen.set(false)">
      @if (error()) { <div class="alert alert-error" style="margin-bottom:14px">{{ error() }}</div> }
      <div class="form-grid">
        <div class="field"><label for="budgetYear">Año *</label><input id="budgetYear" name="budgetYear" type="number" min="2020" class="input" [(ngModel)]="budgetForm.year"></div>
        <div class="field"><label for="budgetTitle">Nombre *</label><input id="budgetTitle" name="budgetTitle" class="input" maxlength="150" [(ngModel)]="budgetForm.title" placeholder="Presupuesto anual"></div>
      </div>
      <div class="form-actions">
        <button type="button" class="btn btn-secondary" (click)="budgetOpen.set(false)">Cancelar</button>
        <button type="button" class="btn btn-primary" [disabled]="saving()" (click)="createBudget()">Crear presupuesto</button>
      </div>
    </app-modal>

    <app-modal [open]="itemOpen()" title="Agregar rubro" [subtitle]="selected()?.title || ''" [narrow]="true" (closed)="itemOpen.set(false)">
      @if (error()) { <div class="alert alert-error" style="margin-bottom:14px">{{ error() }}</div> }
      <div class="form-grid">
        <div class="field"><label for="itemCode">Código *</label><input id="itemCode" name="itemCode" class="input" maxlength="30" [(ngModel)]="itemForm.code" placeholder="Ej. 4101"></div>
        <div class="field">
          <label for="itemType">Tipo *</label>
          <select id="itemType" name="itemType" class="select" [(ngModel)]="itemForm.type">
            <option value="INCOME">Ingreso</option>
            <option value="EXPENSE">Gasto</option>
          </select>
        </div>
        <div class="field span-2"><label for="itemName">Nombre del rubro *</label><input id="itemName" name="itemName" class="input" maxlength="150" [(ngModel)]="itemForm.name"></div>
        <div class="field span-2"><label for="itemAmount">Valor presupuestado *</label><input id="itemAmount" name="itemAmount" type="number" min="0" step="0.01" class="input" [(ngModel)]="itemForm.budgetedAmount"></div>
      </div>
      <div class="form-actions">
        <button type="button" class="btn btn-secondary" (click)="itemOpen.set(false)">Cancelar</button>
        <button type="button" class="btn btn-primary" [disabled]="saving()" (click)="createItem()">Agregar rubro</button>
      </div>
    </app-modal>

    <app-modal [open]="executionOpen()" title="Registrar ejecución" [subtitle]="executionItem()?.name || ''" (closed)="executionOpen.set(false)">
      @if (error()) { <div class="alert alert-error" style="margin-bottom:14px">{{ error() }}</div> }
      <div class="form-grid">
        <div class="field"><label for="execAmount">Valor *</label><input id="execAmount" name="execAmount" type="number" min="0.01" step="0.01" class="input" [(ngModel)]="executionForm.amount"></div>
        <div class="field"><label for="execDate">Fecha</label><input id="execDate" name="execDate" type="date" class="input" [(ngModel)]="executionForm.executionDate"></div>
        <div class="field"><label for="execInvoice">Factura</label><input id="execInvoice" name="execInvoice" class="input" maxlength="50" [(ngModel)]="executionForm.invoiceNumber"></div>
        <div class="field span-2"><label for="execDescription">Descripción *</label><textarea id="execDescription" name="execDescription" class="textarea" maxlength="2000" [(ngModel)]="executionForm.description"></textarea></div>
      </div>
      <div class="form-actions">
        <button type="button" class="btn btn-secondary" (click)="executionOpen.set(false)">Cancelar</button>
        <button type="button" class="btn btn-primary" [disabled]="saving()" (click)="registerExecution()">Registrar ejecución</button>
      </div>
    </app-modal>

    <app-modal [open]="executionsOpen()" title="Ejecuciones del rubro" [subtitle]="executionsItem()?.name || ''" (closed)="executionsOpen.set(false)">
      @if (loadingExecutions()) {
        <div class="loading"><span class="spinner"></span> Cargando ejecuciones…</div>
      } @else if (!executions().length) {
        <app-empty-state symbol="≡" title="Sin ejecuciones" message="Este rubro aún no tiene gastos registrados." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Fecha</th><th>Descripción</th><th>Factura</th><th class="num">Valor</th></tr></thead>
            <tbody>
              @for (execution of executions(); track execution.id) {
                <tr>
                  <td>{{ execution.executionDate | date:'dd/MM/yyyy' }}</td>
                  <td class="main-cell">{{ execution.description }}</td>
                  <td class="mono">{{ execution.invoiceNumber || '—' }}</td>
                  <td class="num">{{ execution.amount | money }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </app-modal>
  `
})
export class BudgetPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly budgets = signal<AnnualBudget[]>([]);
  readonly summary = signal<BudgetSummary | null>(null);
  readonly selected = signal<AnnualBudget | null>(null);
  readonly items = signal<BudgetItem[]>([]);
  readonly executions = signal<BudgetExecution[]>([]);
  readonly loading = signal(false);
  readonly loadingItems = signal(false);
  readonly loadingExecutions = signal(false);
  readonly saving = signal(false);
  readonly budgetOpen = signal(false);
  readonly itemOpen = signal(false);
  readonly executionOpen = signal(false);
  readonly executionsOpen = signal(false);
  readonly error = signal('');
  year = currentYear();
  budgetForm: CreateBudgetRequest = { year: currentYear(), title: '' };
  itemForm: CreateItemRequest = { code: '', name: '', type: 'EXPENSE', budgetedAmount: 0 };
  executionForm: ExecuteBudgetRequest = { amount: 0, executionDate: today(), invoiceNumber: '', description: '' };
  readonly executionItem = signal<BudgetItem | null>(null);
  readonly executionsItem = signal<BudgetItem | null>(null);

  constructor() {
    void this.load();
    void this.loadSummary();
  }

  canManage(): boolean {
    return this.auth.hasPermission('BUDGET_MANAGE');
  }

  async load(): Promise<void> {
    this.loading.set(true);
    try {
      const budgets = await firstValueFrom(this.api.get<AnnualBudget[]>('/budgets'));
      this.budgets.set(budgets);
      const current = this.selected();
      const refreshed = current?.id ? budgets.find((budget) => budget.id === current.id) ?? null : null;
      this.selected.set(refreshed);
      if (refreshed?.id) await this.loadItems(refreshed.id);
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  async loadSummary(): Promise<void> {
    try {
      const summary = await firstValueFrom(this.api.get<BudgetSummary>(`/budgets/${this.year}/summary`));
      this.summary.set(summary);
    } catch {
      this.summary.set(null);
    }
  }

  async selectBudget(budget: AnnualBudget): Promise<void> {
    this.selected.set(budget);
    this.year = budget.year ?? this.year;
    if (budget.id) await this.loadItems(budget.id);
    await this.loadSummary();
  }

  async loadItems(budgetId: string): Promise<void> {
    this.loadingItems.set(true);
    try {
      this.items.set(await firstValueFrom(this.api.get<BudgetItem[]>(`/budgets/${budgetId}/items`)));
    } finally {
      this.loadingItems.set(false);
    }
  }

  totalBudgeted(): number {
    return this.items().reduce((sum, item) => sum + Number(item.budgetedAmount ?? 0), 0);
  }

  totalExecuted(): number {
    return this.items().reduce((sum, item) => sum + Number(item.executedAmount ?? 0), 0);
  }

  openBudget(): void {
    this.error.set('');
    this.budgetForm = { year: currentYear(), title: '' };
    this.budgetOpen.set(true);
  }

  async createBudget(): Promise<void> {
    this.error.set('');
    if (!this.budgetForm.title.trim() || !this.budgetForm.year) {
      this.error.set('Indica el año y el nombre del presupuesto.');
      return;
    }
    this.saving.set(true);
    try {
      const created = await firstValueFrom(this.api.post<AnnualBudget>('/budgets', {
        year: Number(this.budgetForm.year),
        title: this.budgetForm.title.trim()
      }));
      this.toast.success('Presupuesto creado.');
      this.budgetOpen.set(false);
      await this.load();
      await this.selectBudget(created);
      await this.loadSummary();
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  openItem(): void {
    this.error.set('');
    this.itemForm = { code: '', name: '', type: 'EXPENSE', budgetedAmount: 0 };
    this.itemOpen.set(true);
  }

  async createItem(): Promise<void> {
    this.error.set('');
    const budget = this.selected();
    if (!budget?.id) return;
    if (!this.itemForm.code.trim() || !this.itemForm.name.trim()) {
      this.error.set('Completa el código y el nombre del rubro.');
      return;
    }
    this.saving.set(true);
    try {
      await firstValueFrom(this.api.post<BudgetItem>(`/budgets/${budget.id}/items`, {
        code: this.itemForm.code.trim(),
        name: this.itemForm.name.trim(),
        type: this.itemForm.type,
        budgetedAmount: Number(this.itemForm.budgetedAmount ?? 0)
      }));
      this.toast.success('Rubro agregado.');
      this.itemOpen.set(false);
      await this.loadItems(budget.id);
      await this.load();
      await this.loadSummary();
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  openExecution(item: BudgetItem): void {
    this.error.set('');
    this.executionItem.set(item);
    this.executionForm = { amount: 0, executionDate: today(), invoiceNumber: '', description: '' };
    this.executionOpen.set(true);
  }

  async registerExecution(): Promise<void> {
    this.error.set('');
    const item = this.executionItem();
    if (!item?.id) return;
    if (!this.executionForm.description.trim() || !this.executionForm.amount) {
      this.error.set('Indica el valor y la descripción de la ejecución.');
      return;
    }
    this.saving.set(true);
    try {
      await firstValueFrom(this.api.post<BudgetExecution>(`/budgets/items/${item.id}/executions`, {
        amount: Number(this.executionForm.amount),
        executionDate: this.executionForm.executionDate,
        invoiceNumber: this.executionForm.invoiceNumber?.trim() || undefined,
        description: this.executionForm.description.trim()
      }));
      this.toast.success('Ejecución registrada.');
      this.executionOpen.set(false);
      if (this.selected()?.id) await this.loadItems(this.selected()!.id!);
      await this.load();
      await this.loadSummary();
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async openExecutions(item: BudgetItem): Promise<void> {
    if (!item.id) return;
    this.executionsItem.set(item);
    this.executions.set([]);
    this.executionsOpen.set(true);
    this.loadingExecutions.set(true);
    try {
      this.executions.set(await firstValueFrom(this.api.get<BudgetExecution[]>(`/budgets/items/${item.id}/executions`)));
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.loadingExecutions.set(false);
    }
  }
}
