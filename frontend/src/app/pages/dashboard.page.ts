import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { TenantContextService } from '../core/tenant-context.service';
import { MoneyPipe } from '../core/format.pipes';
import { label, roleLabel } from '../core/labels';
import {
  AuditLogView,
  Communication,
  Company,
  MyUnit,
  PageResultAuditLogView,
  PageResultCommunication,
  PageResultTenantView,
  PageResultWorkOrder,
  Parcel,
  PortfolioView,
  PqrSlaMetrics,
  ResidentCommunicationItem,
  SecurityAlert,
  SecuritySummary,
  Statement,
  TenantView,
  WorkOrder
} from '../core/models';
import { PageHeaderComponent } from '../shared/ui.components';

@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [RouterLink, DatePipe, MoneyPipe, PageHeaderComponent],
  template: `
    @if (platformOnly()) {
      <app-page-header title="Plataforma" subtitle="Estado general de las empresas y copropiedades administradas." />
      <div class="grid grid-4">
        <div class="stat accent"><div class="stat-label">Copropiedades</div><div class="stat-value">{{ tenantsCount() }}</div><div class="stat-hint">{{ activeTenants() }} activas</div></div>
        <div class="stat"><div class="stat-label">Empresas</div><div class="stat-value">{{ companies().length }}</div><div class="stat-hint">Registradas en la plataforma</div></div>
        <div class="stat"><div class="stat-label">Copropiedades activas</div><div class="stat-value">{{ activeTenants() }}</div><div class="stat-hint">Con operación habilitada</div></div>
        <div class="stat"><div class="stat-label">Accesos recientes</div><div class="stat-value">{{ platformAudit().length }}</div><div class="stat-hint">Últimos eventos globales</div></div>
      </div>
      <div class="grid grid-2" style="margin-top:18px">
        <div class="card">
          <div class="card-header"><div><h2>Copropiedades</h2><p>Últimas copropiedades registradas.</p></div><a class="btn btn-secondary btn-sm" routerLink="/app/plataforma/copropiedades">Ver todas</a></div>
          <div class="table-wrap">
            <table class="table">
              <thead><tr><th>Nombre</th><th>Ciudad</th><th>Estado</th></tr></thead>
              <tbody>
                @for (tenant of tenants().slice(0, 6); track tenant.id) {
                  <tr><td class="main-cell">{{ tenant.name }}</td><td>{{ tenant.city || '—' }}</td><td><span class="badge" [class]="'badge ' + (tenant.status === 'ACTIVE' ? 'success' : 'neutral')">{{ label(tenant.status) }}</span></td></tr>
                } @empty { <tr><td colspan="3" class="table-empty">Aún no hay copropiedades.</td></tr> }
              </tbody>
            </table>
          </div>
        </div>
        <div class="card">
          <div class="card-header"><div><h2>Auditoría global</h2><p>Eventos recientes de la plataforma.</p></div><a class="btn btn-secondary btn-sm" routerLink="/app/plataforma/auditoria">Abrir auditoría</a></div>
          <div class="card-body">
            <div class="timeline">
              @for (event of platformAudit(); track event.id) {
                <div class="timeline-item">
                  <span class="timeline-dot"></span>
                  <div class="timeline-content">
                    <div style="font-size:13px;font-weight:600">{{ event.action }}</div>
                    <div class="muted small">{{ event.actorEmail || 'Sistema' }} · {{ event.occurredAt | date:'dd/MM/yyyy HH:mm' }}</div>
                  </div>
                </div>
              } @empty { <p class="muted small">Sin eventos recientes.</p> }
            </div>
          </div>
        </div>
      </div>
    } @else if (auth.isPorter()) {
      <app-page-header title="Portería" subtitle="Resumen operativo de acceso, visitantes y paquetería.">
        <a class="btn btn-primary" routerLink="/app/porteria/control">Control de acceso</a>
        <a class="btn btn-secondary" routerLink="/app/porteria/visitantes">Registrar visitante</a>
      </app-page-header>
      @if (loading()) { <div class="loading"><span class="spinner"></span> Cargando resumen…</div> }
      @else {
        <div class="grid grid-4">
          <div class="stat accent"><div class="stat-label">Vehículos dentro</div><div class="stat-value">{{ security()?.vehiclesInside ?? 0 }}</div><div class="stat-hint">{{ security()?.vehicleEntriesToday ?? 0 }} entradas hoy</div></div>
          <div class="stat"><div class="stat-label">Visitantes dentro</div><div class="stat-value">{{ security()?.visitorsInside ?? 0 }}</div><div class="stat-hint">{{ security()?.pendingVisitRequests ?? 0 }} solicitudes pendientes</div></div>
          <div class="stat warn"><div class="stat-label">Paquetes pendientes</div><div class="stat-value">{{ security()?.packagesPending ?? 0 }}</div><div class="stat-hint">Por entregar a residentes</div></div>
          <div class="stat danger"><div class="stat-label">Alertas abiertas</div><div class="stat-value">{{ security()?.openAlerts ?? 0 }}</div><div class="stat-hint">{{ security()?.openIncidents ?? 0 }} novedades abiertas</div></div>
        </div>
        <div class="grid grid-2" style="margin-top:18px">
          <div class="card">
            <div class="card-header"><div><h2>Accesos rápidos</h2><p>Operaciones frecuentes del turno.</p></div></div>
            <div class="card-body">
              <div class="grid grid-2">
                <a class="btn btn-secondary" style="justify-content:flex-start;min-height:52px" routerLink="/app/porteria/control">Registrar entrada o salida</a>
                <a class="btn btn-secondary" style="justify-content:flex-start;min-height:52px" routerLink="/app/porteria/visitantes">Validar QR o visitante</a>
                <a class="btn btn-secondary" style="justify-content:flex-start;min-height:52px" routerLink="/app/porteria/paquetes">Recibir paquete</a>
                <a class="btn btn-secondary" style="justify-content:flex-start;min-height:52px" routerLink="/app/porteria/novedades">Reportar novedad</a>
              </div>
            </div>
          </div>
          <div class="card">
            <div class="card-header"><div><h2>Alertas de seguridad</h2><p>Eventos que requieren atención.</p></div><a class="btn btn-secondary btn-sm" routerLink="/app/porteria/control">Ver control</a></div>
            <div class="card-body">
              @for (alert of alerts(); track alert.id) {
                <div class="row between" style="padding:9px 0;border-bottom:1px solid #eef2f7">
                  <div><div style="font-weight:600;font-size:13px">{{ alert.message }}</div><div class="muted small">{{ alert.plate || 'Sin placa' }} · {{ alert.createdAt | date:'dd/MM HH:mm' }}</div></div>
                  <span class="badge danger">Abierta</span>
                </div>
              } @empty { <p class="muted small">No hay alertas abiertas.</p> }
            </div>
          </div>
        </div>
      }
    } @else if (auth.isResident()) {
      <app-page-header title="Mi copropiedad" subtitle="Tu información, obligaciones y novedades de la comunidad.">
        @if (auth.hasPermission('FINANCE_VIEW_OWN')) { <a class="btn btn-primary" routerLink="/app/residente/estado-cuenta">Ver estado de cuenta</a> }
        @if (auth.hasPermission('VISITORS_VIEW_OWN')) { <a class="btn btn-secondary" routerLink="/app/residente/visitas">Invitar visita</a> }
      </app-page-header>
      @if (loading()) { <div class="loading"><span class="spinner"></span> Cargando tu información…</div> }
      @else {
        <div class="grid grid-4">
          @if (auth.hasPermission('FINANCE_VIEW_OWN')) {
            <div class="stat accent"><div class="stat-label">Saldo pendiente</div><div class="stat-value">{{ statement()?.closingBalance | money }}</div><div class="stat-hint">Vence {{ statement()?.nextDueDate || '—' }}</div></div>
            <div class="stat warn"><div class="stat-label">Valor vencido</div><div class="stat-value">{{ statement()?.overdueAmount | money }}</div><div class="stat-hint">Obligaciones vencidas</div></div>
            <div class="stat"><div class="stat-label">Saldo a favor</div><div class="stat-value">{{ statement()?.creditBalance | money }}</div><div class="stat-hint">Disponible para futuras cuotas</div></div>
          }
          @if (auth.hasPermission('PACKAGES_VIEW_OWN')) {
            <div class="stat"><div class="stat-label">Paquetes pendientes</div><div class="stat-value">{{ pendingPackages() }}</div><div class="stat-hint">En portería para ti</div></div>
          }
          <div class="stat"><div class="stat-label">Inmuebles</div><div class="stat-value">{{ units().length }}</div><div class="stat-hint">Asociados a tu usuario</div></div>
        </div>

        <div class="grid grid-2" style="margin-top:18px">
          @if (auth.hasPermission('UNITS_VIEW_OWN')) {
            <div class="card">
              <div class="card-header"><div><h2>Mis inmuebles</h2><p>Inmuebles y roles asociados a tu usuario.</p></div></div>
              <div class="card-body">
                @for (unit of units(); track unit.unitId) {
                  <div class="row between" style="padding:10px 0;border-bottom:1px solid #eef2f7">
                    <div>
                      <div style="font-weight:650">{{ unit.identifier }}</div>
                      <div class="muted small">{{ unit.tower || 'Sin torre' }} · {{ label(unit.unitType) }}</div>
                    </div>
                    <div class="tag-list">
                      @for (relation of unit.relations; track relation) { <span class="chip">{{ label(relation) }}</span> }
                    </div>
                  </div>
                } @empty { <p class="muted small">No tienes inmuebles asociados. Contacta a la administración.</p> }
              </div>
            </div>
          }
          @if (auth.hasPermission('COMMUNICATIONS_VIEW')) {
            <div class="card">
              <div class="card-header"><div><h2>Comunicados recientes</h2><p>Novedades publicadas por la administración.</p></div><a class="btn btn-secondary btn-sm" routerLink="/app/residente/comunicados">Ver todos</a></div>
              <div class="card-body">
                @for (item of communications(); track item.communication?.id) {
                  <div style="padding:9px 0;border-bottom:1px solid #eef2f7">
                    <div class="row between">
                      <div style="font-weight:600;font-size:13px">{{ item.communication?.title }}</div>
                      @if (!item.isRead) { <span class="badge info">Nuevo</span> }
                    </div>
                    <div class="muted small" style="margin-top:3px;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden">{{ item.communication?.summary || item.communication?.content }}</div>
                  </div>
                } @empty { <p class="muted small">No hay comunicados recientes.</p> }
              </div>
            </div>
          }
        </div>

        <div class="card" style="margin-top:18px">
          <div class="card-header"><div><h2>Accesos del portal</h2><p>Servicios disponibles para tu rol.</p></div></div>
          <div class="card-body">
            <div class="grid grid-4">
              @if (auth.hasPermission('RESERVATIONS_VIEW_OWN')) { <a class="btn btn-secondary" style="min-height:54px" routerLink="/app/residente/reservas">Reservar zona común</a> }
              @if (auth.hasPermission('PQR_VIEW_OWN')) { <a class="btn btn-secondary" style="min-height:54px" routerLink="/app/residente/pqrs">Radicar una PQRS</a> }
              @if (auth.hasPermission('CERTIFICATES_VIEW_OWN')) { <a class="btn btn-secondary" style="min-height:54px" routerLink="/app/residente/certificados">Descargar certificados</a> }
              @if (auth.hasPermission('ASSEMBLIES_VIEW')) { <a class="btn btn-secondary" style="min-height:54px" routerLink="/app/residente/asambleas">Asambleas y votaciones</a> }
              @if (auth.hasPermission('VISITORS_VIEW_OWN')) { <a class="btn btn-secondary" style="min-height:54px" routerLink="/app/residente/visitas">Gestionar visitas</a> }
              @if (auth.hasPermission('DOCUMENTS_VIEW')) { <a class="btn btn-secondary" style="min-height:54px" routerLink="/app/residente/documentos">Consultar documentos</a> }
              @if (auth.hasPermission('PACKAGES_VIEW_OWN')) { <a class="btn btn-secondary" style="min-height:54px" routerLink="/app/residente/paquetes">Mis paquetes</a> }
              @if (auth.hasPermission('FINANCE_VIEW_OWN')) { <a class="btn btn-secondary" style="min-height:54px" routerLink="/app/residente/estado-cuenta">Estado de cuenta</a> }
            </div>
          </div>
        </div>
      }
    } @else {
      <app-page-header [title]="'Hola, ' + (auth.user()?.fullName || 'bienvenido')" [subtitle]="'Resumen de ' + (tenantContext.tenant()?.name || 'la copropiedad') + ' · ' + roleLabel(auth.activeRole())">
        @if (auth.hasPermission('FINANCE_VIEW')) { <a class="btn btn-primary" routerLink="/app/cartera">Ver cartera</a> }
        @if (auth.hasPermission('PQR_VIEW')) { <a class="btn btn-secondary" routerLink="/app/pqrs">PQRS</a> }
      </app-page-header>
      @if (loading()) { <div class="loading"><span class="spinner"></span> Cargando indicadores…</div> }
      @else {
        @if (auth.hasPermission('VEHICLES_VIEW')) {
          <div class="grid grid-4">
            <div class="stat accent"><div class="stat-label">Vehículos dentro</div><div class="stat-value">{{ security()?.vehiclesInside ?? 0 }}</div><div class="stat-hint">{{ security()?.vehicleEntriesToday ?? 0 }} entradas hoy</div></div>
            <div class="stat"><div class="stat-label">Visitantes dentro</div><div class="stat-value">{{ security()?.visitorsInside ?? 0 }}</div><div class="stat-hint">{{ security()?.pendingVisitRequests ?? 0 }} por autorizar</div></div>
            <div class="stat warn"><div class="stat-label">Paquetes pendientes</div><div class="stat-value">{{ security()?.packagesPending ?? 0 }}</div><div class="stat-hint">En portería</div></div>
            <div class="stat danger"><div class="stat-label">Alertas abiertas</div><div class="stat-value">{{ security()?.openAlerts ?? 0 }}</div><div class="stat-hint">{{ security()?.openIncidents ?? 0 }} novedades</div></div>
          </div>
        }
        @if (auth.hasPermission('FINANCE_VIEW')) {
          <div class="grid grid-4" style="margin-top:18px">
            <div class="stat accent"><div class="stat-label">Cartera pendiente</div><div class="stat-value">{{ portfolio()?.totalOutstanding | money }}</div><div class="stat-hint">{{ portfolio()?.units?.totalElements ?? 0 }} inmuebles con saldo</div></div>
            <div class="stat danger"><div class="stat-label">Cartera vencida</div><div class="stat-value">{{ portfolio()?.totalOverdue | money }}</div><div class="stat-hint">Obligaciones vencidas</div></div>
            <div class="stat"><div class="stat-label">Saldo a favor</div><div class="stat-value">{{ portfolio()?.totalCredit | money }}</div><div class="stat-hint">Crédito de residentes</div></div>
            <div class="stat"><div class="stat-label">PQRS abiertas</div><div class="stat-value">{{ pqr()?.totalOpen ?? 0 }}</div><div class="stat-hint">{{ pqr()?.overdueCount ?? 0 }} fuera de SLA</div></div>
          </div>
        }
        <div class="grid grid-2" style="margin-top:18px">
          <div class="card">
            <div class="card-header"><div><h2>Accesos rápidos</h2><p>Gestión diaria de la copropiedad.</p></div></div>
            <div class="card-body">
              <div class="grid grid-3">
                @if (auth.hasPermission('PROPERTIES_VIEW')) { <a class="btn btn-secondary" style="min-height:50px" routerLink="/app/propiedades">Inmuebles</a> }
                @if (auth.hasPermission('RESIDENTES_VIEW')) { <a class="btn btn-secondary" style="min-height:50px" routerLink="/app/personas">Personas</a> }
                @if (auth.hasPermission('VEHICLES_VIEW')) { <a class="btn btn-secondary" style="min-height:50px" routerLink="/app/vehiculos">Vehículos</a> }
                @if (auth.hasPermission('FINANCE_VIEW')) { <a class="btn btn-secondary" style="min-height:50px" routerLink="/app/pagos">Registrar pago</a> }
                @if (auth.hasPermission('RESERVATIONS_VIEW')) { <a class="btn btn-secondary" style="min-height:50px" routerLink="/app/reservas">Reservas</a> }
                @if (auth.hasPermission('COMMUNICATIONS_MANAGE')) { <a class="btn btn-secondary" style="min-height:50px" routerLink="/app/comunicados">Comunicados</a> }
                @if (auth.hasPermission('REPORTS_VIEW')) { <a class="btn btn-secondary" style="min-height:50px" routerLink="/app/reportes">Reportes</a> }
                @if (auth.hasPermission('ASSEMBLIES_MANAGE')) { <a class="btn btn-secondary" style="min-height:50px" routerLink="/app/asambleas">Asambleas</a> }
                @if (auth.hasPermission('MAINTENANCE_VIEW')) { <a class="btn btn-secondary" style="min-height:50px" routerLink="/app/mantenimiento">Mantenimiento</a> }
              </div>
            </div>
          </div>
          <div class="card">
            <div class="card-header"><div><h2>Actividad reciente</h2><p>Últimos eventos auditados.</p></div>@if (auth.hasPermission('AUDIT_VIEW')) { <a class="btn btn-secondary btn-sm" routerLink="/app/auditoria">Ver auditoría</a> }</div>
            <div class="card-body">
              <div class="timeline">
                @for (event of audit(); track event.id) {
                  <div class="timeline-item">
                    <span class="timeline-dot"></span>
                    <div class="timeline-content">
                      <div style="font-size:13px;font-weight:600">{{ event.action }}</div>
                      <div class="muted small">{{ event.actorEmail || 'Sistema' }} · {{ event.entity || '—' }} · {{ event.occurredAt | date:'dd/MM HH:mm' }}</div>
                    </div>
                  </div>
                } @empty { <p class="muted small">No hay eventos recientes para mostrar.</p> }
              </div>
            </div>
          </div>
        </div>
        @if (auth.hasPermission('MAINTENANCE_VIEW') && workOrders().length) {
          <div class="card" style="margin-top:18px">
            <div class="card-header"><div><h2>Órdenes de mantenimiento pendientes</h2><p>Trabajos por atender.</p></div><a class="btn btn-secondary btn-sm" routerLink="/app/mantenimiento">Ver mantenimiento</a></div>
            <div class="table-wrap">
              <table class="table">
                <thead><tr><th>Orden</th><th>Título</th><th>Prioridad</th><th>Estado</th></tr></thead>
                <tbody>
                  @for (order of workOrders(); track order.id) {
                    <tr><td class="mono">{{ order.orderNumber }}</td><td class="main-cell">{{ order.title }}</td><td>{{ label(order.priority) }}</td><td><span class="badge warning">{{ label(order.status) }}</span></td></tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        }
      }
    }
  `
})
export class DashboardPage {
  readonly auth = inject(AuthService);
  readonly tenantContext = inject(TenantContextService);
  private readonly api = inject(ApiService);
  readonly label = label;
  readonly roleLabel = roleLabel;
  readonly loading = signal(true);
  readonly security = signal<SecuritySummary | null>(null);
  readonly portfolio = signal<PortfolioView | null>(null);
  readonly pqr = signal<PqrSlaMetrics | null>(null);
  readonly audit = signal<AuditLogView[]>([]);
  readonly alerts = signal<SecurityAlert[]>([]);
  readonly units = signal<MyUnit[]>([]);
  readonly statement = signal<Statement | null>(null);
  readonly packages = signal<Parcel[]>([]);
  readonly communications = signal<ResidentCommunicationItem[]>([]);
  readonly tenants = signal<TenantView[]>([]);
  readonly companies = signal<Company[]>([]);
  readonly platformAudit = signal<AuditLogView[]>([]);
  readonly workOrders = signal<WorkOrder[]>([]);
  readonly platformOnly = computed(() => this.auth.user()?.platformRole === 'SUPER_ADMIN_PLATFORM' && !this.auth.hasTenant());
  readonly tenantsCount = computed(() => this.tenants().length);
  readonly activeTenants = computed(() => this.tenants().filter((tenant) => tenant.status === 'ACTIVE').length);
  readonly pendingPackages = computed(() => this.packages().filter((item) => item.status !== 'DELIVERED' && item.status !== 'RETURNED').length);

  constructor() {
    void this.loadData();
  }

  private async loadData(): Promise<void> {
    this.loading.set(true);
    try {
      if (this.platformOnly()) {
        const [tenants, companies, audit] = await Promise.all([
          this.safe(this.api.get<PageResultTenantView>('/platform/tenants', { size: 100 })),
          this.safe(this.api.get<Company[]>('/platform/companies')),
          this.safe(this.api.get<PageResultAuditLogView>('/platform/audit', { size: 6 }))
        ]);
        this.tenants.set(tenants?.content ?? []);
        this.companies.set(companies ?? []);
        this.platformAudit.set(audit?.content ?? []);
      } else if (this.auth.isPorter()) {
        const [security, alerts] = await Promise.all([
          this.safe(this.api.get<SecuritySummary>('/security/summary')),
          this.safe(this.api.get<{ content: SecurityAlert[] }>('/access/alerts', { onlyOpen: true, size: 6 }))
        ]);
        this.security.set(security);
        this.alerts.set(alerts?.content ?? []);
      } else if (this.auth.isResident()) {
        const units = this.auth.hasPermission('UNITS_VIEW_OWN', 'FINANCE_VIEW_OWN', 'PACKAGES_VIEW_OWN', 'COMMUNICATIONS_VIEW')
          ? await this.safe(this.api.get<MyUnit[]>('/my/units'))
          : null;
        this.units.set(units ?? []);
        const firstUnit = units?.[0]?.unitId;
        const statement = firstUnit && this.auth.hasPermission('FINANCE_VIEW_OWN')
          ? await this.safe(this.api.get<Statement>('/my/statement', { unitId: firstUnit }))
          : null;
        const [packages, communications] = await Promise.all([
          this.auth.hasPermission('PACKAGES_VIEW_OWN') ? this.safe(this.api.get<Parcel[]>('/my/packages')) : Promise.resolve(null),
          this.auth.hasPermission('COMMUNICATIONS_VIEW') ? this.safe(this.api.get<{ content: ResidentCommunicationItem[] }>('/my/communications', { size: 4 })) : Promise.resolve(null)
        ]);
        this.statement.set(statement);
        this.packages.set(packages ?? []);
        this.communications.set(communications?.content ?? []);
      } else {
        this.security.set(this.auth.hasPermission('VEHICLES_VIEW')
          ? await this.safe(this.api.get<SecuritySummary>('/security/summary'))
          : null);
        this.portfolio.set(this.auth.hasPermission('FINANCE_VIEW')
          ? await this.safe(this.api.get<PortfolioView>('/billing/portfolio', { size: 1 }))
          : null);
        this.pqr.set(this.auth.hasPermission('PQR_VIEW')
          ? await this.safe(this.api.get<PqrSlaMetrics>('/pqrs/metrics/sla'))
          : null);
        this.audit.set(this.auth.hasPermission('AUDIT_VIEW')
          ? (await this.safe(this.api.get<PageResultAuditLogView>('/audit', { size: 6 })))?.content ?? []
          : []);
        this.workOrders.set(this.auth.hasPermission('MAINTENANCE_VIEW')
          ? (await this.safe(this.api.get<PageResultWorkOrder>('/maintenance/work-orders', { status: 'PENDING', size: 5 })))?.content ?? []
          : []);
      }
    } finally {
      this.loading.set(false);
    }
  }

  private async safe<T>(source: Observable<T>): Promise<T | null> {
    try {
      return await firstValueFrom(source);
    } catch {
      return null;
    }
  }
}
