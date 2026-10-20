import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { IonIcon } from '@ionic/angular/standalone';
import { AuthService } from '../core/auth.service';
import { BrandingService } from '../core/branding.service';
import { ConfirmService } from '../core/confirm.service';
import { roleLabel } from '../core/labels';
import { TenantContextService } from '../core/tenant-context.service';
import { initials } from '../core/utils';

interface MenuItem {
  label: string;
  path: string;
  icon: string;
  permissions?: string[];
}

interface MenuSection {
  label: string;
  items: MenuItem[];
}

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IonIcon],
  template: `
    <div class="app-shell">
      <div class="sidebar-backdrop" [class.open]="sidebarOpen()" (click)="sidebarOpen.set(false)"></div>
      <aside class="sidebar" [class.open]="sidebarOpen()">
        <div class="sidebar-brand">
          <div class="brand-mark" aria-hidden="true">{{ brandInitial() }}</div>
          <div class="brand-text">
            <div class="brand-name">{{ branding.appName() }}</div>
            <div class="brand-company">{{ branding.company() || 'Administración de copropiedades' }}</div>
          </div>
        </div>

        <nav class="sidebar-nav" aria-label="Menú principal">
          @for (section of menuSections(); track section.label) {
            <div class="nav-group-label">{{ section.label }}</div>
            @for (item of section.items; track item.path) {
              <a class="nav-item" [routerLink]="item.path" routerLinkActive="active" (click)="sidebarOpen.set(false)">
                <ion-icon [name]="item.icon" aria-hidden="true"></ion-icon>
                <span>{{ item.label }}</span>
              </a>
            }
          }
        </nav>

        <div class="sidebar-footer">
          <a class="user-card" routerLink="/app/perfil" (click)="sidebarOpen.set(false)" style="text-decoration:none">
            <span class="avatar">{{ userInitials() }}</span>
            <span class="user-meta">
              <span class="user-name">{{ auth.user()?.fullName || 'Mi cuenta' }}</span>
              <span class="user-role">{{ roleName() }}</span>
            </span>
          </a>
          <button type="button" class="nav-item" style="width:100%;border:0;background:transparent;cursor:pointer" (click)="logout()">
            <ion-icon name="logOutOutline" aria-hidden="true" style="font-size:18px;color:#94a3b8"></ion-icon>
            <span>Cerrar sesión</span>
          </button>
        </div>
      </aside>

      <div class="shell-main">
        <header class="topbar">
          <button type="button" class="mobile-menu-btn" aria-label="Abrir menú" (click)="sidebarOpen.set(!sidebarOpen())">
            <ion-icon name="menuOutline"></ion-icon>
          </button>
          <div class="topbar-title">{{ tenantName() }}</div>
          <div class="topbar-context">
            {{ roleName() }}
            @if (branding.company()) { <span> · {{ branding.company() }}</span> }
          </div>
          @if (auth.tenants().length > 1) {
            <a class="topbar-switch" routerLink="/seleccionar-copropiedad" title="Cambiar de copropiedad">
              Cambiar copropiedad
            </a>
          }
        </header>
        <main class="content">
          <router-outlet></router-outlet>
        </main>
      </div>
    </div>
  `
})
export class ShellComponent {
  readonly auth = inject(AuthService);
  readonly branding = inject(BrandingService);
  readonly tenantContext = inject(TenantContextService);
  private readonly confirm = inject(ConfirmService);
  private readonly router = inject(Router);
  readonly sidebarOpen = signal(false);

  readonly brandInitial = computed(() => {
    const name = this.branding.appName().trim();
    return name ? name.charAt(0).toUpperCase() : 'V';
  });

  readonly userInitials = computed(() => initials(this.auth.user()?.fullName));
  readonly roleName = computed(() => roleLabel(this.auth.activeRole()));

  readonly tenantName = computed(() => {
    const tenant = this.tenantContext.tenant();
    if (tenant?.name) return tenant.name;
    const session = this.auth.session();
    return session?.tenants?.find((item) => item.id === session.activeTenantId)?.name ?? 'Portal de copropiedad';
  });

  readonly menuSections = computed<MenuSection[]>(() => {
    const session = this.auth.session();
    if (!session) return [];
    const platformOnly = session.user?.platformRole === 'SUPER_ADMIN_PLATFORM' && !session.activeTenantId;
    if (platformOnly) {
      return [{
        label: 'Plataforma',
        items: [
          { label: 'Resumen', path: '/app/plataforma', icon: 'homeOutline' },
          { label: 'Empresas', path: '/app/plataforma/empresas', icon: 'businessOutline' },
          { label: 'Copropiedades', path: '/app/plataforma/copropiedades', icon: 'layersOutline' },
          { label: 'Auditoría global', path: '/app/plataforma/auditoria', icon: 'shieldCheckmarkOutline' }
        ]
      }];
    }

    if (this.auth.isPorter()) {
      const porterSections: MenuSection[] = [];
      pushFiltered(porterSections, 'Operación de portería', [
        { label: 'Resumen', path: '/app/inicio', icon: 'homeOutline' },
        { label: 'Control de acceso', path: '/app/porteria/control', icon: 'carOutline', permissions: ['VEHICLES_VIEW'] },
        { label: 'Visitantes', path: '/app/porteria/visitantes', icon: 'peopleOutline', permissions: ['VISITORS_VIEW'] },
        { label: 'Paquetería', path: '/app/porteria/paquetes', icon: 'cubeOutline', permissions: ['PACKAGES_VIEW'] },
        { label: 'Novedades', path: '/app/porteria/novedades', icon: 'alertCircleOutline', permissions: ['INCIDENTS_VIEW'] }
      ], this.auth);
      return porterSections;
    }

    if (this.auth.isResident()) {
      const residentSections: MenuSection[] = [];
      pushFiltered(residentSections, 'Mi copropiedad', [
        { label: 'Resumen', path: '/app/inicio', icon: 'homeOutline' },
        { label: 'Mis inmuebles', path: '/app/residente/inmuebles', icon: 'businessOutline', permissions: ['UNITS_VIEW_OWN'] },
        { label: 'Mis vehículos', path: '/app/residente/vehiculos', icon: 'carOutline', permissions: ['VEHICLES_VIEW_OWN'] },
        { label: 'Estado de cuenta', path: '/app/residente/estado-cuenta', icon: 'walletOutline', permissions: ['FINANCE_VIEW_OWN'] },
        { label: 'Certificados', path: '/app/residente/certificados', icon: 'ribbonOutline', permissions: ['CERTIFICATES_VIEW_OWN'] }
      ], this.auth);
      pushFiltered(residentSections, 'Convivencia', [
        { label: 'Visitas e invitaciones', path: '/app/residente/visitas', icon: 'peopleOutline', permissions: ['VISITORS_VIEW_OWN'] },
        { label: 'Reservas', path: '/app/residente/reservas', icon: 'calendarOutline', permissions: ['RESERVATIONS_VIEW_OWN'] },
        { label: 'PQRS', path: '/app/residente/pqrs', icon: 'chatbubblesOutline', permissions: ['PQR_VIEW_OWN'] },
        { label: 'Comunicados', path: '/app/residente/comunicados', icon: 'megaphoneOutline', permissions: ['COMMUNICATIONS_VIEW'] },
        { label: 'Asambleas', path: '/app/residente/asambleas', icon: 'podiumOutline', permissions: ['ASSEMBLIES_VIEW'] }
      ], this.auth);
      pushFiltered(residentSections, 'Recursos', [
        { label: 'Documentos', path: '/app/residente/documentos', icon: 'documentTextOutline', permissions: ['DOCUMENTS_VIEW'] },
        { label: 'Mis paquetes', path: '/app/residente/paquetes', icon: 'cubeOutline', permissions: ['PACKAGES_VIEW_OWN'] }
      ], this.auth);
      return residentSections;
    }

    const sections: MenuSection[] = [{
      label: 'General',
      items: [
        { label: 'Resumen', path: '/app/inicio', icon: 'homeOutline' }
      ]
    }];

    const operation: MenuItem[] = [
      { label: 'Inmuebles', path: '/app/propiedades', icon: 'businessOutline', permissions: ['PROPERTIES_VIEW'] },
      { label: 'Personas', path: '/app/personas', icon: 'peopleOutline', permissions: ['RESIDENTES_VIEW'] },
      { label: 'Vehículos', path: '/app/vehiculos', icon: 'carOutline', permissions: ['VEHICLES_VIEW'] },
      { label: 'Reservas y zonas', path: '/app/reservas', icon: 'calendarOutline', permissions: ['RESERVATIONS_VIEW', 'COMMON_AREAS_MANAGE'] },
      { label: 'PQRS', path: '/app/pqrs', icon: 'chatbubblesOutline', permissions: ['PQR_VIEW'] },
      { label: 'Comunicados', path: '/app/comunicados', icon: 'megaphoneOutline', permissions: ['COMMUNICATIONS_VIEW', 'COMMUNICATIONS_MANAGE'] }
    ];
    pushFiltered(sections, 'Operación', operation, this.auth);

    pushFiltered(sections, 'Finanzas', [
      { label: 'Cartera', path: '/app/cartera', icon: 'walletOutline', permissions: ['FINANCE_VIEW'] },
      { label: 'Pagos', path: '/app/pagos', icon: 'cardOutline', permissions: ['FINANCE_VIEW', 'PAYMENTS_CREATE', 'PAYMENTS_RECONCILE'] },
      { label: 'Conciliación bancaria', path: '/app/conciliacion', icon: 'swapHorizontalOutline', permissions: ['PAYMENTS_RECONCILE'] },
      { label: 'Presupuesto', path: '/app/presupuesto', icon: 'pieChartOutline', permissions: ['BUDGET_VIEW'] },
      { label: 'Nómina', path: '/app/nomina', icon: 'cashOutline', permissions: ['PAYROLL_VIEW', 'PAYROLL_MANAGE'] },
      { label: 'Reportes', path: '/app/reportes', icon: 'barChartOutline', permissions: ['REPORTS_VIEW'] },
      { label: 'Certificados', path: '/app/certificados', icon: 'ribbonOutline', permissions: ['CERTIFICATES_MANAGE'] }
    ], this.auth);

    pushFiltered(sections, 'Servicios', [
      { label: 'Documentos', path: '/app/documentos', icon: 'documentTextOutline', permissions: ['DOCUMENTS_MANAGE'] },
      { label: 'Asambleas', path: '/app/asambleas', icon: 'podiumOutline', permissions: ['ASSEMBLIES_VIEW', 'ASSEMBLIES_MANAGE'] },
      { label: 'Proveedores', path: '/app/proveedores', icon: 'briefcaseOutline', permissions: ['SUPPLIERS_VIEW'] },
      { label: 'Mantenimiento', path: '/app/mantenimiento', icon: 'constructOutline', permissions: ['MAINTENANCE_VIEW'] },
      { label: 'Notificaciones', path: '/app/notificaciones', icon: 'notificationsOutline', permissions: ['NOTIFICATIONS_MANAGE'] }
    ], this.auth);

    pushFiltered(sections, 'Administración', [
      { label: 'Usuarios', path: '/app/usuarios', icon: 'personCircleOutline', permissions: ['USERS_VIEW', 'USERS_MANAGE'] },
      { label: 'Roles y permisos', path: '/app/roles', icon: 'keyOutline', permissions: ['ROLES_MANAGE'] },
      { label: 'Auditoría', path: '/app/auditoria', icon: 'shieldCheckmarkOutline', permissions: ['AUDIT_VIEW'] },
      { label: 'Configuración', path: '/app/configuracion', icon: 'settingsOutline', permissions: ['TENANT_SETTINGS_VIEW', 'TENANT_SETTINGS_MANAGE', 'FINANCE_VIEW', 'NOTIFICATIONS_MANAGE'] }
    ], this.auth);

    if (session.user?.platformRole === 'SUPER_ADMIN_PLATFORM') {
      sections.push({
        label: 'Plataforma',
        items: [
          { label: 'Empresas', path: '/app/plataforma/empresas', icon: 'businessOutline' },
          { label: 'Copropiedades', path: '/app/plataforma/copropiedades', icon: 'layersOutline' },
          { label: 'Auditoría global', path: '/app/plataforma/auditoria', icon: 'shieldCheckmarkOutline' }
        ]
      });
    }

    return sections;
  });

  constructor() {
    void this.auth.restore();
    const activeTenantId = this.auth.session()?.activeTenantId;
    if (activeTenantId) void this.tenantContext.load(activeTenantId);
  }

  async logout(): Promise<void> {
    const accepted = await this.confirm.confirm({
      title: 'Cerrar sesión',
      message: '¿Deseas salir del portal en este dispositivo?',
      confirmLabel: 'Cerrar sesión',
      danger: true
    });
    if (accepted) await this.auth.logout();
  }
}

function pushFiltered(sections: MenuSection[], label: string, items: MenuItem[], auth: AuthService): void {
  const visible = items.filter((item) => !item.permissions || auth.hasPermission(...item.permissions));
  if (visible.length) sections.push({ label, items: visible });
}
