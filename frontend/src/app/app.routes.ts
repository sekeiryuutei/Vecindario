import { Routes } from '@angular/router';
import { authGuard, guestGuard, permissionGuard, platformGuard, roleGuard, tenantSelectionGuard } from './core/guards';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/public/login.page').then((m) => m.LoginPage)
  },
  {
    path: 'recuperar',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/public/forgot-password.page').then((m) => m.ForgotPasswordPage)
  },
  {
    path: 'restablecer',
    loadComponent: () => import('./pages/public/reset-password.page').then((m) => m.ResetPasswordPage)
  },
  {
    path: 'seleccionar-copropiedad',
    canActivate: [tenantSelectionGuard],
    loadComponent: () => import('./pages/public/select-tenant.page').then((m) => m.SelectTenantPage)
  },
  {
    path: 'verificar-certificado',
    loadComponent: () => import('./pages/public/verify-certificate.page').then((m) => m.VerifyCertificatePage)
  },
  {
    path: 'verificar-certificado/:code',
    loadComponent: () => import('./pages/public/verify-certificate.page').then((m) => m.VerifyCertificatePage)
  },
  {
    path: 'pagos/wompi/resultado',
    loadComponent: () => import('./pages/public/wompi-result.page').then((m) => m.WompiResultPage)
  },
  {
    path: 'app',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell.component').then((m) => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'inicio' },
      { path: 'inicio', loadComponent: () => import('./pages/dashboard.page').then((m) => m.DashboardPage) },
      { path: 'perfil', loadComponent: () => import('./pages/profile.page').then((m) => m.ProfilePage) },
      { path: 'sin-acceso', loadComponent: () => import('./pages/access-denied.page').then((m) => m.AccessDeniedPage) },

      {
        path: 'propiedades',
        canActivate: [permissionGuard('PROPERTIES_VIEW')],
        loadComponent: () => import('./pages/admin/properties.page').then((m) => m.PropertiesPage)
      },
      {
        path: 'personas',
        canActivate: [permissionGuard('RESIDENTES_VIEW')],
        loadComponent: () => import('./pages/admin/people.page').then((m) => m.PeoplePage)
      },
      {
        path: 'vehiculos',
        canActivate: [permissionGuard('VEHICLES_VIEW')],
        loadComponent: () => import('./pages/admin/vehicles.page').then((m) => m.VehiclesPage)
      },
      {
        path: 'cartera',
        canActivate: [permissionGuard('FINANCE_VIEW')],
        loadComponent: () => import('./pages/admin/finance.page').then((m) => m.FinancePage)
      },
      {
        path: 'pagos',
        canActivate: [permissionGuard('FINANCE_VIEW', 'PAYMENTS_CREATE', 'PAYMENTS_RECONCILE')],
        loadComponent: () => import('./pages/admin/payments.page').then((m) => m.PaymentsPage)
      },
      {
        path: 'conciliacion',
        canActivate: [permissionGuard('PAYMENTS_RECONCILE')],
        loadComponent: () => import('./pages/admin/reconciliation.page').then((m) => m.ReconciliationPage)
      },
      {
        path: 'certificados',
        canActivate: [permissionGuard('CERTIFICATES_MANAGE')],
        loadComponent: () => import('./pages/admin/certificates.page').then((m) => m.CertificatesPage)
      },
      {
        path: 'reservas',
        canActivate: [permissionGuard('RESERVATIONS_VIEW', 'COMMON_AREAS_MANAGE')],
        loadComponent: () => import('./pages/admin/reservations.page').then((m) => m.ReservationsPage)
      },
      {
        path: 'pqrs',
        canActivate: [permissionGuard('PQR_VIEW')],
        loadComponent: () => import('./pages/admin/pqrs.page').then((m) => m.PqrsPage)
      },
      {
        path: 'comunicados',
        canActivate: [permissionGuard('COMMUNICATIONS_MANAGE', 'COMMUNICATIONS_VIEW')],
        loadComponent: () => import('./pages/admin/communications.page').then((m) => m.CommunicationsPage)
      },
      {
        path: 'notificaciones',
        canActivate: [permissionGuard('NOTIFICATIONS_MANAGE')],
        loadComponent: () => import('./pages/admin/notifications.page').then((m) => m.NotificationsPage)
      },
      {
        path: 'documentos',
        canActivate: [permissionGuard('DOCUMENTS_MANAGE')],
        loadComponent: () => import('./pages/admin/documents.page').then((m) => m.DocumentsPage)
      },
      {
        path: 'asambleas',
        canActivate: [permissionGuard('ASSEMBLIES_VIEW', 'ASSEMBLIES_MANAGE')],
        loadComponent: () => import('./pages/admin/assemblies.page').then((m) => m.AssembliesPage)
      },
      {
        path: 'proveedores',
        canActivate: [permissionGuard('SUPPLIERS_VIEW')],
        loadComponent: () => import('./pages/admin/suppliers.page').then((m) => m.SuppliersPage)
      },
      {
        path: 'mantenimiento',
        canActivate: [permissionGuard('MAINTENANCE_VIEW')],
        loadComponent: () => import('./pages/admin/maintenance.page').then((m) => m.MaintenancePage)
      },
      {
        path: 'presupuesto',
        canActivate: [permissionGuard('BUDGET_VIEW')],
        loadComponent: () => import('./pages/admin/budget.page').then((m) => m.BudgetPage)
      },
      {
        path: 'reportes',
        canActivate: [permissionGuard('REPORTS_VIEW')],
        loadComponent: () => import('./pages/admin/reports.page').then((m) => m.ReportsPage)
      },
      {
        path: 'auditoria',
        canActivate: [permissionGuard('AUDIT_VIEW')],
        loadComponent: () => import('./pages/admin/audit.page').then((m) => m.AuditPage)
      },
      {
        path: 'usuarios',
        canActivate: [permissionGuard('USERS_VIEW', 'USERS_MANAGE')],
        loadComponent: () => import('./pages/admin/members.page').then((m) => m.MembersPage)
      },
      {
        path: 'roles',
        canActivate: [permissionGuard('ROLES_MANAGE')],
        loadComponent: () => import('./pages/admin/roles.page').then((m) => m.RolesPage)
      },
      {
        path: 'configuracion',
        canActivate: [permissionGuard('TENANT_SETTINGS_VIEW', 'TENANT_SETTINGS_MANAGE', 'FINANCE_VIEW', 'NOTIFICATIONS_MANAGE')],
        loadComponent: () => import('./pages/admin/settings.page').then((m) => m.SettingsPage)
      },

      {
        path: 'porteria',
        canActivate: [roleGuard('PORTERO', 'SUPERVISOR_PORTERIA')],
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'control' },
          {
            path: 'control',
            canActivate: [permissionGuard('VEHICLES_VIEW')],
            loadComponent: () => import('./pages/porter/access-control.page').then((m) => m.AccessControlPage)
          },
          {
            path: 'visitantes',
            canActivate: [permissionGuard('VISITORS_VIEW')],
            loadComponent: () => import('./pages/porter/visitors.page').then((m) => m.PorterVisitorsPage)
          },
          {
            path: 'paquetes',
            canActivate: [permissionGuard('PACKAGES_VIEW')],
            loadComponent: () => import('./pages/porter/packages.page').then((m) => m.PorterPackagesPage)
          },
          {
            path: 'novedades',
            canActivate: [permissionGuard('INCIDENTS_VIEW')],
            loadComponent: () => import('./pages/porter/incidents.page').then((m) => m.PorterIncidentsPage)
          }
        ]
      },

      {
        path: 'residente',
        canActivate: [roleGuard('PROPIETARIO', 'ARRENDATARIO', 'RESIDENTE')],
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'inmuebles' },
          {
            path: 'inmuebles',
            canActivate: [permissionGuard('UNITS_VIEW_OWN')],
            loadComponent: () => import('./pages/resident/my-units.page').then((m) => m.MyUnitsPage)
          },
          {
            path: 'vehiculos',
            canActivate: [permissionGuard('VEHICLES_VIEW_OWN')],
            loadComponent: () => import('./pages/resident/my-vehicles.page').then((m) => m.MyVehiclesPage)
          },
          {
            path: 'estado-cuenta',
            canActivate: [permissionGuard('FINANCE_VIEW_OWN')],
            loadComponent: () => import('./pages/resident/my-statement.page').then((m) => m.MyStatementPage)
          },
          {
            path: 'certificados',
            canActivate: [permissionGuard('CERTIFICATES_VIEW_OWN')],
            loadComponent: () => import('./pages/resident/my-certificates.page').then((m) => m.MyCertificatesPage)
          },
          {
            path: 'visitas',
            canActivate: [permissionGuard('VISITORS_VIEW_OWN')],
            loadComponent: () => import('./pages/resident/my-visitors.page').then((m) => m.MyVisitorsPage)
          },
          {
            path: 'reservas',
            canActivate: [permissionGuard('RESERVATIONS_VIEW_OWN')],
            loadComponent: () => import('./pages/resident/my-reservations.page').then((m) => m.MyReservationsPage)
          },
          {
            path: 'pqrs',
            canActivate: [permissionGuard('PQR_VIEW_OWN')],
            loadComponent: () => import('./pages/resident/my-pqrs.page').then((m) => m.MyPqrsPage)
          },
          {
            path: 'comunicados',
            canActivate: [permissionGuard('COMMUNICATIONS_VIEW')],
            loadComponent: () => import('./pages/resident/my-communications.page').then((m) => m.MyCommunicationsPage)
          },
          {
            path: 'asambleas',
            canActivate: [permissionGuard('ASSEMBLIES_VIEW')],
            loadComponent: () => import('./pages/resident/my-assemblies.page').then((m) => m.MyAssembliesPage)
          },
          {
            path: 'documentos',
            canActivate: [permissionGuard('DOCUMENTS_VIEW')],
            loadComponent: () => import('./pages/resident/my-documents.page').then((m) => m.MyDocumentsPage)
          },
          {
            path: 'paquetes',
            canActivate: [permissionGuard('PACKAGES_VIEW_OWN')],
            loadComponent: () => import('./pages/resident/my-packages.page').then((m) => m.MyPackagesPage)
          }
        ]
      },

      {
        path: 'plataforma',
        canActivate: [platformGuard],
        children: [
          { path: '', loadComponent: () => import('./pages/platform/platform-dashboard.page').then((m) => m.PlatformDashboardPage) },
          { path: 'empresas', loadComponent: () => import('./pages/platform/companies.page').then((m) => m.CompaniesPage) },
          { path: 'copropiedades', loadComponent: () => import('./pages/platform/tenants.page').then((m) => m.TenantsPage) },
          { path: 'auditoria', loadComponent: () => import('./pages/platform/platform-audit.page').then((m) => m.PlatformAuditPage) }
        ]
      },

      { path: '**', redirectTo: 'inicio' }
    ]
  },
  { path: '**', loadComponent: () => import('./pages/public/not-found.page').then((m) => m.NotFoundPage) }
];
