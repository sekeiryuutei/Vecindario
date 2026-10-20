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
  CreateContractRequest,
  CreateSupplierRequest,
  PageResultSupplier,
  Supplier,
  SupplierContract
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError, today } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent, PaginationComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-suppliers-page',
  standalone: true,
  imports: [FormsModule, DatePipe, MoneyPipe, PageHeaderComponent, ModalComponent, PaginationComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Proveedores y contratistas" subtitle="Directorio de proveedores, calificaciones y contratos vigentes.">
      @if (auth.hasPermission('SUPPLIERS_MANAGE')) { <button type="button" class="btn btn-primary" (click)="openCreate()">Nuevo proveedor</button> }
    </app-page-header>

    <div class="filter-bar">
      <div class="field grow"><label for="q">Buscar</label><input id="q" name="q" class="input" [(ngModel)]="query" (keyup.enter)="load(0)" placeholder="Nombre, NIT, contacto o correo"></div>
      <div class="field"><label for="category">Categoría</label><input id="category" name="category" class="input" [(ngModel)]="category" (keyup.enter)="load(0)" placeholder="Ej. Jardinería"></div>
      <div class="field"><label for="active">Estado</label><select id="active" name="active" class="select" [(ngModel)]="active" (change)="load(0)"><option value="">Todos</option><option value="true">Activos</option><option value="false">Inactivos</option></select></div>
      <button type="button" class="btn btn-secondary" (click)="load(0)">Filtrar</button>
      <button type="button" class="btn btn-ghost" (click)="clearFilters()">Limpiar</button>
    </div>

    <div class="card">
      <div class="card-header"><div><h2>Directorio</h2><p>{{ totalElements() }} proveedor(es).</p></div><button type="button" class="btn btn-secondary btn-sm" (click)="load(page())">Actualizar</button></div>
      @if (loading()) {
        <div class="loading"><span class="spinner"></span> Cargando proveedores…</div>
      } @else if (!items().length) {
        <app-empty-state symbol="⌂" title="Sin proveedores" message="Registra proveedores para asociarlos a contratos y órdenes de mantenimiento." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Proveedor</th><th>NIT</th><th>Contacto</th><th>Categoría</th><th class="num">Calificación</th><th>Estado</th><th style="width:170px"></th></tr></thead>
            <tbody>
              @for (item of items(); track item.id) {
                <tr>
                  <td class="main-cell">{{ item.name }}</td>
                  <td>{{ item.taxId }}</td>
                  <td><div>{{ item.contactName || '—' }}</div><div class="muted small">{{ item.contactPhone || item.contactEmail || '' }}</div></td>
                  <td>{{ item.category }}</td>
                  <td class="num">{{ item.rating != null ? item.rating + '/5' : '—' }}</td>
                  <td><span class="badge" [class]="item.isActive ? 'badge success' : 'badge neutral'">{{ item.isActive ? 'Activo' : 'Inactivo' }}</span></td>
                  <td>
                    <div class="table-actions">
                      <button type="button" class="btn btn-secondary btn-sm" (click)="openContracts(item)">Contratos</button>
                      @if (auth.hasPermission('SUPPLIERS_MANAGE')) {
                        <button type="button" class="btn-icon" title="Editar" (click)="openEdit(item)">✎</button>
                        @if (item.isActive) { <button type="button" class="btn-icon danger" title="Inactivar" (click)="deactivate(item)">⊘</button> }
                      }
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

    <app-modal [open]="formOpen()" [title]="editing() ? 'Editar proveedor' : 'Nuevo proveedor'" subtitle="Datos de contacto y clasificación del proveedor." [wide]="true" (closed)="formOpen.set(false)">
      @if (error()) { <div class="alert alert-error" style="margin-bottom:14px">{{ error() }}</div> }
      <form (ngSubmit)="save()" novalidate>
        <div class="form-grid">
          <div class="field"><label for="name">Razón social *</label><input id="name" name="name" class="input" required [(ngModel)]="form.name" maxlength="200"></div>
          <div class="field"><label for="taxId">NIT / documento *</label><input id="taxId" name="taxId" class="input" required [(ngModel)]="form.taxId" maxlength="30"></div>
          <div class="field"><label for="contactName">Contacto</label><input id="contactName" name="contactName" class="input" [(ngModel)]="form.contactName"></div>
          <div class="field"><label for="contactPhone">Teléfono</label><input id="contactPhone" name="contactPhone" class="input" [(ngModel)]="form.contactPhone"></div>
          <div class="field"><label for="contactEmail">Correo</label><input id="contactEmail" name="contactEmail" type="email" class="input" [(ngModel)]="form.contactEmail"></div>
          <div class="field"><label for="supplierCategory">Categoría *</label><input id="supplierCategory" name="supplierCategory" class="input" required [(ngModel)]="form.category" placeholder="Mantenimiento, seguridad, aseo…"></div>
          <div class="field"><label for="rating">Calificación (0 a 5)</label><input id="rating" name="rating" type="number" min="0" max="5" step="0.1" class="input" [(ngModel)]="form.rating"></div>
          <div class="field span-2"><label for="notes">Notas</label><textarea id="notes" name="notes" class="textarea" [(ngModel)]="form.notes"></textarea></div>
        </div>
        <div class="form-actions">
          <button type="button" class="btn btn-secondary" (click)="formOpen.set(false)">Cancelar</button>
          <button type="submit" class="btn btn-primary" [disabled]="saving()">Guardar proveedor</button>
        </div>
      </form>
    </app-modal>

    <app-modal [open]="contractsOpen()" [title]="'Contratos · ' + (selected()?.name || '')" subtitle="Acuerdos vigentes y vigencias." [wide]="true" (closed)="contractsOpen.set(false)">
      <div class="grid grid-2">
        <div>
          <h3 class="section-title">Contratos registrados</h3>
          <div class="stack" style="gap:9px">
            @for (contract of contracts(); track contract.id) {
              <div style="border:1px solid var(--line);border-radius:9px;padding:10px">
                <div class="row between"><div style="font-weight:650;font-size:13px">{{ contract.title }}</div><span class="badge" [class]="'badge ' + tone(contract.status)">{{ label(contract.status) }}</span></div>
                <div class="muted small">{{ contract.contractNumber }} · {{ contract.startDate | date:'dd/MM/yyyy' }} a {{ contract.endDate | date:'dd/MM/yyyy' }}</div>
                <div class="muted small">Mensual: {{ contract.monthlyAmount | money }} · Total: {{ contract.totalAmount | money }}</div>
              </div>
            } @empty { <p class="muted small">Este proveedor no tiene contratos registrados.</p> }
          </div>
        </div>
        <div>
          <h3 class="section-title">Nuevo contrato</h3>
          @if (contractError()) { <div class="alert alert-error" style="margin-bottom:10px">{{ contractError() }}</div> }
          <div class="form-grid">
            <div class="field"><label for="contractNumber">Número *</label><input id="contractNumber" name="contractNumber" class="input" [(ngModel)]="contractForm.contractNumber"></div>
            <div class="field"><label for="contractTitle">Título *</label><input id="contractTitle" name="contractTitle" class="input" [(ngModel)]="contractForm.title"></div>
            <div class="field"><label for="startDate">Inicio *</label><input id="startDate" name="startDate" type="date" class="input" [(ngModel)]="contractForm.startDate"></div>
            <div class="field"><label for="endDate">Fin *</label><input id="endDate" name="endDate" type="date" class="input" [(ngModel)]="contractForm.endDate"></div>
            <div class="field"><label for="monthly">Valor mensual</label><input id="monthly" name="monthly" type="number" min="0" step="0.01" class="input" [(ngModel)]="contractForm.monthlyAmount"></div>
            <div class="field"><label for="total">Valor total</label><input id="total" name="total" type="number" min="0" step="0.01" class="input" [(ngModel)]="contractForm.totalAmount"></div>
            <div class="field span-2"><label for="contractDescription">Descripción</label><textarea id="contractDescription" name="contractDescription" class="textarea" [(ngModel)]="contractForm.description"></textarea></div>
            <div class="field span-2"><label class="check"><input type="checkbox" [(ngModel)]="contractForm.hasInsurancePolicy"> Tiene póliza de seguro</label></div>
            <div class="field"><label for="policyNumber">Número de póliza</label><input id="policyNumber" name="policyNumber" class="input" [(ngModel)]="contractForm.insurancePolicyNumber"></div>
            <div class="field"><label for="policyExpiry">Vencimiento de póliza</label><input id="policyExpiry" name="policyExpiry" type="date" class="input" [(ngModel)]="contractForm.insuranceExpirationDate"></div>
          </div>
          <div class="form-actions"><button type="button" class="btn btn-primary" [disabled]="saving()" (click)="addContract()">Agregar contrato</button></div>
        </div>
      </div>
    </app-modal>
  `
})
export class SuppliersPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly items = signal<Supplier[]>([]);
  readonly contracts = signal<SupplierContract[]>([]);
  readonly loading = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly formOpen = signal(false);
  readonly editing = signal<Supplier | null>(null);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly contractsOpen = signal(false);
  readonly selected = signal<Supplier | null>(null);
  readonly contractError = signal('');
  query = '';
  category = '';
  active = '';
  form: CreateSupplierRequest = this.emptyForm();
  contractForm: CreateContractRequest = this.emptyContract();

  constructor() {
    void this.load(0);
  }

  async load(page = 0): Promise<void> {
    this.loading.set(true);
    try {
      const result = await firstValueFrom(this.api.get<PageResultSupplier>('/suppliers', {
        query: this.query.trim(),
        category: this.category.trim(),
        active: this.active,
        page,
        size: 20
      }));
      this.items.set(result.content ?? []);
      this.page.set(result.page ?? 0);
      this.totalPages.set(result.totalPages ?? 0);
      this.totalElements.set(result.totalElements ?? 0);
    } finally {
      this.loading.set(false);
    }
  }

  clearFilters(): void {
    this.query = '';
    this.category = '';
    this.active = '';
    void this.load(0);
  }

  openCreate(): void {
    this.editing.set(null);
    this.form = this.emptyForm();
    this.error.set('');
    this.formOpen.set(true);
  }

  openEdit(item: Supplier): void {
    this.editing.set(item);
    this.form = {
      taxId: item.taxId ?? '',
      name: item.name ?? '',
      contactName: item.contactName,
      contactPhone: item.contactPhone,
      contactEmail: item.contactEmail,
      category: item.category ?? '',
      rating: item.rating,
      notes: item.notes
    };
    this.error.set('');
    this.formOpen.set(true);
  }

  async save(): Promise<void> {
    this.error.set('');
    if (!this.form.name.trim() || !this.form.taxId.trim() || !this.form.category.trim()) {
      this.error.set('Completa razón social, NIT y categoría.');
      return;
    }
    this.saving.set(true);
    try {
      const editing = this.editing();
      if (editing?.id) {
        await firstValueFrom(this.api.put<Supplier>(`/suppliers/${editing.id}`, this.form));
        this.toast.success('Proveedor actualizado.');
      } else {
        await firstValueFrom(this.api.post<Supplier>('/suppliers', this.form));
        this.toast.success('Proveedor registrado.');
      }
      this.formOpen.set(false);
      await this.load(this.page());
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async deactivate(item: Supplier): Promise<void> {
    if (!item.id) return;
    const accepted = await this.confirm.confirm({
      title: 'Inactivar proveedor',
      message: `¿Deseas inactivar ${item.name}? No podrá asociarse a nuevas órdenes de trabajo.`,
      confirmLabel: 'Inactivar',
      danger: true
    });
    if (!accepted) return;
    await firstValueFrom(this.api.delete<void>(`/suppliers/${item.id}`));
    this.toast.success('Proveedor inactivado.');
    await this.load(this.page());
  }

  async openContracts(item: Supplier): Promise<void> {
    this.selected.set(item);
    this.contractForm = this.emptyContract();
    this.contractError.set('');
    this.contractsOpen.set(true);
    await this.loadContracts();
  }

  async loadContracts(): Promise<void> {
    const supplier = this.selected();
    if (!supplier?.id) return;
    this.contracts.set(await firstValueFrom(this.api.get<SupplierContract[]>(`/suppliers/${supplier.id}/contracts`)));
  }

  async addContract(): Promise<void> {
    const supplier = this.selected();
    this.contractError.set('');
    if (!supplier?.id || !this.contractForm.contractNumber.trim() || !this.contractForm.title.trim() || !this.contractForm.startDate || !this.contractForm.endDate) {
      this.contractError.set('Completa número, título y vigencia del contrato.');
      return;
    }
    this.saving.set(true);
    try {
      await firstValueFrom(this.api.post<SupplierContract>(`/suppliers/${supplier.id}/contracts`, {
        ...this.contractForm,
        insurancePolicyNumber: this.contractForm.hasInsurancePolicy ? this.contractForm.insurancePolicyNumber : undefined,
        insuranceExpirationDate: this.contractForm.hasInsurancePolicy ? this.contractForm.insuranceExpirationDate : undefined
      }));
      this.toast.success('Contrato registrado.');
      this.contractForm = this.emptyContract();
      await this.loadContracts();
    } catch (error) {
      this.contractError.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  private emptyForm(): CreateSupplierRequest {
    return { taxId: '', name: '', category: '', contactName: '', contactPhone: '', contactEmail: '', notes: '', rating: undefined };
  }

  private emptyContract(): CreateContractRequest {
    return {
      contractNumber: '',
      title: '',
      description: '',
      startDate: today(),
      endDate: '',
      monthlyAmount: undefined,
      totalAmount: undefined,
      hasInsurancePolicy: false,
      insurancePolicyNumber: '',
      insuranceExpirationDate: ''
    };
  }
}
