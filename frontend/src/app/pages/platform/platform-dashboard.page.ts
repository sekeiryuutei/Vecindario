import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { label, statusTone } from '../../core/labels';
import {
  AuditLogView,
  Company,
  PageResultAuditLogView,
  PageResultTenantView,
  TenantView
} from '../../core/models';
import { EmptyStateComponent, PageHeaderComponent } from '../../shared/ui.components';

interface FeatureStat {
  key: string;
  label: string;
  enabled: number;
  total: number;
}

@Component({
  selector: 'app-platform-dashboard-page',
  standalone: true,
  imports: [DatePipe, RouterLink, PageHeaderComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Plataforma" subtitle="Estado general de las empresas administradoras y las copropiedades de la plataforma.">
      <a class="btn btn-secondary" routerLink="/app/plataforma/empresas">Empresas</a>
      <a class="btn btn-primary" routerLink="/app/plataforma/copropiedades">Copropiedades</a>
    </app-page-header>

    @if (loading()) {
      <div class="loading"><span class="spinner"></span> Cargando indicadores de la plataforma…</div>
    } @else {
      <div class="grid grid-4">
        <div class="stat accent"><div class="stat-label">Copropiedades</div><div class="stat-value">{{ tenants().length }}</div><div class="stat-hint">{{ activeTenants() }} activas</div></div>
        <div class="stat"><div class="stat-label">Empresas</div><div class="stat-value">{{ companies().length }}</div><div class="stat-hint">Administradoras registradas</div></div>
        <div class="stat warn"><div class="stat-label">En aprovisionamiento</div><div class="stat-value">{{ provisioningTenants() }}</div><div class="stat-hint">Procesos pendientes</div></div>
        <div class="stat"><div class="stat-label">Eventos auditados</div><div class="stat-value">{{ audit().length }}</div><div class="stat-hint">Últimos eventos globales</div></div>
      </div>

      <div class="grid grid-2" style="margin-top:18px">
        <div class="card">
          <div class="card-header"><div><h2>Copropiedades recientes</h2><p>Últimas copropiedades registradas en la plataforma.</p></div><a class="btn btn-secondary btn-sm" routerLink="/app/plataforma/copropiedades">Ver todas</a></div>
          @if (!tenants().length) {
            <app-empty-state symbol="⌂" title="Sin copropiedades" message="Crea la primera copropiedad desde la gestión de copropiedades." />
          } @else {
            <div class="table-wrap">
              <table class="table">
                <thead><tr><th>Nombre</th><th>Ciudad</th><th>Contacto</th><th>Estado</th></tr></thead>
                <tbody>
                  @for (tenant of tenants().slice(0, 8); track tenant.id) {
                    <tr>
                      <td><div class="main-cell">{{ tenant.name }}</div><div class="muted small">{{ tenant.slug }}</div></td>
                      <td>{{ tenant.city || '—' }}</td>
                      <td><div>{{ tenant.email || '—' }}</div><div class="muted small">{{ tenant.phone || 'Sin teléfono' }}</div></td>
                      <td><span class="badge" [class]="'badge ' + tone(tenant.status)">{{ label(tenant.status) }}</span></td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </div>

        <div class="card">
          <div class="card-header"><div><h2>Funcionalidades</h2><p>Adopción de los módulos opcionales en las copropiedades.</p></div></div>
          <div class="card-body">
            <div class="stack" style="gap:12px">
              @for (feature of featureStats(); track feature.key) {
                <div>
                  <div class="row between" style="margin-bottom:5px">
                    <span style="font-size:13px;font-weight:600">{{ feature.label }}</span>
                    <span class="muted small">{{ feature.enabled }} de {{ feature.total }}</span>
                  </div>
                  <div class="progress"><span [style.width.%]="feature.total ? (feature.enabled * 100 / feature.total) : 0"></span></div>
                </div>
              }
            </div>
          </div>
        </div>
      </div>

      <div class="grid grid-2" style="margin-top:18px">
        <div class="card">
          <div class="card-header"><div><h2>Actividad reciente</h2><p>Eventos globales registrados por la plataforma.</p></div><a class="btn btn-secondary btn-sm" routerLink="/app/plataforma/auditoria">Abrir auditoría</a></div>
          <div class="card-body">
            <div class="timeline">
              @for (event of audit(); track event.id) {
                <div class="timeline-item">
                  <span class="timeline-dot"></span>
                  <div class="timeline-content">
                    <div class="row between">
                      <strong style="font-size:13px">{{ event.action }}</strong>
                      <span class="muted small">{{ event.occurredAt | date:'dd/MM/yyyy HH:mm' }}</span>
                    </div>
                    <div class="muted small">{{ event.actorEmail || 'Sistema' }} · {{ event.entity || '—' }} · {{ event.result || '—' }}</div>
                  </div>
                </div>
              } @empty { <p class="muted small">No hay eventos recientes.</p> }
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-header"><div><h2>Empresas</h2><p>Administradoras habilitadas para operar copropiedades.</p></div><a class="btn btn-secondary btn-sm" routerLink="/app/plataforma/empresas">Gestionar</a></div>
          @if (!companies().length) {
            <app-empty-state symbol="▥" title="Sin empresas" message="Registra la primera empresa administradora para crear copropiedades." />
          } @else {
            <div class="table-wrap">
              <table class="table">
                <thead><tr><th>Empresa</th><th>NIT</th><th>Creada</th></tr></thead>
                <tbody>
                  @for (company of companies().slice(0, 8); track company.id) {
                    <tr><td class="main-cell">{{ company.name }}</td><td class="mono">{{ company.nit || '—' }}</td><td>{{ company.createdAt | date:'dd/MM/yyyy' }}</td></tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </div>
      </div>
    }
  `
})
export class PlatformDashboardPage {
  private readonly api = inject(ApiService);
  readonly label = label;
  readonly tone = statusTone;
  readonly tenants = signal<TenantView[]>([]);
  readonly companies = signal<Company[]>([]);
  readonly audit = signal<AuditLogView[]>([]);
  readonly loading = signal(true);
  readonly activeTenants = computed(() => this.tenants().filter((tenant) => tenant.status === 'ACTIVE').length);
  readonly provisioningTenants = computed(() => this.tenants().filter((tenant) => tenant.status === 'PROVISIONING').length);
  readonly featureStats = computed<FeatureStat[]>(() => {
    const tenants = this.tenants();
    return [
      { key: 'ONLINE_PAYMENTS', label: label('ONLINE_PAYMENTS') },
      { key: 'WHATSAPP', label: label('WHATSAPP') },
      { key: 'CAMERAS', label: label('CAMERAS') },
      { key: 'LPR', label: label('LPR') }
    ].map((feature) => ({
      ...feature,
      enabled: tenants.filter((tenant) => tenant.features?.[feature.key]).length,
      total: tenants.length
    }));
  });

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    try {
      const [tenants, companies, audit] = await Promise.all([
        this.safe(this.api.get<PageResultTenantView>('/platform/tenants', { page: 0, size: 100 })),
        this.safe(this.api.get<Company[]>('/platform/companies')),
        this.safe(this.api.get<PageResultAuditLogView>('/platform/audit', { page: 0, size: 8 }))
      ]);
      this.tenants.set(tenants?.content ?? []);
      this.companies.set(companies ?? []);
      this.audit.set(audit?.content ?? []);
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
