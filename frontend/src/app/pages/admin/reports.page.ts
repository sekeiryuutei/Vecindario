import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService, saveBlob } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { MoneyPipe } from '../../core/format.pipes';
import { BudgetReport, MaintenanceReport, PortfolioReport } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { currentYear, describeError } from '../../core/utils';
import { EmptyStateComponent, PageHeaderComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-reports-page',
  standalone: true,
  imports: [FormsModule, MoneyPipe, PageHeaderComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Reportes" subtitle="Indicadores de cartera, presupuesto y mantenimiento para la toma de decisiones.">
      @if (auth.hasPermission('REPORTS_EXPORT')) {
        <button type="button" class="btn btn-secondary" [disabled]="exporting()" (click)="exportPortfolio('csv')">Exportar cartera CSV</button>
        <button type="button" class="btn btn-secondary" [disabled]="exporting()" (click)="exportPortfolio('pdf')">Exportar cartera PDF</button>
      }
      <button type="button" class="btn btn-primary" (click)="loadAll()">Actualizar</button>
    </app-page-header>

    <div class="grid grid-4">
      <div class="stat accent"><div class="stat-label">Cartera total</div><div class="stat-value">{{ portfolio()?.totalDueSum | money }}</div><div class="stat-hint">{{ portfolio()?.totalUnitsWithDebt ?? 0 }} inmuebles con saldo</div></div>
      <div class="stat danger"><div class="stat-label">Cartera vencida</div><div class="stat-value">{{ portfolio()?.totalOverdueSum | money }}</div><div class="stat-hint">Obligaciones fuera de fecha</div></div>
      <div class="stat"><div class="stat-label">Saldo a favor</div><div class="stat-value">{{ portfolio()?.totalCreditSum | money }}</div><div class="stat-hint">Crédito de los inmuebles</div></div>
      <div class="stat warn"><div class="stat-label">Órdenes abiertas</div><div class="stat-value">{{ openWorkOrders() }}</div><div class="stat-hint">Mantenimiento en curso o pendiente</div></div>
    </div>

    <div class="grid grid-2" style="margin-top:18px">
      <div class="card">
        <div class="card-header"><div><h2>Reporte de cartera</h2><p>Saldos por inmueble.</p></div><span class="badge info">Finanzas</span></div>
        @if (loading()) {
          <div class="loading"><span class="spinner"></span> Calculando cartera…</div>
        } @else if (!portfolioItems().length) {
          <app-empty-state symbol="✓" title="Sin cartera pendiente" message="No hay inmuebles con obligaciones pendientes." />
        } @else {
          <div class="table-wrap">
            <table class="table">
              <thead><tr><th>Inmueble</th><th>Torre</th><th class="num">Pendiente</th><th class="num">Vencido</th><th class="num">A favor</th><th class="num">Cuotas vencidas</th></tr></thead>
              <tbody>
                @for (item of portfolioItems(); track item.unitIdentifier) {
                  <tr>
                    <td class="main-cell">{{ item.unitIdentifier }}</td>
                    <td>{{ item.tower || '—' }}</td>
                    <td class="num">{{ item.totalDue | money }}</td>
                    <td class="num" [style.color]="(item.overdueAmount || 0) > 0 ? '#b91c1c' : null">{{ item.overdueAmount | money }}</td>
                    <td class="num">{{ item.creditBalance | money }}</td>
                    <td class="num">{{ item.overdueChargesCount ?? 0 }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>

      <div class="card">
        <div class="card-header"><div><h2>Reporte de mantenimiento</h2><p>Estado de activos, planes y órdenes de trabajo.</p></div><span class="badge info">Operación</span></div>
        @if (loading()) {
          <div class="loading"><span class="spinner"></span> Consolidando mantenimiento…</div>
        } @else if (maintenance()) {
          <div class="card-body">
            <div class="grid grid-2">
              <div class="stat"><div class="stat-label">Activos</div><div class="stat-value">{{ maintenance()!.totalAssets ?? 0 }}</div><div class="stat-hint">{{ maintenance()!.activePlans ?? 0 }} planes activos</div></div>
              <div class="stat"><div class="stat-label">Órdenes totales</div><div class="stat-value">{{ maintenance()!.totalWorkOrders ?? 0 }}</div><div class="stat-hint">{{ maintenance()!.completedWorkOrders ?? 0 }} completadas</div></div>
              <div class="stat warn"><div class="stat-label">En proceso</div><div class="stat-value">{{ maintenance()!.inProgressWorkOrders ?? 0 }}</div><div class="stat-hint">{{ maintenance()!.pendingWorkOrders ?? 0 }} pendientes</div></div>
              <div class="stat accent"><div class="stat-label">Costo ejecutado</div><div class="stat-value">{{ maintenance()!.totalActualCost | money }}</div><div class="stat-hint">Estimado: {{ maintenance()!.totalEstimatedCost | money }}</div></div>
            </div>
            <div class="divider"></div>
            <div class="chart-bars">
              <div class="chart-row">
                <span>Órdenes completadas</span>
                <div class="chart-track"><div class="chart-fill" [style.width.%]="pct(maintenance()!.completedWorkOrders, maintenance()!.totalWorkOrders)"></div></div>
                <span class="chart-value">{{ pct(maintenance()!.completedWorkOrders, maintenance()!.totalWorkOrders) }}%</span>
              </div>
              <div class="chart-row">
                <span>Ejecución de costos</span>
                <div class="chart-track"><div class="chart-fill" [style.width.%]="pct(maintenance()!.totalActualCost, maintenance()!.totalEstimatedCost)"></div></div>
                <span class="chart-value">{{ pct(maintenance()!.totalActualCost, maintenance()!.totalEstimatedCost) }}%</span>
              </div>
            </div>
          </div>
        }
      </div>
    </div>

    <div class="card">
      <div class="card-header">
        <div><h2>Reporte presupuestal</h2><p>Comparativo entre presupuesto y ejecución del año.</p></div>
        <div class="row">
          <div class="field" style="min-width:100px">
            <label for="reportYear">Año</label>
            <input id="reportYear" name="reportYear" type="number" class="input" [(ngModel)]="year" (change)="loadBudget()">
          </div>
          <button type="button" class="btn btn-secondary btn-sm" (click)="loadBudget()">Consultar</button>
        </div>
      </div>
      @if (loadingBudget()) {
        <div class="loading"><span class="spinner"></span> Generando reporte presupuestal…</div>
      } @else if (budget()) {
        <div class="card-body">
          <div class="grid grid-4">
            <div class="stat"><div class="stat-label">Ingresos presupuestados</div><div class="stat-value">{{ budget()!.incomeBudgeted | money }}</div><div class="stat-hint">Ejecutado: {{ budget()!.incomeExecuted | money }}</div></div>
            <div class="stat"><div class="stat-label">Gastos presupuestados</div><div class="stat-value">{{ budget()!.expenseBudgeted | money }}</div><div class="stat-hint">Ejecutado: {{ budget()!.expenseExecuted | money }}</div></div>
            <div class="stat accent"><div class="stat-label">Neto presupuestado</div><div class="stat-value">{{ budget()!.netBudgeted | money }}</div></div>
            <div class="stat" [class.danger]="(budget()!.netExecuted ?? 0) < 0"><div class="stat-label">Neto ejecutado</div><div class="stat-value">{{ budget()!.netExecuted | money }}</div></div>
          </div>
          <div class="divider"></div>
          <div class="chart-bars">
            <div class="chart-row">
              <span>Ejecución de ingresos</span>
              <div class="chart-track"><div class="chart-fill" [style.width.%]="pct(budget()!.incomeExecuted, budget()!.incomeBudgeted)"></div></div>
              <span class="chart-value">{{ pct(budget()!.incomeExecuted, budget()!.incomeBudgeted) }}%</span>
            </div>
            <div class="chart-row">
              <span>Ejecución de gastos</span>
              <div class="chart-track"><div class="chart-fill" [style.width.%]="pct(budget()!.expenseExecuted, budget()!.expenseBudgeted)"></div></div>
              <span class="chart-value">{{ pct(budget()!.expenseExecuted, budget()!.expenseBudgeted) }}%</span>
            </div>
          </div>
        </div>
      } @else {
        <app-empty-state symbol="◫" title="Sin presupuesto" message="No se encontró presupuesto para el año seleccionado." />
      }
    </div>
  `
})
export class ReportsPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  readonly portfolio = signal<PortfolioReport | null>(null);
  readonly maintenance = signal<MaintenanceReport | null>(null);
  readonly budget = signal<BudgetReport | null>(null);
  readonly loading = signal(false);
  readonly loadingBudget = signal(false);
  readonly exporting = signal(false);
  year = currentYear();

  constructor() {
    void this.loadAll();
    void this.loadBudget();
  }

  portfolioItems() {
    return this.portfolio()?.items ?? [];
  }

  openWorkOrders(): number {
    return (this.maintenance()?.pendingWorkOrders ?? 0) + (this.maintenance()?.inProgressWorkOrders ?? 0);
  }

  pct(value: number | undefined, total: number | undefined): number {
    const base = Number(total ?? 0);
    if (!base) return 0;
    return Math.min(100, Math.round((Number(value ?? 0) / base) * 100));
  }

  async loadAll(): Promise<void> {
    this.loading.set(true);
    try {
      const [portfolio, maintenance] = await Promise.all([
        firstValueFrom(this.api.get<PortfolioReport>('/reports/portfolio')),
        firstValueFrom(this.api.get<MaintenanceReport>('/reports/maintenance'))
      ]);
      this.portfolio.set(portfolio);
      this.maintenance.set(maintenance);
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  async loadBudget(): Promise<void> {
    this.loadingBudget.set(true);
    try {
      this.budget.set(await firstValueFrom(this.api.get<BudgetReport>(`/reports/budget/${this.year}`)));
    } catch {
      this.budget.set(null);
    } finally {
      this.loadingBudget.set(false);
    }
  }

  async exportPortfolio(format: 'csv' | 'pdf'): Promise<void> {
    this.exporting.set(true);
    try {
      const fallback = `reporte-cartera.${format}`;
      const response = await firstValueFrom(this.api.download(`/reports/portfolio/export/${format}`, undefined, fallback));
      saveBlob(response, fallback);
      this.toast.success('Reporte descargado.');
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.exporting.set(false);
    }
  }
}
