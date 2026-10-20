import { Component, OnDestroy, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ConfirmService } from '../../core/confirm.service';
import { MoneyPipe } from '../../core/format.pipes';
import { label, statusTone } from '../../core/labels';
import {
  BankImport,
  BankRow,
  ImportProfile,
  PageResultBankImport,
  PageResultBankRow,
  PageResultPropertyUnit,
  ProfileRequest,
  PropertyUnit,
  ReconciliationSettings,
  RowDetail
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent, PaginationComponent } from '../../shared/ui.components';

type ReconciliationTab = 'imports' | 'rows' | 'profiles' | 'settings';

@Component({
  selector: 'app-reconciliation-page',
  standalone: true,
  imports: [FormsModule, DatePipe, MoneyPipe, PageHeaderComponent, ModalComponent, PaginationComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Conciliación bancaria" subtitle="Carga extractos, revisa coincidencias y aplica pagos con control de decisiones." />

    <div class="tabs">
      <button type="button" class="tab" [class.active]="tab() === 'imports'" (click)="setTab('imports')">Importaciones</button>
      <button type="button" class="tab" [class.active]="tab() === 'rows'" (click)="setTab('rows')">Movimientos</button>
      <button type="button" class="tab" [class.active]="tab() === 'profiles'" (click)="setTab('profiles')">Perfiles</button>
      <button type="button" class="tab" [class.active]="tab() === 'settings'" (click)="setTab('settings')">Configuración</button>
    </div>

    @if (tab() === 'imports') {
      <div class="grid grid-2">
        <div class="card">
          <div class="card-header"><div><h2>Nueva importación</h2><p>Extracto CSV o XLSX de máximo 5 MB.</p></div></div>
          <div class="card-body">
            @if (uploadError()) { <div class="alert alert-error" style="margin-bottom:14px">{{ uploadError() }}</div> }
            @if (uploadResult()) {
              <div class="alert alert-success" style="margin-bottom:14px">Extracto recibido. Estado: {{ label(uploadResult()!.status) }}. {{ uploadResult()!.status === 'COMPLETED' ? 'Puedes revisar las filas.' : 'El procesamiento continúa en segundo plano.' }}</div>
            }
            <form (ngSubmit)="upload()" novalidate>
              <div class="form-grid">
                <div class="field"><label for="bankName">Banco *</label><input id="bankName" name="bankName" class="input" required [(ngModel)]="uploadForm.bankName"></div>
                <div class="field"><label for="accountLabel">Cuenta *</label><input id="accountLabel" name="accountLabel" class="input" required [(ngModel)]="uploadForm.accountLabel" placeholder="Ej. Ahorros 1234"></div>
                <div class="field span-2">
                  <label for="profileId">Perfil de importación</label>
                  <select id="profileId" name="profileId" class="select" [(ngModel)]="uploadForm.profileId">
                    <option value="">Perfil activo predeterminado</option>
                    @for (profile of profiles(); track profile.id) { <option [value]="profile.id">{{ profile.name }}</option> }
                  </select>
                </div>
                <div class="field span-2">
                  <label for="file">Archivo *</label>
                  <input id="file" name="file" type="file" class="input" accept=".csv,.xlsx" (change)="onFile($event)">
                  <span class="hint">El archivo debe corresponder al formato del perfil seleccionado.</span>
                </div>
              </div>
              <div class="form-actions">
                <button type="submit" class="btn btn-primary" [disabled]="uploading()">{{ uploading() ? 'Cargando…' : 'Cargar extracto' }}</button>
              </div>
            </form>
          </div>
        </div>
        <div class="card">
          <div class="card-header"><div><h2>Últimas importaciones</h2><p>Seguimiento del procesamiento.</p></div><button type="button" class="btn btn-secondary btn-sm" (click)="loadImports(importsPage())">Actualizar</button></div>
          @if (loadingImports()) {
            <div class="loading"><span class="spinner"></span> Cargando importaciones…</div>
          } @else {
            <div class="table-wrap">
              <table class="table">
                <thead><tr><th>Archivo</th><th>Estado</th><th class="num">Filas</th><th class="num">Automáticas</th><th></th></tr></thead>
                <tbody>
                  @for (item of imports(); track item.id) {
                    <tr>
                      <td><div class="main-cell" style="font-size:12.5px">{{ item.filename || item.bankName }}</div><div class="muted small">{{ item.createdAt | date:'dd/MM HH:mm' }}</div></td>
                      <td><span class="badge" [class]="'badge ' + tone(item.status)">{{ label(item.status) }}</span></td>
                      <td class="num">{{ item.totalRows ?? 0 }}</td>
                      <td class="num">{{ item.autoMatched ?? 0 }}</td>
                      <td><button type="button" class="btn btn-secondary btn-sm" [disabled]="item.status !== 'COMPLETED'" (click)="openRows(item)">Filas</button></td>
                    </tr>
                  } @empty { <tr><td colspan="5" class="table-empty">Aún no se han cargado extractos.</td></tr> }
                </tbody>
              </table>
            </div>
            <app-pagination [page]="importsPage()" [totalPages]="importsPages()" [totalElements]="importsTotal()" (pageChange)="loadImports($event)" />
          }
        </div>
      </div>
    }

    @if (tab() === 'rows') {
      <div class="filter-bar">
        <div class="field">
          <label for="importFilter">Importación</label>
          <select id="importFilter" name="importFilter" class="select" [(ngModel)]="rowsImportId" (change)="loadRows(0)">
            <option value="">Todas</option>
            @for (item of imports(); track item.id) { <option [value]="item.id">{{ item.filename || item.bankName }} · {{ item.createdAt | date:'dd/MM/yy' }}</option> }
          </select>
        </div>
        <div class="field">
          <label for="rowStatus">Estado</label>
          <select id="rowStatus" name="rowStatus" class="select" [(ngModel)]="rowsStatus" (change)="loadRows(0)">
            <option value="">Todos</option>
            @for (status of rowStatuses; track status) { <option [value]="status">{{ label(status) }}</option> }
          </select>
        </div>
        <button type="button" class="btn btn-secondary" (click)="loadRows(0)">Filtrar</button>
      </div>
      <div class="card">
        <div class="card-header"><div><h2>Movimientos bancarios</h2><p>{{ rowsTotal() }} movimiento(s).</p></div></div>
        @if (loadingRows()) {
          <div class="loading"><span class="spinner"></span> Cargando movimientos…</div>
        } @else if (!rows().length) {
          <app-empty-state symbol="⇄" title="Sin movimientos" message="No hay filas que coincidan con los filtros." />
        } @else {
          <div class="table-wrap">
            <table class="table">
              <thead><tr><th>Fecha</th><th>Descripción</th><th class="num">Valor</th><th>Inmueble</th><th class="num">Coincidencia</th><th>Estado</th><th style="width:70px"></th></tr></thead>
              <tbody>
                @for (row of rows(); track row.id) {
                  <tr>
                    <td>{{ row.txnDate | date:'dd/MM/yyyy' }}</td>
                    <td><div class="main-cell">{{ row.description || '—' }}</div><div class="muted small">{{ row.reference || row.counterparty || '' }}</div></td>
                    <td class="num">{{ row.amount | money }}</td>
                    <td>{{ row.unitIdentifier || '—' }}</td>
                    <td class="num">{{ row.matchScore != null ? (row.matchScore * 100).toFixed(0) + '%' : '—' }}</td>
                    <td><span class="badge" [class]="'badge ' + tone(row.status)">{{ label(row.status) }}</span></td>
                    <td><button type="button" class="btn-icon" title="Revisar" (click)="openRow(row)">👁</button></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <app-pagination [page]="rowsPage()" [totalPages]="rowsPages()" [totalElements]="rowsTotal()" (pageChange)="loadRows($event)" />
        }
      </div>
    }

    @if (tab() === 'profiles') {
      <div class="card">
        <div class="card-header">
          <div><h2>Perfiles de importación</h2><p>Mapeo de columnas y formatos por banco.</p></div>
          <button type="button" class="btn btn-primary btn-sm" (click)="openProfile()">Nuevo perfil</button>
        </div>
        @if (loadingProfiles()) {
          <div class="loading"><span class="spinner"></span> Cargando perfiles…</div>
        } @else {
          <div class="table-wrap">
            <table class="table">
              <thead><tr><th>Nombre</th><th>Banco</th><th>Fecha</th><th>Valor / crédito / débito</th><th>Formato</th><th style="width:90px"></th></tr></thead>
              <tbody>
                @for (profile of profiles(); track profile.id) {
                  <tr>
                    <td class="main-cell">{{ profile.name }}</td>
                    <td>{{ profile.bankName || '—' }}</td>
                    <td class="mono">{{ profile.dateColumn }}</td>
                    <td class="mono">{{ profile.amountColumn || (profile.creditColumn + ' / ' + (profile.debitColumn || '—')) }}</td>
                    <td>{{ profile.dateFormat || '—' }} · {{ profile.decimalSeparator || '.' }}</td>
                    <td>
                      <div class="table-actions">
                        <button type="button" class="btn-icon" title="Editar" (click)="editProfile(profile)">✎</button>
                        <button type="button" class="btn-icon danger" title="Eliminar" (click)="removeProfile(profile)">🗑</button>
                      </div>
                    </td>
                  </tr>
                } @empty { <tr><td colspan="6" class="table-empty">No hay perfiles configurados.</td></tr> }
              </tbody>
            </table>
          </div>
        }
      </div>
    }

    @if (tab() === 'settings') {
      <div class="card" style="max-width:720px">
        <div class="card-header"><div><h2>Reglas de conciliación</h2><p>Umbrales para coincidencias automáticas y revisión manual.</p></div></div>
        <div class="card-body">
          @if (loadingSettings()) {
            <div class="loading"><span class="spinner"></span> Cargando configuración…</div>
          } @else {
            <div class="form-grid">
              <div class="field"><label for="autoThreshold">Umbral de coincidencia automática</label><input id="autoThreshold" name="autoThreshold" type="number" min="0" max="1" step="0.01" class="input" [(ngModel)]="settings.autoThreshold"><span class="hint">Valor entre 0 y 1. Ej. 0.92.</span></div>
              <div class="field"><label for="ambiguityGap">Margen de ambigüedad</label><input id="ambiguityGap" name="ambiguityGap" type="number" min="0" max="1" step="0.01" class="input" [(ngModel)]="settings.ambiguityGap"></div>
              <div class="field"><label class="check"><input type="checkbox" [(ngModel)]="settings.autoApply"> Aplicar automáticamente los pagos conciliados</label></div>
            </div>
            <div class="form-actions"><button type="button" class="btn btn-primary" [disabled]="savingSettings()" (click)="saveSettings()">Guardar configuración</button></div>
          }
        </div>
      </div>
    }

    <app-modal [open]="rowOpen()" title="Revisión de movimiento" subtitle="Candidatos, decisiones e historial de la fila." [wide]="true" (closed)="rowOpen.set(false)">
      @if (detail()) {
        <dl class="detail-list">
          <dt>Fecha</dt><dd>{{ detail()!.row?.txnDate | date:'dd/MM/yyyy' }}</dd>
          <dt>Descripción</dt><dd>{{ detail()!.row?.description || '—' }}</dd>
          <dt>Referencia</dt><dd class="mono">{{ detail()!.row?.reference || '—' }}</dd>
          <dt>Contraparte</dt><dd>{{ detail()!.row?.counterparty || '—' }}</dd>
          <dt>Valor</dt><dd>{{ detail()!.row?.amount | money }}</dd>
          <dt>Estado</dt><dd>{{ label(detail()!.row?.status) }}</dd>
        </dl>
        <div class="divider"></div>
        <h3 class="section-title">Candidatos</h3>
        <div class="stack">
          @for (candidate of detail()!.candidates; track candidate.unitId) {
            <div class="row between" style="border:1px solid var(--line);border-radius:8px;padding:9px 11px">
              <div><div style="font-weight:600">{{ candidate.unitIdentifier }}</div><div class="muted small">Coincidencia {{ ((candidate.score || 0) * 100).toFixed(0) }}% · {{ candidate.signals }}</div></div>
              <button type="button" class="btn btn-secondary btn-sm" (click)="openMatch(candidate.unitId)">Conciliar</button>
            </div>
          } @empty { <p class="muted small">No hay candidatos automáticos. Puedes conciliar manualmente indicando el inmueble.</p> }
        </div>
        @if (detail()!.decisions?.length) {
          <h3 class="section-title" style="margin-top:18px">Decisiones</h3>
          <div class="timeline">
            @for (decision of detail()!.decisions; track decision.id) {
              <div class="timeline-item"><span class="timeline-dot"></span><div class="timeline-content"><div style="font-size:13px;font-weight:600">{{ decision.action }} · {{ decision.fromStatus }} → {{ decision.toStatus }}</div><div class="muted small">{{ decision.reason }} · {{ decision.decidedAt | date:'dd/MM/yyyy HH:mm' }}</div></div></div>
            }
          </div>
        }
        <div class="form-actions">
          @if (detail()!.row?.status === 'MATCHED' || detail()!.row?.status === 'AUTO_MATCHED') {
            <button type="button" class="btn btn-secondary" (click)="openRevert()">Revertir conciliación</button>
          }
          <button type="button" class="btn btn-danger" (click)="openReject()">Rechazar / ignorar</button>
        </div>
      }
    </app-modal>

    <app-modal [open]="matchOpen()" title="Conciliar movimiento" subtitle="Indica el inmueble al que se aplicará el pago." [narrow]="true" (closed)="matchOpen.set(false)">
      <div class="field">
        <label for="matchUnit">Inmueble</label>
        <select id="matchUnit" name="matchUnit" class="select" [(ngModel)]="matchUnitId">
          <option value="">Selecciona un inmueble</option>
          @for (unit of units(); track unit.id) { <option [value]="unit.id">{{ unit.identifier }} · {{ unit.tower || 'Sin torre' }}</option> }
        </select>
      </div>
      <div class="field"><label for="matchReason">Motivo</label><textarea id="matchReason" name="matchReason" class="textarea" [(ngModel)]="decisionReason"></textarea></div>
      <div class="form-actions">
        <button type="button" class="btn btn-secondary" (click)="matchOpen.set(false)">Cancelar</button>
        <button type="button" class="btn btn-primary" [disabled]="actionSaving()" (click)="match()">Conciliar</button>
      </div>
    </app-modal>

    <app-modal [open]="decisionOpen()" [title]="decisionAction === 'reject' ? 'Rechazar movimiento' : 'Revertir conciliación'" subtitle="La decisión queda registrada en auditoría." [narrow]="true" (closed)="decisionOpen.set(false)">
      <div class="field"><label for="decisionReason">Motivo</label><textarea id="decisionReason" name="decisionReason" class="textarea" [(ngModel)]="decisionReason"></textarea></div>
      <div class="form-actions">
        <button type="button" class="btn btn-secondary" (click)="decisionOpen.set(false)">Cancelar</button>
        <button type="button" class="btn btn-danger" [disabled]="actionSaving()" (click)="submitDecision()">{{ decisionAction === 'reject' ? 'Rechazar' : 'Revertir' }}</button>
      </div>
    </app-modal>

    <app-modal [open]="profileOpen()" [title]="profileEditing() ? 'Editar perfil' : 'Nuevo perfil'" subtitle="Define cómo se leen las columnas del extracto." [wide]="true" (closed)="profileOpen.set(false)">
      @if (profileError()) { <div class="alert alert-error" style="margin-bottom:14px">{{ profileError() }}</div> }
      <form (ngSubmit)="saveProfile()" novalidate>
        <div class="form-grid">
          <div class="field"><label for="profileName">Nombre *</label><input id="profileName" name="profileName" class="input" required [(ngModel)]="profileForm.name"></div>
          <div class="field"><label for="profileBank">Banco</label><input id="profileBank" name="profileBank" class="input" [(ngModel)]="profileForm.bankName"></div>
          <div class="field"><label for="dateColumn">Columna fecha *</label><input id="dateColumn" name="dateColumn" class="input" required [(ngModel)]="profileForm.dateColumn"></div>
          <div class="field"><label for="amountColumn">Columna valor</label><input id="amountColumn" name="amountColumn" class="input" [(ngModel)]="profileForm.amountColumn"></div>
          <div class="field"><label for="creditColumn">Columna crédito</label><input id="creditColumn" name="creditColumn" class="input" [(ngModel)]="profileForm.creditColumn"></div>
          <div class="field"><label for="debitColumn">Columna débito</label><input id="debitColumn" name="debitColumn" class="input" [(ngModel)]="profileForm.debitColumn"></div>
          <div class="field"><label for="descriptionColumn">Columna descripción</label><input id="descriptionColumn" name="descriptionColumn" class="input" [(ngModel)]="profileForm.descriptionColumn"></div>
          <div class="field"><label for="referenceColumn">Columna referencia</label><input id="referenceColumn" name="referenceColumn" class="input" [(ngModel)]="profileForm.referenceColumn"></div>
          <div class="field"><label for="counterpartyColumn">Columna contraparte</label><input id="counterpartyColumn" name="counterpartyColumn" class="input" [(ngModel)]="profileForm.counterpartyColumn"></div>
          <div class="field"><label for="transactionIdColumn">Columna ID transacción</label><input id="transactionIdColumn" name="transactionIdColumn" class="input" [(ngModel)]="profileForm.transactionIdColumn"></div>
          <div class="field"><label for="dateFormat">Formato de fecha</label><input id="dateFormat" name="dateFormat" class="input" [(ngModel)]="profileForm.dateFormat" placeholder="dd/MM/yyyy"></div>
          <div class="field"><label for="decimalSeparator">Separador decimal</label><input id="decimalSeparator" name="decimalSeparator" class="input" [(ngModel)]="profileForm.decimalSeparator" placeholder="."></div>
        </div>
        <div class="form-actions">
          <button type="button" class="btn btn-secondary" (click)="profileOpen.set(false)">Cancelar</button>
          <button type="submit" class="btn btn-primary" [disabled]="profileSaving()">Guardar perfil</button>
        </div>
      </form>
    </app-modal>
  `
})
export class ReconciliationPage implements OnDestroy {
  private readonly api = inject(ApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly tab = signal<ReconciliationTab>('imports');
  readonly imports = signal<BankImport[]>([]);
  readonly importsPage = signal(0);
  readonly importsPages = signal(0);
  readonly importsTotal = signal(0);
  readonly loadingImports = signal(false);
  readonly rows = signal<BankRow[]>([]);
  readonly rowsPage = signal(0);
  readonly rowsPages = signal(0);
  readonly rowsTotal = signal(0);
  readonly loadingRows = signal(false);
  readonly profiles = signal<ImportProfile[]>([]);
  readonly loadingProfiles = signal(false);
  readonly rowOpen = signal(false);
  readonly detail = signal<RowDetail | null>(null);
  readonly matchOpen = signal(false);
  readonly decisionOpen = signal(false);
  readonly actionSaving = signal(false);
  readonly profileOpen = signal(false);
  readonly profileEditing = signal<ImportProfile | null>(null);
  readonly profileSaving = signal(false);
  readonly profileError = signal('');
  readonly uploading = signal(false);
  readonly uploadError = signal('');
  readonly uploadResult = signal<BankImport | null>(null);
  readonly loadingSettings = signal(false);
  readonly savingSettings = signal(false);
  readonly rowStatuses = ['AUTO_MATCHED', 'MANUAL_REVIEW', 'MATCHED', 'REJECTED', 'DUPLICATE', 'AMBIGUOUS', 'IGNORED'];
  readonly units = signal<PropertyUnit[]>([]);
  uploadForm = { bankName: '', accountLabel: '', profileId: '' };
  uploadFile: File | null = null;
  rowsImportId = '';
  rowsStatus = '';
  decisionAction: 'reject' | 'revert' = 'reject';
  decisionReason = '';
  matchUnitId = '';
  settings: ReconciliationSettings = { autoThreshold: 0.92, ambiguityGap: 0.05, autoApply: false };
  profileForm: ProfileRequest = { name: '', dateColumn: '' };
  private pollTimer: number | null = null;

  constructor() {
    void this.loadImports(0);
    void this.loadProfiles();
    void this.loadUnits();
  }

  ngOnDestroy(): void {
    if (this.pollTimer !== null) window.clearTimeout(this.pollTimer);
  }

  setTab(tab: ReconciliationTab): void {
    this.tab.set(tab);
    if (tab === 'rows' && !this.rows().length) void this.loadRows(0);
    if (tab === 'profiles' && !this.profiles().length) void this.loadProfiles();
    if (tab === 'settings' && !this.settingsLoaded) void this.loadSettings();
  }

  private settingsLoaded = false;

  async loadUnits(): Promise<void> {
    try {
      const result = await firstValueFrom(this.api.get<PageResultPropertyUnit>('/properties', { size: 200 }));
      this.units.set(result.content ?? []);
    } catch {
      // El selector se reintenta al abrir la conciliación.
    }
  }

  async loadImports(page = 0): Promise<void> {
    this.loadingImports.set(true);
    try {
      const result = await firstValueFrom(this.api.get<PageResultBankImport>('/bank-reconciliation/imports', { page, size: 10 }));
      this.imports.set(result.content ?? []);
      this.importsPage.set(result.page ?? 0);
      this.importsPages.set(result.totalPages ?? 0);
      this.importsTotal.set(result.totalElements ?? 0);
      const processing = result.content?.find((item) => item.status === 'QUEUED' || item.status === 'PROCESSING');
      if (processing) this.schedulePoll();
    } finally {
      this.loadingImports.set(false);
    }
  }

  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.uploadFile = input.files?.[0] ?? null;
  }

  async upload(): Promise<void> {
    this.uploadError.set('');
    this.uploadResult.set(null);
    if (!this.uploadForm.bankName.trim() || !this.uploadForm.accountLabel.trim()) {
      this.uploadError.set('Indica el banco y la cuenta del extracto.');
      return;
    }
    if (!this.uploadFile) {
      this.uploadError.set('Selecciona el archivo del extracto.');
      return;
    }
    this.uploading.set(true);
    try {
      const result = await firstValueFrom(this.api.upload<BankImport>('/bank-reconciliation/imports', this.uploadFile, {
        bankName: this.uploadForm.bankName.trim(),
        accountLabel: this.uploadForm.accountLabel.trim(),
        profileId: this.uploadForm.profileId
      }));
      this.uploadResult.set(result);
      this.toast.success('Extracto cargado. El procesamiento puede tardar unos segundos.');
      this.uploadFile = null;
      await this.loadImports(0);
      this.schedulePoll();
    } catch (error) {
      this.uploadError.set(describeError(error));
    } finally {
      this.uploading.set(false);
    }
  }

  openRows(item: BankImport): void {
    this.rowsImportId = item.id ?? '';
    this.rowsStatus = '';
    this.tab.set('rows');
    void this.loadRows(0);
  }

  async loadRows(page = 0): Promise<void> {
    this.loadingRows.set(true);
    try {
      const result = await firstValueFrom(this.api.get<PageResultBankRow>('/bank-reconciliation/rows', {
        importId: this.rowsImportId,
        status: this.rowsStatus,
        page,
        size: 20
      }));
      this.rows.set(result.content ?? []);
      this.rowsPage.set(result.page ?? 0);
      this.rowsPages.set(result.totalPages ?? 0);
      this.rowsTotal.set(result.totalElements ?? 0);
    } finally {
      this.loadingRows.set(false);
    }
  }

  async openRow(row: BankRow): Promise<void> {
    if (!row.id) return;
    try {
      const detail = await firstValueFrom(this.api.get<RowDetail>(`/bank-reconciliation/rows/${row.id}`));
      this.detail.set(detail);
      this.rowOpen.set(true);
    } catch {
      // El interceptor global informa el error.
    }
  }

  openMatch(unitId: string | undefined): void {
    this.matchUnitId = unitId ?? '';
    this.decisionReason = '';
    this.matchOpen.set(true);
  }

  async match(): Promise<void> {
    const rowId = this.detail()?.row?.id;
    if (!rowId || !this.matchUnitId.trim() || !this.decisionReason.trim()) {
      this.toast.warning('Indica el inmueble y el motivo de la conciliación.');
      return;
    }
    this.actionSaving.set(true);
    try {
      const detail = await firstValueFrom(this.api.post<RowDetail>(`/bank-reconciliation/rows/${rowId}/match`, {
        unitId: this.matchUnitId.trim(),
        reason: this.decisionReason.trim()
      }));
      this.detail.set(detail);
      this.matchOpen.set(false);
      this.toast.success('Movimiento conciliado.');
      await this.loadRows(this.rowsPage());
    } finally {
      this.actionSaving.set(false);
    }
  }

  openReject(): void {
    this.decisionAction = 'reject';
    this.decisionReason = '';
    this.decisionOpen.set(true);
  }

  openRevert(): void {
    this.decisionAction = 'revert';
    this.decisionReason = '';
    this.decisionOpen.set(true);
  }

  async submitDecision(): Promise<void> {
    const rowId = this.detail()?.row?.id;
    if (!rowId || !this.decisionReason.trim()) {
      this.toast.warning('Ingresa el motivo de la decisión.');
      return;
    }
    this.actionSaving.set(true);
    try {
      const detail = await firstValueFrom(this.api.post<RowDetail>(`/bank-reconciliation/rows/${rowId}/${this.decisionAction}`, {
        reason: this.decisionReason.trim()
      }));
      this.detail.set(detail);
      this.decisionOpen.set(false);
      this.toast.success(this.decisionAction === 'reject' ? 'Movimiento rechazado.' : 'Conciliación revertida.');
      await this.loadRows(this.rowsPage());
    } finally {
      this.actionSaving.set(false);
    }
  }

  async loadProfiles(): Promise<void> {
    this.loadingProfiles.set(true);
    try {
      this.profiles.set(await firstValueFrom(this.api.get<ImportProfile[]>('/bank-reconciliation/profiles')));
    } finally {
      this.loadingProfiles.set(false);
    }
  }

  openProfile(): void {
    this.profileEditing.set(null);
    this.profileForm = { name: '', dateColumn: '' };
    this.profileError.set('');
    this.profileOpen.set(true);
  }

  editProfile(profile: ImportProfile): void {
    this.profileEditing.set(profile);
    this.profileForm = {
      name: profile.name ?? '',
      bankName: profile.bankName,
      dateColumn: profile.dateColumn ?? '',
      amountColumn: profile.amountColumn,
      creditColumn: profile.creditColumn,
      debitColumn: profile.debitColumn,
      descriptionColumn: profile.descriptionColumn,
      referenceColumn: profile.referenceColumn,
      counterpartyColumn: profile.counterpartyColumn,
      transactionIdColumn: profile.transactionIdColumn,
      dateFormat: profile.dateFormat,
      decimalSeparator: profile.decimalSeparator
    };
    this.profileError.set('');
    this.profileOpen.set(true);
  }

  async saveProfile(): Promise<void> {
    this.profileError.set('');
    if (!this.profileForm.name.trim() || !this.profileForm.dateColumn.trim()) {
      this.profileError.set('El nombre y la columna de fecha son obligatorios.');
      return;
    }
    this.profileSaving.set(true);
    try {
      const editing = this.profileEditing();
      if (editing?.id) {
        await firstValueFrom(this.api.put<ImportProfile>(`/bank-reconciliation/profiles/${editing.id}`, this.profileForm));
        this.toast.success('Perfil actualizado.');
      } else {
        await firstValueFrom(this.api.post<ImportProfile>('/bank-reconciliation/profiles', this.profileForm));
        this.toast.success('Perfil creado.');
      }
      this.profileOpen.set(false);
      await this.loadProfiles();
    } catch (error) {
      this.profileError.set(describeError(error));
    } finally {
      this.profileSaving.set(false);
    }
  }

  async removeProfile(profile: ImportProfile): Promise<void> {
    if (!profile.id) return;
    const accepted = await this.confirm.confirm({
      title: 'Eliminar perfil',
      message: `¿Deseas eliminar el perfil ${profile.name}? Las importaciones históricas se conservan.`,
      confirmLabel: 'Eliminar',
      danger: true
    });
    if (!accepted) return;
    try {
      await firstValueFrom(this.api.delete<void>(`/bank-reconciliation/profiles/${profile.id}`));
      this.toast.success('Perfil eliminado.');
      await this.loadProfiles();
    } catch {
      // El interceptor global informa el error.
    }
  }

  async loadSettings(): Promise<void> {
    this.settingsLoaded = true;
    this.loadingSettings.set(true);
    try {
      this.settings = await firstValueFrom(this.api.get<ReconciliationSettings>('/bank-reconciliation/settings'));
    } catch {
      // Se muestran los valores iniciales si aún no hay configuración.
    } finally {
      this.loadingSettings.set(false);
    }
  }

  async saveSettings(): Promise<void> {
    this.savingSettings.set(true);
    try {
      await firstValueFrom(this.api.put<ReconciliationSettings>('/bank-reconciliation/settings', this.settings));
      this.toast.success('Configuración de conciliación guardada.');
    } finally {
      this.savingSettings.set(false);
    }
  }

  private schedulePoll(): void {
    if (this.pollTimer !== null) window.clearTimeout(this.pollTimer);
    this.pollTimer = window.setTimeout(() => void this.loadImports(this.importsPage()), 3000);
  }
}
