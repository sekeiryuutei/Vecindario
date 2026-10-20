import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService, saveBlob } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import {
  PageResultPayrollEmployee,
  PageResultPayrollRun,
  PayrollEmployee,
  PayrollEmployeeRequest,
  PayrollRun,
  PayrollSettings
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError } from '../../core/utils';
import { EmptyStateComponent, PageHeaderComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-payroll-page',
  standalone: true,
  imports: [FormsModule, PageHeaderComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Nómina" subtitle="Empleados, parámetros de liquidación y corridas mensuales de la copropiedad.">
      <button type="button" class="btn btn-secondary" [disabled]="loading()" (click)="load()">Actualizar</button>
    </app-page-header>

    <div class="tabs" role="tablist" aria-label="Secciones de nómina">
      <button type="button" class="tab" [class.active]="tab() === 'runs'" (click)="tab.set('runs')">Corridas</button>
      <button type="button" class="tab" [class.active]="tab() === 'employees'" (click)="tab.set('employees')">Empleados</button>
      <button type="button" class="tab" [class.active]="tab() === 'settings'" (click)="tab.set('settings')">Parámetros</button>
    </div>

    @if (error()) { <div class="alert alert-error" style="margin-bottom:14px">{{ error() }}</div> }
    @if (loading()) {
      <div class="loading"><span class="spinner"></span> Cargando nómina...</div>
    } @else {
      @if (tab() === 'runs') {
        <div class="card">
          <div class="card-header"><div><h2>Generar corrida</h2><p>Calcula la nómina del periodo con los empleados activos y los parámetros vigentes.</p></div></div>
          <div class="card-body">
            @if (canManage()) {
              <div class="form-grid">
                <div class="field"><label for="runPeriod">Periodo *</label><input id="runPeriod" type="month" class="input" [(ngModel)]="runPeriod"></div>
                <div class="field span-2"><label for="runNotes">Notas</label><input id="runNotes" class="input" maxlength="2000" [(ngModel)]="runNotes" placeholder="Opcional"></div>
              </div>
              <div class="form-actions">
                <button type="button" class="btn btn-primary" [disabled]="saving()" (click)="generateRun()">
                  {{ saving() ? 'Calculando...' : 'Generar y calcular' }}
                </button>
              </div>
            } @else {
              <div class="notice">Tu rol puede consultar la nómina, pero no generar corridas.</div>
            }
          </div>
        </div>

        <div class="card">
          <div class="card-header"><div><h2>Corridas registradas</h2><p>Historial de nóminas calculadas, pagadas y anuladas.</p></div><span class="badge neutral">{{ runsTotal() }} en total</span></div>
          <div class="card-body">
            @if (runs().length) {
              <div class="table-wrap">
                <table class="table">
                  <thead><tr><th>Periodo</th><th>Estado</th><th class="num">Devengado</th><th class="num">Deducciones</th><th class="num">Neto</th><th class="num">Costo empleador</th><th>Acciones</th></tr></thead>
                  <tbody>
                    @for (run of runs(); track run.id) {
                      <tr>
                        <td><strong>{{ run.period }}</strong><br><small class="muted">{{ run.runNumber }}</small></td>
                        <td><span class="badge" [class]="runStatusClass(run.status)">{{ runStatusLabel(run.status) }}</span></td>
                        <td class="num">{{ money(run.totalGross) }}</td>
                        <td class="num">{{ money(run.totalDeductions) }}</td>
                        <td class="num"><strong>{{ money(run.totalNet) }}</strong></td>
                        <td class="num">{{ money(run.totalEmployerCost) }}</td>
                        <td>
                          <div class="actions">
                            <button type="button" class="btn btn-sm btn-secondary" (click)="viewRun(run.id!)">Ver</button>
                            <button type="button" class="btn btn-sm btn-secondary" (click)="exportRun(run.id!)">CSV</button>
                            @if (canManage() && run.status !== 'PAID' && run.status !== 'VOID') {
                              <button type="button" class="btn btn-sm btn-secondary" (click)="recalculateRun(run.id!)">Recalcular</button>
                            }
                            @if (canManage() && run.status === 'CALCULATED') {
                              <button type="button" class="btn btn-sm btn-primary" (click)="payRun(run.id!)">Pagar</button>
                            }
                            @if (canManage() && run.status !== 'PAID' && run.status !== 'VOID') {
                              <button type="button" class="btn btn-sm btn-danger" (click)="startVoid(run.id!)">Anular</button>
                            }
                          </div>
                          @if (voidingRunId() === run.id) {
                            <div class="inline-form">
                              <input class="input" maxlength="2000" placeholder="Motivo de anulación" [(ngModel)]="voidReason">
                              <button type="button" class="btn btn-sm btn-danger" [disabled]="saving()" (click)="voidRun(run.id!)">Confirmar</button>
                              <button type="button" class="btn btn-sm btn-secondary" (click)="cancelVoid()">Cancelar</button>
                            </div>
                          }
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            } @else {
              <app-empty-state symbol="₱" title="Sin corridas" message="Genera la primera nómina del periodo." />
            }
          </div>
        </div>

        @if (selectedRun(); as run) {
          <div class="card">
            <div class="card-header">
              <div><h2>Detalle {{ run.period }}</h2><p>{{ run.runNumber }} · {{ runStatusLabel(run.status) }}</p></div>
              <button type="button" class="btn btn-secondary" (click)="selectedRun.set(null)">Cerrar</button>
            </div>
            <div class="card-body">
              @if (run.items?.length) {
                <div class="table-wrap">
                  <table class="table">
                    <thead><tr><th>Empleado</th><th>Cargo</th><th class="num">Días</th><th class="num">Devengado</th><th class="num">Salud</th><th class="num">Pensión</th><th class="num">Otras</th><th class="num">Neto</th><th class="num">Costo empleador</th></tr></thead>
                    <tbody>
                      @for (item of run.items; track item.id) {
                        <tr>
                          <td><strong>{{ item.employeeName }}</strong><br><small class="muted">{{ item.documentNumber }}</small></td>
                          <td>{{ item.position }}</td>
                          <td class="num">{{ item.workedDays }}</td>
                          <td class="num">{{ money(item.grossPay) }}</td>
                          <td class="num">{{ money(item.healthDeduction) }}</td>
                          <td class="num">{{ money(item.pensionDeduction) }}</td>
                          <td class="num">{{ money(item.otherDeductions) }}</td>
                          <td class="num"><strong>{{ money(item.netPay) }}</strong></td>
                          <td class="num">{{ money(item.employerCost) }}</td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
                <div class="totals-grid">
                  <div><span>Total devengado</span><strong>{{ money(run.totalGross) }}</strong></div>
                  <div><span>Total deducciones</span><strong>{{ money(run.totalDeductions) }}</strong></div>
                  <div><span>Total neto</span><strong>{{ money(run.totalNet) }}</strong></div>
                  <div><span>Costo total empleador</span><strong>{{ money(run.totalEmployerCost) }}</strong></div>
                </div>
              } @else {
                <app-empty-state symbol="—" title="Sin detalle" message="Esta corrida no tiene empleados liquidados." />
              }
            </div>
          </div>
        }
      }

      @if (tab() === 'employees') {
        @if (canManage()) {
          <div class="card">
            <div class="card-header"><div><h2>{{ editingEmployeeId() ? 'Editar empleado' : 'Nuevo empleado' }}</h2><p>Datos laborales y deducciones fijas autorizadas.</p></div></div>
            <div class="card-body">
              <div class="form-grid">
                <div class="field"><label for="docType">Tipo de documento *</label><select id="docType" class="input" [(ngModel)]="employeeForm.documentType"><option value="CC">CC</option><option value="CE">CE</option><option value="NIT">NIT</option><option value="PAS">PAS</option><option value="OTRO">OTRO</option></select></div>
                <div class="field"><label for="docNumber">Número de documento *</label><input id="docNumber" class="input" maxlength="30" [(ngModel)]="employeeForm.documentNumber"></div>
                <div class="field span-2"><label for="fullName">Nombre completo *</label><input id="fullName" class="input" maxlength="200" [(ngModel)]="employeeForm.fullName"></div>
                <div class="field"><label for="position">Cargo *</label><input id="position" class="input" maxlength="100" [(ngModel)]="employeeForm.position"></div>
                <div class="field"><label for="baseSalary">Salario base *</label><input id="baseSalary" type="number" min="0" step="0.01" class="input" [(ngModel)]="employeeForm.baseSalary"></div>
                <div class="field"><label for="startDate">Fecha de ingreso *</label><input id="startDate" type="date" class="input" [(ngModel)]="employeeForm.startDate"></div>
                <div class="field"><label for="endDate">Fecha de retiro</label><input id="endDate" type="date" class="input" [(ngModel)]="employeeForm.endDate"></div>
                <div class="field"><label for="fixedDeductions">Deducciones fijas</label><input id="fixedDeductions" type="number" min="0" step="0.01" class="input" [(ngModel)]="employeeForm.fixedDeductions"></div>
                <div class="field"><label for="status">Estado</label><select id="status" class="input" [(ngModel)]="employeeForm.status"><option value="ACTIVE">Activo</option><option value="INACTIVE">Inactivo</option></select></div>
                <div class="field"><label for="bankName">Banco</label><input id="bankName" class="input" maxlength="100" [(ngModel)]="employeeForm.bankName"></div>
                <div class="field"><label for="bankAccount">Cuenta bancaria</label><input id="bankAccount" class="input" maxlength="50" [(ngModel)]="employeeForm.bankAccount"></div>
                <div class="field"><label for="costCenter">Centro de costo</label><input id="costCenter" class="input" maxlength="100" [(ngModel)]="employeeForm.costCenter"></div>
                <div class="field span-2"><label for="employeeNotes">Notas</label><input id="employeeNotes" class="input" maxlength="2000" [(ngModel)]="employeeForm.notes"></div>
              </div>
              <div class="form-actions">
                <button type="button" class="btn btn-primary" [disabled]="saving()" (click)="saveEmployee()">{{ saving() ? 'Guardando...' : 'Guardar empleado' }}</button>
                @if (editingEmployeeId()) { <button type="button" class="btn btn-secondary" (click)="cancelEmployee()">Cancelar</button> }
              </div>
            </div>
          </div>
        }

        <div class="card">
          <div class="card-header"><div><h2>Empleados</h2><p>Personal activo e inactivo de la copropiedad.</p></div><span class="badge neutral">{{ employeesTotal() }} en total</span></div>
          <div class="card-body">
            @if (employees().length) {
              <div class="table-wrap">
                <table class="table">
                  <thead><tr><th>Empleado</th><th>Cargo</th><th class="num">Salario</th><th>Ingreso</th><th>Estado</th>@if (canManage()) {<th>Acciones</th>}</tr></thead>
                  <tbody>
                    @for (employee of employees(); track employee.id) {
                      <tr>
                        <td><strong>{{ employee.fullName }}</strong><br><small class="muted">{{ employee.documentType }} {{ employee.documentNumber }}</small></td>
                        <td>{{ employee.position }}</td>
                        <td class="num">{{ money(employee.baseSalary) }}</td>
                        <td>{{ employee.startDate }}</td>
                        <td><span class="badge" [class]="employee.status === 'ACTIVE' ? 'badge success' : 'badge neutral'">{{ employee.status === 'ACTIVE' ? 'Activo' : 'Inactivo' }}</span></td>
                        @if (canManage()) {
                          <td>
                            <div class="actions">
                              <button type="button" class="btn btn-sm btn-secondary" (click)="editEmployee(employee)">Editar</button>
                              @if (employee.status === 'ACTIVE') { <button type="button" class="btn btn-sm btn-danger" (click)="deactivateEmployee(employee)">Inactivar</button> }
                            </div>
                          </td>
                        }
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            } @else {
              <app-empty-state symbol="👥" title="Sin empleados" message="Registra el primer empleado para poder generar la nómina." />
            }
          </div>
        </div>
      }

      @if (tab() === 'settings') {
        <div class="card">
          <div class="card-header"><div><h2>Parámetros de liquidación</h2><p>Porcentajes y bases configurables. Valida los valores con tu contador antes de usarlos en producción.</p></div></div>
          <div class="card-body">
            @if (settings(); as current) {
              @if (canManage()) {
                <div class="form-grid">
                  <div class="field"><label for="currency">Moneda</label><input id="currency" class="input" maxlength="3" [(ngModel)]="settingsForm.currency"></div>
                  <div class="field"><label for="periodicity">Periodicidad</label><select id="periodicity" class="input" [(ngModel)]="settingsForm.periodicity"><option value="MONTHLY">Mensual</option><option value="BIWEEKLY">Quincenal</option></select></div>
                  <div class="field"><label for="baseDays">Días base</label><input id="baseDays" type="number" min="1" max="31" class="input" [(ngModel)]="settingsForm.baseDays"></div>
                  <div class="field"><label for="minimumWage">Salario mínimo de referencia</label><input id="minimumWage" type="number" min="0" step="0.01" class="input" [(ngModel)]="settingsForm.minimumWage"></div>
                  <div class="field"><label for="transportAllowance">Auxilio de transporte</label><input id="transportAllowance" type="number" min="0" step="0.01" class="input" [(ngModel)]="settingsForm.transportAllowance"></div>
                </div>
                <h3 style="margin-top:20px">Aportes del empleado</h3>
                <div class="form-grid">
                  <div class="field"><label for="healthEmployeeRate">Salud (%)</label><input id="healthEmployeeRate" type="number" min="0" max="100" step="0.01" class="input" [(ngModel)]="settingsForm.healthEmployeeRate"></div>
                  <div class="field"><label for="pensionEmployeeRate">Pensión (%)</label><input id="pensionEmployeeRate" type="number" min="0" max="100" step="0.01" class="input" [(ngModel)]="settingsForm.pensionEmployeeRate"></div>
                </div>
                <h3 style="margin-top:20px">Aportes y provisiones del empleador</h3>
                <div class="form-grid">
                  <div class="field"><label for="healthEmployerRate">Salud (%)</label><input id="healthEmployerRate" type="number" min="0" max="100" step="0.01" class="input" [(ngModel)]="settingsForm.healthEmployerRate"></div>
                  <div class="field"><label for="pensionEmployerRate">Pensión (%)</label><input id="pensionEmployerRate" type="number" min="0" max="100" step="0.01" class="input" [(ngModel)]="settingsForm.pensionEmployerRate"></div>
                  <div class="field"><label for="arlRate">ARL (%)</label><input id="arlRate" type="number" min="0" max="100" step="0.001" class="input" [(ngModel)]="settingsForm.arlRate"></div>
                  <div class="field"><label for="compensationRate">Compensación familiar (%)</label><input id="compensationRate" type="number" min="0" max="100" step="0.01" class="input" [(ngModel)]="settingsForm.compensationRate"></div>
                  <div class="field"><label for="senaRate">SENA (%)</label><input id="senaRate" type="number" min="0" max="100" step="0.01" class="input" [(ngModel)]="settingsForm.senaRate"></div>
                  <div class="field"><label for="icbfRate">ICBF (%)</label><input id="icbfRate" type="number" min="0" max="100" step="0.01" class="input" [(ngModel)]="settingsForm.icbfRate"></div>
                  <div class="field"><label for="severanceRate">Cesantías (%)</label><input id="severanceRate" type="number" min="0" max="100" step="0.01" class="input" [(ngModel)]="settingsForm.severanceRate"></div>
                  <div class="field"><label for="vacationRate">Vacaciones (%)</label><input id="vacationRate" type="number" min="0" max="100" step="0.01" class="input" [(ngModel)]="settingsForm.vacationRate"></div>
                  <div class="field"><label for="severanceInterestRate">Intereses de cesantías (%)</label><input id="severanceInterestRate" type="number" min="0" max="100" step="0.01" class="input" [(ngModel)]="settingsForm.severanceInterestRate"></div>
                </div>
                <div class="form-actions">
                  <button type="button" class="btn btn-primary" [disabled]="saving()" (click)="saveSettings()">{{ saving() ? 'Guardando...' : 'Guardar parámetros' }}</button>
                </div>
              } @else {
                <dl class="detail-list">
                  <dt>Moneda</dt><dd>{{ current.currency }}</dd>
                  <dt>Periodicidad</dt><dd>{{ current.periodicity === 'BIWEEKLY' ? 'Quincenal' : 'Mensual' }}</dd>
                  <dt>Días base</dt><dd>{{ current.baseDays }}</dd>
                  <dt>Salud empleado</dt><dd>{{ current.healthEmployeeRate }}%</dd>
                  <dt>Pensión empleado</dt><dd>{{ current.pensionEmployeeRate }}%</dd>
                  <dt>Salud empleador</dt><dd>{{ current.healthEmployerRate }}%</dd>
                  <dt>Pensión empleador</dt><dd>{{ current.pensionEmployerRate }}%</dd>
                  <dt>ARL</dt><dd>{{ current.arlRate }}%</dd>
                  <dt>Compensación</dt><dd>{{ current.compensationRate }}%</dd>
                  <dt>SENA</dt><dd>{{ current.senaRate }}%</dd>
                  <dt>ICBF</dt><dd>{{ current.icbfRate }}%</dd>
                  <dt>Cesantías</dt><dd>{{ current.severanceRate }}%</dd>
                  <dt>Vacaciones</dt><dd>{{ current.vacationRate }}%</dd>
                  <dt>Intereses cesantías</dt><dd>{{ current.severanceInterestRate }}%</dd>
                </dl>
              }
            }
          </div>
        </div>
      }
    }
  `,
  styles: [`
    .tabs { display:flex; gap:6px; margin-bottom:14px; border-bottom:1px solid var(--line); }
    .tab { border:0; background:transparent; padding:10px 14px; font:inherit; font-weight:600; color:var(--muted); cursor:pointer; border-bottom:2px solid transparent; }
    .tab.active { color:var(--primary); border-bottom-color:var(--primary); }
    .actions { display:flex; gap:5px; flex-wrap:wrap; }
    .inline-form { display:flex; gap:6px; margin-top:8px; align-items:center; }
    .inline-form .input { min-width:220px; }
    .totals-grid { display:grid; grid-template-columns:repeat(4, minmax(0, 1fr)); gap:10px; margin-top:16px; }
    .totals-grid > div { border:1px solid var(--line); border-radius:10px; padding:12px; background:#fbfdfe; }
    .totals-grid span { display:block; font-size:11.5px; color:var(--muted); text-transform:uppercase; letter-spacing:.04em; }
    .totals-grid strong { display:block; margin-top:4px; font-size:18px; }
    @media (max-width: 760px) { .totals-grid { grid-template-columns:1fr 1fr; } .inline-form { flex-wrap:wrap; } }
  `]
})
export class PayrollPage {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly tab = signal<'runs' | 'employees' | 'settings'>('runs');
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly settings = signal<PayrollSettings | null>(null);
  readonly employees = signal<PayrollEmployee[]>([]);
  readonly employeesTotal = signal(0);
  readonly runs = signal<PayrollRun[]>([]);
  readonly runsTotal = signal(0);
  readonly selectedRun = signal<PayrollRun | null>(null);
  readonly editingEmployeeId = signal<string | null>(null);
  readonly voidingRunId = signal<string | null>(null);

  runPeriod = new Date().toISOString().slice(0, 7);
  runNotes = '';
  voidReason = '';
  settingsForm: Required<Omit<PayrollSettings, 'updatedAt'>> = {
    currency: 'COP',
    periodicity: 'MONTHLY',
    baseDays: 30,
    healthEmployeeRate: 4,
    pensionEmployeeRate: 4,
    healthEmployerRate: 8.5,
    pensionEmployerRate: 12,
    arlRate: 0.522,
    compensationRate: 3,
    senaRate: 2,
    icbfRate: 3,
    severanceRate: 8.33,
    vacationRate: 4.17,
    severanceInterestRate: 1,
    transportAllowance: 0,
    minimumWage: 0
  };
  employeeForm: PayrollEmployeeRequest = this.emptyEmployee();

  constructor() {
    void this.load();
  }

  canManage(): boolean {
    return this.auth.hasPermission('PAYROLL_MANAGE');
  }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      const [settings, employees, runs] = await Promise.all([
        firstValueFrom(this.api.get<PayrollSettings>('/payroll/settings')),
        firstValueFrom(this.api.get<PageResultPayrollEmployee>('/payroll/employees', { size: 100 })),
        firstValueFrom(this.api.get<PageResultPayrollRun>('/payroll/runs', { size: 50 }))
      ]);
      this.settings.set(settings);
      this.settingsForm = {
        currency: settings.currency ?? 'COP',
        periodicity: settings.periodicity ?? 'MONTHLY',
        baseDays: settings.baseDays ?? 30,
        healthEmployeeRate: settings.healthEmployeeRate ?? 4,
        pensionEmployeeRate: settings.pensionEmployeeRate ?? 4,
        healthEmployerRate: settings.healthEmployerRate ?? 8.5,
        pensionEmployerRate: settings.pensionEmployerRate ?? 12,
        arlRate: settings.arlRate ?? 0.522,
        compensationRate: settings.compensationRate ?? 3,
        senaRate: settings.senaRate ?? 2,
        icbfRate: settings.icbfRate ?? 3,
        severanceRate: settings.severanceRate ?? 8.33,
        vacationRate: settings.vacationRate ?? 4.17,
        severanceInterestRate: settings.severanceInterestRate ?? 1,
        transportAllowance: settings.transportAllowance ?? 0,
        minimumWage: settings.minimumWage ?? 0
      };
      this.employees.set(employees.content ?? []);
      this.employeesTotal.set(employees.totalElements ?? 0);
      this.runs.set(runs.content ?? []);
      this.runsTotal.set(runs.totalElements ?? 0);
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  async saveSettings(): Promise<void> {
    this.saving.set(true);
    this.error.set('');
    try {
      const updated = await firstValueFrom(this.api.put<PayrollSettings>('/payroll/settings', this.settingsForm));
      this.settings.set(updated);
      this.toast.success('Parámetros de nómina guardados.');
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async saveEmployee(): Promise<void> {
    this.error.set('');
    if (!this.employeeForm.documentNumber.trim() || !this.employeeForm.fullName.trim()
        || !this.employeeForm.position.trim() || !this.employeeForm.startDate
        || this.employeeForm.baseSalary == null) {
      this.error.set('Completa documento, nombre, cargo, salario y fecha de ingreso.');
      return;
    }
    this.saving.set(true);
    try {
      const body: PayrollEmployeeRequest = {
        ...this.employeeForm,
        documentType: (this.employeeForm.documentType || 'CC').toUpperCase(),
        fixedDeductions: Number(this.employeeForm.fixedDeductions) || 0,
        baseSalary: Number(this.employeeForm.baseSalary)
      };
      if (this.editingEmployeeId()) {
        await firstValueFrom(this.api.put<PayrollEmployee>(`/payroll/employees/${this.editingEmployeeId()}`, body));
        this.toast.success('Empleado actualizado.');
      } else {
        await firstValueFrom(this.api.post<PayrollEmployee>('/payroll/employees', body));
        this.toast.success('Empleado registrado.');
      }
      this.cancelEmployee();
      await this.load();
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  editEmployee(employee: PayrollEmployee): void {
    this.editingEmployeeId.set(employee.id ?? null);
    this.employeeForm = {
      documentType: employee.documentType ?? 'CC',
      documentNumber: employee.documentNumber ?? '',
      fullName: employee.fullName ?? '',
      position: employee.position ?? '',
      baseSalary: employee.baseSalary ?? 0,
      startDate: employee.startDate ?? '',
      endDate: employee.endDate,
      status: employee.status ?? 'ACTIVE',
      bankName: employee.bankName,
      bankAccount: employee.bankAccount,
      costCenter: employee.costCenter,
      fixedDeductions: employee.fixedDeductions ?? 0,
      notes: employee.notes
    };
    this.tab.set('employees');
  }

  cancelEmployee(): void {
    this.editingEmployeeId.set(null);
    this.employeeForm = this.emptyEmployee();
  }

  async deactivateEmployee(employee: PayrollEmployee): Promise<void> {
    if (!employee.id || !window.confirm(`¿Inactivar a ${employee.fullName}? Las corridas históricas se conservan.`)) return;
    this.saving.set(true);
    try {
      await firstValueFrom(this.api.delete<void>(`/payroll/employees/${employee.id}`));
      this.toast.success('Empleado inactivado.');
      await this.load();
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async generateRun(): Promise<void> {
    this.error.set('');
    if (!this.runPeriod) {
      this.error.set('Selecciona el periodo de la nómina.');
      return;
    }
    this.saving.set(true);
    try {
      const run = await firstValueFrom(this.api.post<PayrollRun>('/payroll/runs', {
        period: this.runPeriod,
        notes: this.runNotes || undefined
      }));
      this.selectedRun.set(run);
      this.runNotes = '';
      this.toast.success(`Nómina ${run.period} calculada.`);
      await this.load();
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async viewRun(id: string): Promise<void> {
    try {
      this.selectedRun.set(await firstValueFrom(this.api.get<PayrollRun>(`/payroll/runs/${id}`)));
    } catch (error) {
      this.error.set(describeError(error));
    }
  }

  async recalculateRun(id: string): Promise<void> {
    if (!window.confirm('¿Recalcular esta nómina con los empleados y parámetros actuales?')) return;
    this.saving.set(true);
    try {
      const run = await firstValueFrom(this.api.post<PayrollRun>(`/payroll/runs/${id}/recalculate`));
      this.selectedRun.set(run);
      this.toast.success('Nómina recalculada.');
      await this.load();
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async payRun(id: string): Promise<void> {
    if (!window.confirm('¿Marcar esta nómina como pagada? Esta acción no se puede deshacer.')) return;
    this.saving.set(true);
    try {
      const run = await firstValueFrom(this.api.post<PayrollRun>(`/payroll/runs/${id}/pay`));
      this.selectedRun.set(run);
      this.toast.success('Nómina marcada como pagada.');
      await this.load();
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  startVoid(id: string): void {
    this.voidingRunId.set(id);
    this.voidReason = '';
  }

  cancelVoid(): void {
    this.voidingRunId.set(null);
    this.voidReason = '';
  }

  async voidRun(id: string): Promise<void> {
    if (this.voidReason.trim().length < 5) {
      this.error.set('Explica el motivo de anulación (mínimo 5 caracteres).');
      return;
    }
    this.saving.set(true);
    try {
      const run = await firstValueFrom(this.api.post<PayrollRun>(`/payroll/runs/${id}/void`, {
        reason: this.voidReason.trim()
      }));
      this.selectedRun.set(run);
      this.cancelVoid();
      this.toast.success('Nómina anulada.');
      await this.load();
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async exportRun(id: string): Promise<void> {
    try {
      const response = await firstValueFrom(this.api.download(`/payroll/runs/${id}/export.csv`, undefined, 'nomina.csv'));
      saveBlob(response, 'nomina.csv');
    } catch (error) {
      this.error.set(describeError(error));
    }
  }

  money(value: number | undefined): string {
    return new Intl.NumberFormat('es-CO', {
      style: 'currency', currency: this.settings()?.currency || 'COP', maximumFractionDigits: 0
    }).format(Number(value) || 0);
  }

  runStatusLabel(status: string | undefined): string {
    return ({ DRAFT: 'Borrador', CALCULATED: 'Calculada', PAID: 'Pagada', VOID: 'Anulada' } as Record<string, string>)[status ?? ''] ?? status ?? '—';
  }

  runStatusClass(status: string | undefined): string {
    if (status === 'PAID') return 'badge success';
    if (status === 'VOID') return 'badge danger';
    if (status === 'CALCULATED') return 'badge warning';
    return 'badge neutral';
  }

  private emptyEmployee(): PayrollEmployeeRequest {
    return {
      documentType: 'CC',
      documentNumber: '',
      fullName: '',
      position: '',
      baseSalary: 0,
      startDate: new Date().toISOString().slice(0, 10),
      status: 'ACTIVE',
      fixedDeductions: 0
    };
  }
}
