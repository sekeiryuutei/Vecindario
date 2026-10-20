export interface PermissionGroup {
  label: string;
  permissions: Array<{ code: string; label: string }>;
}

export const PERMISSION_GROUPS: PermissionGroup[] = [
  {
    label: 'Inmuebles y personas',
    permissions: [
      { code: 'PROPERTIES_VIEW', label: 'Ver inmuebles' },
      { code: 'PROPERTIES_CREATE', label: 'Crear inmuebles' },
      { code: 'PROPERTIES_UPDATE', label: 'Editar inmuebles' },
      { code: 'PROPERTIES_DELETE', label: 'Eliminar inmuebles' },
      { code: 'RESIDENTES_VIEW', label: 'Ver personas' },
      { code: 'RESIDENTES_CREATE', label: 'Crear personas' },
      { code: 'RESIDENTES_UPDATE', label: 'Editar personas' },
      { code: 'RESIDENTES_DELETE', label: 'Eliminar personas' },
      { code: 'USERS_VIEW', label: 'Ver usuarios' },
      { code: 'USERS_MANAGE', label: 'Gestionar usuarios' },
      { code: 'ROLES_MANAGE', label: 'Configurar permisos' }
    ]
  },
  {
    label: 'Vehículos y portería',
    permissions: [
      { code: 'VEHICLES_VIEW', label: 'Ver vehículos' },
      { code: 'VEHICLES_CREATE', label: 'Crear vehículos' },
      { code: 'VEHICLES_UPDATE', label: 'Editar vehículos' },
      { code: 'VEHICLES_DELETE', label: 'Eliminar vehículos' },
      { code: 'VEHICLES_REGISTER_ENTRY', label: 'Registrar entradas' },
      { code: 'VEHICLES_REGISTER_EXIT', label: 'Registrar salidas' },
      { code: 'VISITORS_VIEW', label: 'Ver visitantes' },
      { code: 'VISITORS_VIEW_OWN', label: 'Ver visitas propias' },
      { code: 'VISITORS_CREATE', label: 'Crear invitaciones' },
      { code: 'VISITORS_AUTHORIZE', label: 'Autorizar visitantes en portería' },
      { code: 'VISITORS_RESPOND_OWN', label: 'Autorizar visitas propias' },
      { code: 'PACKAGES_VIEW', label: 'Ver paquetes' },
      { code: 'PACKAGES_VIEW_OWN', label: 'Ver paquetes propios' },
      { code: 'PACKAGES_MANAGE', label: 'Gestionar paquetes' },
      { code: 'INCIDENTS_VIEW', label: 'Ver novedades de portería' },
      { code: 'INCIDENTS_CREATE', label: 'Reportar novedades de portería' },
      { code: 'INCIDENTS_MANAGE', label: 'Gestionar y cerrar novedades' }
    ]
  },
  {
    label: 'Finanzas',
    permissions: [
      { code: 'FINANCE_VIEW', label: 'Ver cartera y finanzas' },
      { code: 'FINANCE_MANAGE', label: 'Administrar finanzas' },
      { code: 'FINANCE_VIEW_OWN', label: 'Ver estado de cuenta propio' },
      { code: 'PAYMENTS_CREATE', label: 'Registrar pagos' },
      { code: 'PAYMENTS_RECONCILE', label: 'Conciliar pagos' },
      { code: 'PAYMENTS_ONLINE_CREATE', label: 'Pagar en línea' },
      { code: 'BUDGET_VIEW', label: 'Ver presupuesto' },
      { code: 'BUDGET_MANAGE', label: 'Gestionar presupuesto' },
      { code: 'REPORTS_VIEW', label: 'Ver reportes' },
      { code: 'REPORTS_EXPORT', label: 'Exportar reportes' }
    ]
  },
  {
    label: 'Servicios y comunidad',
    permissions: [
      { code: 'PQR_CREATE', label: 'Crear PQRS' },
      { code: 'PQR_VIEW', label: 'Ver PQRS' },
      { code: 'PQR_VIEW_OWN', label: 'Ver PQRS propias' },
      { code: 'PQR_ASSIGN', label: 'Asignar PQRS' },
      { code: 'PQR_RESPOND', label: 'Responder PQRS' },
      { code: 'PQR_CLOSE', label: 'Cerrar PQRS' },
      { code: 'COMMON_AREAS_MANAGE', label: 'Configurar zonas comunes' },
      { code: 'RESERVATIONS_VIEW', label: 'Ver reservas' },
      { code: 'RESERVATIONS_CREATE', label: 'Crear reservas' },
      { code: 'RESERVATIONS_APPROVE', label: 'Aprobar reservas' },
      { code: 'RESERVATIONS_VIEW_OWN', label: 'Ver reservas propias' },
      { code: 'RESERVATIONS_MANAGE_OWN', label: 'Gestionar reservas propias' },
      { code: 'ASSEMBLIES_VIEW', label: 'Ver asambleas' },
      { code: 'ASSEMBLIES_MANAGE', label: 'Gestionar asambleas' },
      { code: 'ASSEMBLIES_VOTE', label: 'Votar en asambleas' },
      { code: 'COMMUNICATIONS_VIEW', label: 'Ver comunicados' },
      { code: 'COMMUNICATIONS_MANAGE', label: 'Publicar comunicados' },
      { code: 'NOTIFICATIONS_MANAGE', label: 'Gestionar notificaciones' }
    ]
  },
  {
    label: 'Operación y documentos',
    permissions: [
      { code: 'MAINTENANCE_VIEW', label: 'Ver mantenimiento' },
      { code: 'MAINTENANCE_MANAGE', label: 'Gestionar mantenimiento' },
      { code: 'DOCUMENTS_VIEW', label: 'Ver documentos' },
      { code: 'DOCUMENTS_MANAGE', label: 'Gestionar documentos' },
      { code: 'SUPPLIERS_VIEW', label: 'Ver proveedores' },
      { code: 'SUPPLIERS_MANAGE', label: 'Gestionar proveedores' },
      { code: 'CERTIFICATES_VIEW_OWN', label: 'Ver certificados propios' },
      { code: 'CERTIFICATES_MANAGE', label: 'Emitir certificados' },
      { code: 'AUDIT_VIEW', label: 'Consultar auditoría' },
      { code: 'TENANT_SETTINGS_VIEW', label: 'Ver configuración' },
      { code: 'TENANT_SETTINGS_MANAGE', label: 'Modificar configuración' }
    ]
  }
];

export const ALL_PERMISSIONS: string[] = PERMISSION_GROUPS.flatMap((group) =>
  group.permissions.map((permission) => permission.code)
);

export function permissionLabel(code: string): string {
  for (const group of PERMISSION_GROUPS) {
    const found = group.permissions.find((permission) => permission.code === code);
    if (found) return found.label;
  }
  return code.replaceAll('_', ' ').toLowerCase();
}
