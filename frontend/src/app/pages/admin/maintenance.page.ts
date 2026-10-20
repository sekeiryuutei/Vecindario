import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { MoneyPipe } from '../../core/format.pipes';
import { label, statusTone } from '../../core/labels';
import {
  CreateAssetRequest,
  CreatePlanRequest,
  CreateWorkOrderRequest,
  MaintenanceAsset,
  MaintenancePlan,
  PageResultSupplier,
  PageResultWorkOrder,
  Supplier,
  UpdateWorkOrderStatusRequest,
  WorkOrder
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError, toIsoDateTime, today } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent, PaginationComponent } from '../../shared/ui.components';

type MaintenanceTab = 'orders' | 'assets' | 'plans';
type WorkOrderStatus = 'PENDING' | 'ASSIGNED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

@Component({
  selector: 'app-maintenance-page',
  standalone: true,
  imports: [FormsModule, DatePipe, MoneyPipe, PageHeaderComponent, ModalComponent, PaginationComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Mantenimiento" subtitle="Activos, planes preventivos y órdenes de trabajo con costos y responsables.">
      @if (auth.hasPermission('MAINTENANCE_MANAGE')) { <button type="button" class="btn btn-primary" (click)="openAction()">{{ tab() === 'orders' ? 'Nueva orden' : tab() === 'assets' ? 'Nuevo activo' : 'Nuevo plan' }}</button> }
    </app-page-header>

    <div class="tabs">
      <button type="button" class="tab" [class.active]="tab() === 'orders'" (click)="setTab('orders')">Órdenes de trabajo</button>
      <button type="button" class="tab" [class.active]="tab() === 'assets'" (click)="setTab('assets')">Activos</button>
      <button type="button" class="tab" [class.active]="tab() === 'plans'" (click)="setTab('plans')">Planes preventivos</button>
    </div>

    @if (tab() === 'orders') {
      <div class="filter-bar">
        <div class="field"><label for="orderStatus">Estado</label><select id="orderStatus" name="orderStatus" class="select" [(ngModel)]="orderStatus" (change)="loadOrders(0)"><option value="">Todos</option>@for (option of orderStatuses; track option) { <option [value]="option">{{ label(option) }}</option> }</select></div>
        <div class="field"><label for="orderPriority">Prioridad</label><select id="orderPriority" name="orderPriority" class="select" [(ngModel)]="orderPriority" (change)="loadOrders(0)"><option value="">Todas</option>@for (option of priorities; track option) { <option [value]="option">{{ label(option) }}</option> }</select></div>
        <div class="field"><label for="orderAsset">Activo</label><select id="orderAsset" name="orderAsset" class="select" [(ngModel)]="orderAssetId" (change)="loadOrders(0)"><option value="">Todos</option>@for (asset of assets(); track asset.id) { <option [value]="asset.id">{{ asset.name }}</option> }</select></div>
        <button type="button" class="btn btn-secondary" (click)="loadOrders(0)">Filtrar</button>
        <button type="button" class="btn btn-ghost" (click)="clearOrderFilters()">Limpiar</button>
      </div>
      <div class="card">
        <div class="card-header"><div><h2>Órdenes de trabajo</h2><p>{{ ordersTotal() }} orden(es).</p></div><button type="button" class="btn btn-secondary btn-sm" (click)="loadOrders(ordersPage())">Actualizar</button></div>
        @if (loadingOrders()) { <div class="loading"><span class="spinner"></span> Cargando órdenes…</div> }
        @else if (!orders().length) { <app-empty-state symbol="⚙" title="Sin órdenes" message="No hay órdenes de trabajo para los filtros seleccionados." /> }
        @else {
          <div class="table-wrap"><table class="table">
            <thead><tr><th>Orden</th><th>Título</th><th>Tipo</th><th>Prioridad</th><th>Programada</th><th class="num">Costo</th><th>Estado</th><th style="width:100px"></th></tr></thead>
            <tbody>
              @for (order of orders(); track order.id) {
                <tr>
                  <td class="mono">{{ order.orderNumber }}</td>
                  <td class="main-cell">{{ order.title }}</td>
                  <td>{{ label(order.type) }}</td>
                  <td><span class="badge" [class]="'badge ' + tone(order.priority)">{{ label(order.priority) }}</span></td>
                  <td>{{ order.scheduledStart ? (order.scheduledStart | date:'dd/MM/yyyy HH:mm') : '—' }}</td>
                  <td class="num">{{ (order.actualCost ?? order.estimatedCost) | money }}</td>
                  <td><span class="badge" [class]="'badge ' + tone(order.status)">{{ label(order.status) }}</span></td>
                  <td>@if (auth.hasPermission('MAINTENANCE_MANAGE') && order.status !== 'COMPLETED' && order.status !== 'CANCELLED') { <button type="button" class="btn btn-secondary btn-sm" (click)="openStatus(order)">Actualizar</button> }</td>
                </tr>
              }
            </tbody>
          </table></div>
          <app-pagination [page]="ordersPage()" [totalPages]="ordersPages()" [totalElements]="ordersTotal()" (pageChange)="loadOrders($event)" />
        }
      </div>
    }

    @if (tab() === 'assets') {
      <div class="filter-bar">
        <div class="field"><label for="assetCategory">Categoría</label><input id="assetCategory" name="assetCategory" class="input" [(ngModel)]="assetCategory" (keyup.enter)="loadAssets()" placeholder="Ej. Bombas"></div>
        <div class="field"><label for="assetStatus">Estado</label><select id="assetStatus" name="assetStatus" class="select" [(ngModel)]="assetStatus" (change)="loadAssets()"><option value="">Todos</option>@for (option of assetStatuses; track option) { <option [value]="option">{{ label(option) }}</option> }</select></div>
        <button type="button" class="btn btn-secondary" (click)="loadAssets()">Filtrar</button>
      </div>
      <div class="card">
        <div class="card-header"><div><h2>Activos y equipos</h2><p>{{ assets().length }} activo(s) registrados.</p></div></div>
        @if (loadingAssets()) { <div class="loading"><span class="spinner"></span> Cargando activos…</div> }
        @else if (!assets().length) { <app-empty-state symbol="▣" title="Sin activos" message="Registra los equipos e instalaciones sujetos a mantenimiento." /> }
        @else {
          <div class="table-wrap"><table class="table">
            <thead><tr><th>Activo</th><th>Categoría</th><th>Ubicación</th><th>Marca / modelo</th><th>Garantía</th><th>Estado</th><th style="width:70px"></th></tr></thead>
            <tbody>
              @for (asset of assets(); track asset.id) {
                <tr>
                  <td class="main-cell">{{ asset.name }}<div class="muted small">{{ asset.serialNumber || 'Sin serial' }}</div></td>
                  <td>{{ asset.category }}</td>
                  <td>{{ asset.location || '—' }}</td>
                  <td>{{ asset.brandModel || '—' }}</td>
                  <td>{{ asset.warrantyExpiration | date:'dd/MM/yyyy' }}</td>
                  <td><span class="badge" [class]="'badge ' + tone(asset.status)">{{ label(asset.status) }}</span></td>
                  <td>@if (auth.hasPermission('MAINTENANCE_MANAGE')) { <button type="button" class="btn-icon" title="Editar" (click)="openAsset(asset)">✎</button> }</td>
                </tr>
              }
            </tbody>
          </table></div>
        }
      </div>
    }

    @if (tab() === 'plans') {
      <div class="filter-bar">
        <div class="field"><label for="planAsset">Activo</label><select id="planAsset" name="planAsset" class="select" [(ngModel)]="planAssetId" (change)="loadPlans()"><option value="">Todos</option>@for (asset of assets(); track asset.id) { <option [value]="asset.id">{{ asset.name }}</option> }</select></div>
        <div class="field"><label for="planActive">Estado</label><select id="planActive" name="planActive" class="select" [(ngModel)]="planActive" (change)="loadPlans()"><option value="">Todos</option><option value="true">Activos</option><option value="false">Inactivos</option></select></div>
        <button type="button" class="btn btn-secondary" (click)="loadPlans()">Filtrar</button>
      </div>
      <div class="card">
        <div class="card-header"><div><h2>Planes preventivos</h2><p>{{ plans().length }} plan(es).</p></div></div>
        @if (loadingPlans()) { <div class="loading"><span class="spinner"></span> Cargando planes…</div> }
        @else if (!plans().length) { <app-empty-state symbol="↻" title="Sin planes" message="Crea planes para programar el mantenimiento preventivo de los activos." /> }
        @else {
          <div class="table-wrap"><table class="table">
            <thead><tr><th>Plan</th><th>Activo</th><th>Frecuencia</th><th>Próximo vencimiento</th><th>Proveedor</th><th>Estado</th></tr></thead>
            <tbody>
              @for (plan of plans(); track plan.id) {
                <tr>
                  <td class="main-cell">{{ plan.title }}<div class="muted small">{{ plan.description || 'Sin descripción' }}</div></td>
                  <td>{{ assetName(plan.assetId) }}</td>
                  <td>{{ plan.frequencyDays }} días</td>
                  <td>{{ plan.nextDueDate | date:'dd/MM/yyyy' }}</td>
                  <td>{{ supplierName(plan.assignedSupplierId) }}</td>
                  <td><span class="badge" [class]="plan.isActive ? 'badge success' : 'badge neutral'">{{ plan.isActive ? 'Activo' : 'Inactivo' }}</span></td>
                </tr>
              }
            </tbody>
          </table></div>
        }
      </div>
    }

    <app-modal [open]="orderOpen()" title="Nueva orden de trabajo" subtitle="Programa el trabajo y asigna un responsable." [wide]="true" (closed)="orderOpen.set(false)">
      @if (modalError()) { <div class="alert alert-error" style="margin-bottom:14px">{{ modalError() }}</div> }
      <div class="form-grid">
        <div class="field span-2"><label for="orderTitle">Título *</label><input id="orderTitle" name="orderTitle" class="input" [(ngModel)]="orderForm.title"></div>
        <div class="field span-2"><label for="orderDescription">Descripción *</label><textarea id="orderDescription" name="orderDescription" class="textarea" [(ngModel)]="orderForm.description"></textarea></div>
        <div class="field"><label for="orderType">Tipo</label><select id="orderType" name="orderType" class="select" [(ngModel)]="orderForm.type">@for (option of workOrderTypes; track option) { <option [value]="option">{{ label(option) }}</option> }</select></div>
        <div class="field"><label for="orderPriorityForm">Prioridad</label><select id="orderPriorityForm" name="orderPriorityForm" class="select" [(ngModel)]="orderForm.priority">@for (option of priorities; track option) { <option [value]="option">{{ label(option) }}</option> }</select></div>
        <div class="field"><label for="orderAssetForm">Activo</label><select id="orderAssetForm" name="orderAssetForm" class="select" [(ngModel)]="orderForm.assetId"><option value="">Sin activo</option>@for (asset of assets(); track asset.id) { <option [value]="asset.id">{{ asset.name }}</option> }</select></div>
        <div class="field"><label for="orderSupplier">Proveedor</label><select id="orderSupplier" name="orderSupplier" class="select" [(ngModel)]="orderForm.supplierId"><option value="">Sin proveedor</option>@for (supplier of suppliers(); track supplier.id) { <option [value]="supplier.id">{{ supplier.name }}</option> }</select></div>
        <div class="field"><label for="scheduledStart">Inicio programado</label><input id="scheduledStart" name="scheduledStart" type="datetime-local" class="input" [(ngModel)]="scheduledStart"></div>
        <div class="field"><label for="scheduledEnd">Fin programado</label><input id="scheduledEnd" name="scheduledEnd" type="datetime-local" class="input" [(ngModel)]="scheduledEnd"></div>
        <div class="field"><label for="estimatedCost">Costo estimado</label><input id="estimatedCost" name="estimatedCost" type="number" min="0" step="0.01" class="input" [(ngModel)]="orderForm.estimatedCost"></div>
      </div>
      <div class="form-actions"><button type="button" class="btn btn-secondary" (click)="orderOpen.set(false)">Cancelar</button><button type="button" class="btn btn-primary" [disabled]="saving()" (click)="createOrder()">Crear orden</button></div>
    </app-modal>

    <app-modal [open]="assetOpen()" [title]="editingAsset() ? 'Editar activo' : 'Nuevo activo'" subtitle="Datos técnicos y estado del equipo." [wide]="true" (closed)="assetOpen.set(false)">
      @if (modalError()) { <div class="alert alert-error" style="margin-bottom:14px">{{ modalError() }}</div> }
      <div class="form-grid">
        <div class="field"><label for="assetName">Nombre *</label><input id="assetName" name="assetName" class="input" [(ngModel)]="assetForm.name"></div>
        <div class="field"><label for="assetCategoryForm">Categoría *</label><input id="assetCategoryForm" name="assetCategoryForm" class="input" [(ngModel)]="assetForm.category"></div>
        <div class="field"><label for="assetLocation">Ubicación</label><input id="assetLocation" name="assetLocation" class="input" [(ngModel)]="assetForm.location"></div>
        <div class="field"><label for="assetSerial">Serial</label><input id="assetSerial" name="assetSerial" class="input" [(ngModel)]="assetForm.serialNumber"></div>
        <div class="field"><label for="assetBrand">Marca / modelo</label><input id="assetBrand" name="assetBrand" class="input" [(ngModel)]="assetForm.brandModel"></div>
        <div class="field"><label for="assetStatusForm">Estado</label><select id="assetStatusForm" name="assetStatusForm" class="select" [(ngModel)]="assetForm.status">@for (option of assetStatuses; track option) { <option [value]="option">{{ label(option) }}</option> }</select></div>
        <div class="field"><label for="installation">Instalación</label><input id="installation" name="installation" type="date" class="input" [(ngModel)]="installationDate"></div>
        <div class="field"><label for="warranty">Garantía hasta</label><input id="warranty" name="warranty" type="date" class="input" [(ngModel)]="warrantyDate"></div>
      </div>
      <div class="form-actions"><button type="button" class="btn btn-secondary" (click)="assetOpen.set(false)">Cancelar</button><button type="button" class="btn btn-primary" [disabled]="saving()" (click)="saveAsset()">Guardar activo</button></div>
    </app-modal>

    <app-modal [open]="planOpen()" title="Nuevo plan preventivo" subtitle="Define frecuencia y responsable." (closed)="planOpen.set(false)">
      @if (modalError()) { <div class="alert alert-error" style="margin-bottom:14px">{{ modalError() }}</div> }
      <div class="form-grid">
        <div class="field span-2"><label for="planTitle">Título *</label><input id="planTitle" name="planTitle" class="input" [(ngModel)]="planForm.title"></div>
        <div class="field span-2"><label for="planDescription">Descripción</label><textarea id="planDescription" name="planDescription" class="textarea" [(ngModel)]="planForm.description"></textarea></div>
        <div class="field"><label for="planAssetForm">Activo</label><select id="planAssetForm" name="planAssetForm" class="select" [(ngModel)]="planForm.assetId"><option value="">Sin activo</option>@for (asset of assets(); track asset.id) { <option [value]="asset.id">{{ asset.name }}</option> }</select></div>
        <div class="field"><label for="frequency">Frecuencia (días) *</label><input id="frequency" name="frequency" type="number" min="1" class="input" [(ngModel)]="planForm.frequencyDays"></div>
        <div class="field"><label for="nextDue">Próximo vencimiento</label><input id="nextDue" name="nextDue" type="date" class="input" [(ngModel)]="planNextDue"></div>
        <div class="field"><label for="planSupplier">Proveedor</label><select id="planSupplier" name="planSupplier" class="select" [(ngModel)]="planForm.assignedSupplierId"><option value="">Sin proveedor</option>@for (supplier of suppliers(); track supplier.id) { <option [value]="supplier.id">{{ supplier.name }}</option> }</select></div>
      </div>
      <div class="form-actions"><button type="button" class="btn btn-secondary" (click)="planOpen.set(false)">Cancelar</button><button type="button" class="btn btn-primary" [disabled]="saving()" (click)="createPlan()">Crear plan</button></div>
    </app-modal>

    <app-modal [open]="statusOpen()" title="Actualizar orden" [subtitle]="statusOrder()?.orderNumber || ''" [narrow]="true" (closed)="statusOpen.set(false)">
      <div class="field"><label for="workStatus">Estado</label><select id="workStatus" name="workStatus" class="select" [(ngModel)]="statusForm.status">@for (option of orderStatuses; track option) { <option [value]="option">{{ label(option) }}</option> }</select></div>
      <div class="field" style="margin-top:10px"><label for="actualCost">Costo real</label><input id="actualCost" name="actualCost" type="number" min="0" step="0.01" class="input" [(ngModel)]="statusForm.actualCost"></div>
      <div class="field" style="margin-top:10px"><label for="completionNotes">Notas de cierre</label><textarea id="completionNotes" name="completionNotes" class="textarea" [(ngModel)]="statusForm.completionNotes"></textarea></div>
      <div class="form-actions"><button type="button" class="btn btn-secondary" (click)="statusOpen.set(false)">Cancelar</button><button type="button" class="btn btn-primary" [disabled]="saving()" (click)="saveStatus()">Guardar</button></div>
    </app-modal>
  `
})
export class MaintenancePage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly tab = signal<MaintenanceTab>('orders');
  readonly orders = signal<WorkOrder[]>([]);
  readonly assets = signal<MaintenanceAsset[]>([]);
  readonly plans = signal<MaintenancePlan[]>([]);
  readonly suppliers = signal<Supplier[]>([]);
  readonly loadingOrders = signal(false);
  readonly loadingAssets = signal(false);
  readonly loadingPlans = signal(false);
  readonly saving = signal(false);
  readonly orderOpen = signal(false);
  readonly assetOpen = signal(false);
  readonly planOpen = signal(false);
  readonly statusOpen = signal(false);
  readonly statusOrder = signal<WorkOrder | null>(null);
  readonly editingAsset = signal<MaintenanceAsset | null>(null);
  readonly modalError = signal('');
  readonly ordersPage = signal(0);
  readonly ordersPages = signal(0);
  readonly ordersTotal = signal(0);
  readonly orderStatuses: WorkOrderStatus[] = ['PENDING', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'];
  readonly priorities = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];
  readonly workOrderTypes = ['PREVENTIVE', 'CORRECTIVE', 'EMERGENCY', 'IMPROVEMENT'];
  readonly assetStatuses = ['OPERATIONAL', 'DEGRADED', 'OUT_OF_SERVICE', 'RETIRED'];
  orderStatus = '';
  orderPriority = '';
  orderAssetId = '';
  assetCategory = '';
  assetStatus = '';
  planAssetId = '';
  planActive = '';
  scheduledStart = '';
  scheduledEnd = '';
  installationDate = '';
  warrantyDate = '';
  planNextDue = today();
  orderForm: CreateWorkOrderRequest = this.emptyOrder();
  assetForm: CreateAssetRequest = this.emptyAsset();
  planForm: CreatePlanRequest = { title: '', frequencyDays: 30, nextDueDate: today() };
  statusForm: UpdateWorkOrderStatusRequest = { status: 'IN_PROGRESS' };

  constructor() {
    void this.loadAssets();
    void this.loadPlans();
    void this.loadOrders(0);
    void this.loadSuppliers();
  }

  setTab(tab: MaintenanceTab): void {
    this.tab.set(tab);
    if (tab === 'assets' && !this.assets().length) void this.loadAssets();
    if (tab === 'plans' && !this.plans().length) void this.loadPlans();
    if (tab === 'orders' && !this.orders().length) void this.loadOrders(0);
  }

  async loadOrders(page = 0): Promise<void> {
    this.loadingOrders.set(true);
    try {
      const result = await firstValueFrom(this.api.get<PageResultWorkOrder>('/maintenance/work-orders', {
        status: this.orderStatus,
        priority: this.orderPriority,
        assetId: this.orderAssetId,
        page,
        size: 20
      }));
      this.orders.set(result.content ?? []);
      this.ordersPage.set(result.page ?? 0);
      this.ordersPages.set(result.totalPages ?? 0);
      this.ordersTotal.set(result.totalElements ?? 0);
    } finally {
      this.loadingOrders.set(false);
    }
  }

  clearOrderFilters(): void {
    this.orderStatus = '';
    this.orderPriority = '';
    this.orderAssetId = '';
    void this.loadOrders(0);
  }

  async loadAssets(): Promise<void> {
    this.loadingAssets.set(true);
    try {
      this.assets.set(await firstValueFrom(this.api.get<MaintenanceAsset[]>('/maintenance/assets', {
        category: this.assetCategory.trim(),
        status: this.assetStatus
      })));
    } finally {
      this.loadingAssets.set(false);
    }
  }

  async loadPlans(): Promise<void> {
    this.loadingPlans.set(true);
    try {
      this.plans.set(await firstValueFrom(this.api.get<MaintenancePlan[]>('/maintenance/plans', {
        assetId: this.planAssetId,
        active: this.planActive
      })));
    } finally {
      this.loadingPlans.set(false);
    }
  }

  async loadSuppliers(): Promise<void> {
    try {
      const result = await firstValueFrom(this.api.get<PageResultSupplier>('/suppliers', { active: true, size: 200 }));
      this.suppliers.set(result.content ?? []);
    } catch {
      // El selector se reintenta al abrir un formulario.
    }
  }

  assetName(id: string | undefined): string {
    return this.assets().find((asset) => asset.id === id)?.name ?? '—';
  }

  supplierName(id: string | undefined): string {
    return this.suppliers().find((supplier) => supplier.id === id)?.name ?? '—';
  }

  openAction(): void {
    if (this.tab() === 'orders') this.openOrder();
    else if (this.tab() === 'assets') this.openAsset();
    else this.openPlan();
  }

  openOrder(): void {
    this.orderForm = this.emptyOrder();
    this.scheduledStart = '';
    this.scheduledEnd = '';
    this.modalError.set('');
    this.orderOpen.set(true);
  }

  async createOrder(): Promise<void> {
    this.modalError.set('');
    if (!this.orderForm.title.trim() || !this.orderForm.description.trim()) {
      this.modalError.set('El título y la descripción son obligatorios.');
      return;
    }
    this.saving.set(true);
    try {
      await firstValueFrom(this.api.post<WorkOrder>('/maintenance/work-orders', {
        ...this.orderForm,
        title: this.orderForm.title.trim(),
        description: this.orderForm.description.trim(),
        scheduledStart: toIsoDateTime(this.scheduledStart),
        scheduledEnd: toIsoDateTime(this.scheduledEnd)
      }));
      this.toast.success('Orden de trabajo creada.');
      this.orderOpen.set(false);
      await this.loadOrders(0);
    } catch (error) {
      this.modalError.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  openAsset(asset?: MaintenanceAsset): void {
    this.editingAsset.set(asset ?? null);
    this.assetForm = asset ? {
      name: asset.name ?? '',
      category: asset.category ?? '',
      location: asset.location,
      serialNumber: asset.serialNumber,
      brandModel: asset.brandModel,
      installationDate: asset.installationDate,
      warrantyExpiration: asset.warrantyExpiration,
      status: (asset.status as CreateAssetRequest['status']) ?? 'OPERATIONAL'
    } : this.emptyAsset();
    this.installationDate = asset?.installationDate ?? '';
    this.warrantyDate = asset?.warrantyExpiration ?? '';
    this.modalError.set('');
    this.assetOpen.set(true);
  }

  async saveAsset(): Promise<void> {
    this.modalError.set('');
    if (!this.assetForm.name.trim() || !this.assetForm.category.trim()) {
      this.modalError.set('El nombre y la categoría del activo son obligatorios.');
      return;
    }
    this.saving.set(true);
    try {
      const body: CreateAssetRequest = {
        ...this.assetForm,
        name: this.assetForm.name.trim(),
        category: this.assetForm.category.trim(),
        installationDate: this.installationDate || undefined,
        warrantyExpiration: this.warrantyDate || undefined
      };
      const editing = this.editingAsset();
      if (editing?.id) {
        await firstValueFrom(this.api.put<MaintenanceAsset>(`/maintenance/assets/${editing.id}`, body));
        this.toast.success('Activo actualizado.');
      } else {
        await firstValueFrom(this.api.post<MaintenanceAsset>('/maintenance/assets', body));
        this.toast.success('Activo registrado.');
      }
      this.assetOpen.set(false);
      await this.loadAssets();
    } catch (error) {
      this.modalError.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  openPlan(): void {
    this.planForm = { title: '', description: '', frequencyDays: 30, nextDueDate: today(), assetId: this.assets()[0]?.id, assignedSupplierId: undefined };
    this.planNextDue = today();
    this.modalError.set('');
    this.planOpen.set(true);
  }

  async createPlan(): Promise<void> {
    this.modalError.set('');
    if (!this.planForm.title.trim() || !this.planForm.frequencyDays) {
      this.modalError.set('Indica el título y la frecuencia del plan.');
      return;
    }
    this.saving.set(true);
    try {
      await firstValueFrom(this.api.post<MaintenancePlan>('/maintenance/plans', {
        ...this.planForm,
        title: this.planForm.title.trim(),
        nextDueDate: this.planNextDue || undefined
      }));
      this.toast.success('Plan preventivo creado.');
      this.planOpen.set(false);
      await this.loadPlans();
    } catch (error) {
      this.modalError.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  openStatus(order: WorkOrder): void {
    this.statusOrder.set(order);
    this.statusForm = {
      status: (order.status as WorkOrderStatus) || 'IN_PROGRESS',
      actualCost: order.actualCost ?? order.estimatedCost,
      completionNotes: order.completionNotes
    };
    this.statusOpen.set(true);
  }

  async saveStatus(): Promise<void> {
    const order = this.statusOrder();
    if (!order?.id) return;
    this.saving.set(true);
    try {
      await firstValueFrom(this.api.patch<WorkOrder>(`/maintenance/work-orders/${order.id}/status`, this.statusForm));
      this.toast.success('Orden actualizada.');
      this.statusOpen.set(false);
      await this.loadOrders(this.ordersPage());
    } finally {
      this.saving.set(false);
    }
  }

  private emptyOrder(): CreateWorkOrderRequest {
    return { title: '', description: '', type: 'CORRECTIVE', priority: 'MEDIUM', assetId: undefined, supplierId: undefined, estimatedCost: undefined, scheduledStart: undefined, scheduledEnd: undefined };
  }

  private emptyAsset(): CreateAssetRequest {
    return { name: '', category: '', location: '', serialNumber: '', brandModel: '', status: 'OPERATIONAL' };
  }
}
