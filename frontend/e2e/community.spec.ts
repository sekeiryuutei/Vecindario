import { expect, request, test } from '@playwright/test';
import { cachedTenantToken, DEMO_PASSWORD, loginTenant } from './helpers';

async function deleteAppointmentBySubject(subject: string): Promise<void> {
  const api = await request.newContext({
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:48124'
  });
  try {
    let token = cachedTenantToken('admin@vecindad.local', 'Demo Norte');
    if (!token) {
      const login = await api.post('/api/v1/auth/login', {
        data: { email: 'admin@vecindad.local', password: DEMO_PASSWORD }
      });
      expect(login.ok()).toBeTruthy();
      const auth = await login.json();
      const tenant = auth.tenants.find((item: { slug: string }) => item.slug === 'demo_norte');
      expect(tenant).toBeTruthy();
      const selected = await api.post('/api/v1/auth/select-tenant', {
        headers: { Authorization: `Bearer ${auth.accessToken}` },
        data: { tenantId: tenant.id }
      });
      expect(selected.ok()).toBeTruthy();
      token = (await selected.json()).accessToken;
    }
    const list = await api.get('/api/v1/appointments?size=100', {
      headers: { Authorization: `Bearer ${token}` }
    });
    expect(list.ok()).toBeTruthy();
    const page = await list.json();
    const appointment = page.content.find((item: { subject: string }) => item.subject === subject);
    if (appointment) {
      const removed = await api.delete(`/api/v1/appointments/${appointment.id}`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      expect(removed.ok()).toBeTruthy();
    }
  } finally {
    await api.dispose();
  }
}

function futureWeekday(daysAhead: number, hour: number, minute = 0): string {
  const date = new Date();
  date.setDate(date.getDate() + daysAhead);
  date.setHours(hour, minute, 0, 0);
  while (date.getDay() === 0 || date.getDay() === 6) {
    date.setDate(date.getDate() + 1);
  }
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

test.describe('Módulos comunitarios de administración', () => {
  test('el administrador consulta junta, citas, capacitaciones, personal, expedientes y matriz de roles', async ({ page }) => {
    await loginTenant(page, 'admin@vecindad.local', 'Demo Norte');

    await page.goto('/app/junta');
    await expect(page.getByRole('heading', { name: 'Junta directiva', exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Miembros de la junta' })).toBeVisible();
    await expect(page.getByText('Presidente').first()).toBeVisible();

    await page.goto('/app/citas');
    await expect(page.getByRole('heading', { name: /Citas con la administraci/ })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Agenda' })).toBeVisible();
    await expect(page.getByText(/cita\(s\)\./)).toBeVisible();

    await page.goto('/app/capacitaciones');
    await expect(page.getByRole('heading', { name: 'Capacitaciones', exact: true }).first()).toBeVisible();
    await expect(page.getByRole('button', { name: 'Nueva capacitación' })).toBeVisible();

    await page.goto('/app/personal');
    await expect(page.getByRole('heading', { name: 'Personal operativo' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Equipo' })).toBeVisible();
    await expect(page.getByText('Portero').first()).toBeVisible();

    await page.goto('/app/expedientes');
    await expect(page.getByRole('heading', { name: 'Expediente del inmueble' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Personas vinculadas' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Notas del expediente' })).toBeVisible();

    await page.goto('/app/roles');
    await expect(page.getByRole('heading', { name: 'Roles y permisos' })).toBeVisible();
    await page.getByRole('button', { name: 'Matriz', exact: true }).click();
    await page.getByLabel('Buscar permiso').fill('BOARD_VIEW');
    await expect(page.getByText('BOARD_VIEW', { exact: true }).first()).toBeVisible();
  });

  test('el administrador registra y elimina personal operativo', async ({ page }) => {
    await loginTenant(page, 'admin@vecindad.local', 'Demo Norte');
    await page.goto('/app/personal');

    const name = `Persona QA ${Date.now()}`;
    await page.getByRole('button', { name: 'Registrar persona' }).click();
    const dialog = page.getByRole('dialog', { name: 'Registrar persona' });
    await expect(dialog).toBeVisible();
    await dialog.getByLabel('Nombre completo *').fill(name);
    await dialog.getByLabel('Número de documento *').fill(String(Date.now()));
    await dialog.getByLabel('Cargo *').fill('Auxiliar de servicios');
    await dialog.getByRole('button', { name: 'Guardar', exact: true }).click();
    await expect(page.getByText('Personal registrado.')).toBeVisible();

    const row = page.getByRole('row', { name: new RegExp(name) });
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: 'Eliminar' }).click();
    await page.getByRole('alertdialog', { name: 'Eliminar personal' })
      .getByRole('button', { name: 'Eliminar', exact: true }).click();
    await expect(page.getByText('Personal eliminado.')).toBeVisible();
    await expect(row).toHaveCount(0);
  });

  test('el administrador crea, cancela y elimina una capacitación', async ({ page }) => {
    await loginTenant(page, 'admin@vecindad.local', 'Demo Norte');
    await page.goto('/app/capacitaciones');

    const title = `Capacitación QA ${Date.now()}`;
    await page.getByRole('button', { name: 'Nueva capacitación' }).click();
    const dialog = page.getByRole('dialog', { name: 'Nueva capacitación' });
    await expect(dialog).toBeVisible();
    await dialog.locator('#trainingTitle').fill(title);
    await dialog.locator('#trainingStarts').fill(futureWeekday(21, 9));
    await dialog.locator('#trainingEnds').fill(futureWeekday(21, 11));
    await dialog.getByRole('button', { name: 'Guardar', exact: true }).click();
    await expect(page.getByText('Capacitación creada.')).toBeVisible();

    const row = page.getByRole('row', { name: new RegExp(title) });
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: 'Cancelar', exact: true }).click();
    await page.getByRole('alertdialog', { name: 'Cancelar capacitación' })
      .getByRole('button', { name: 'Cancelar capacitación', exact: true }).click();
    await expect(page.getByText('Capacitación cancelada.')).toBeVisible();

    await row.getByRole('button', { name: 'Eliminar', exact: true }).click();
    await page.getByRole('alertdialog', { name: 'Eliminar capacitación' })
      .getByRole('button', { name: 'Eliminar', exact: true }).click();
    await expect(page.getByText('Capacitación eliminada.')).toBeVisible();
    await expect(row).toHaveCount(0);
  });

  test('el administrador agrega y elimina una nota del expediente', async ({ page }) => {
    await loginTenant(page, 'admin@vecindad.local', 'Demo Norte');
    await page.goto('/app/expedientes');
    await expect(page.locator('#unitFileUnit option', { hasText: 'T1-101' })).toHaveCount(1);
    const selectedUnit = await page.locator('#unitFileUnit option').evaluateAll((options) =>
      options.find((option) => option.textContent?.includes('T1-101'))?.value);
    expect(selectedUnit).toBeTruthy();
    await page.locator('#unitFileUnit').selectOption(selectedUnit!);
    await expect(page.getByRole('heading', { name: 'Notas del expediente' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'T1-101', exact: true })).toBeVisible();

    const title = `Nota QA ${Date.now()}`;
    await page.getByRole('button', { name: 'Agregar nota' }).click();
    const dialog = page.getByRole('dialog', { name: 'Agregar nota' });
    await expect(dialog).toBeVisible();
    await dialog.getByLabel('Título *').fill(title);
    await dialog.getByLabel('Nota *').fill('Seguimiento creado por la prueba de interfaz.');
    await dialog.getByRole('button', { name: 'Guardar nota' }).click();
    await expect(page.getByText('Nota agregada.')).toBeVisible();

    const noteCard = page.locator('strong', { hasText: title }).locator('..').locator('..');
    await expect(noteCard).toBeVisible();
    await noteCard.getByRole('button', { name: 'Eliminar', exact: true }).click();
    await page.getByRole('alertdialog', { name: 'Eliminar nota' })
      .getByRole('button', { name: 'Eliminar', exact: true }).click();
    await expect(page.getByText('Nota eliminada.')).toBeVisible();
    await expect(page.getByText(title, { exact: true })).toHaveCount(0);
  });
});

test.describe('Módulos comunitarios del residente', () => {
  test('el propietario consulta junta, capacitaciones y expediente, y solicita una cita', async ({ page }) => {
    await loginTenant(page, 'propietario@demo-norte.local', 'Demo Norte');

    await page.goto('/app/residente/junta');
    await expect(page.getByRole('heading', { name: 'Junta directiva', exact: true })).toBeVisible();
    await expect(page.getByText('Presidente').first()).toBeVisible();

    await page.goto('/app/residente/capacitaciones');
    await expect(page.getByRole('heading', { name: 'Capacitaciones', exact: true })).toBeVisible();

    await page.goto('/app/residente/expediente');
    await expect(page.getByRole('heading', { name: 'Expediente de mi inmueble' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Personas vinculadas' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'T1-101', exact: true })).toBeVisible();

    await page.goto('/app/residente/citas');
    await expect(page.getByRole('heading', { name: /Citas con la administraci/ })).toBeVisible();
    await page.locator('#myAppointmentUnit').selectOption({ index: 1 });
    const subject = `Cita QA ${Date.now()}`;
    await page.locator('#myAppointmentSubject').fill(subject);
    await page.locator('#myAppointmentReason').fill('Consulta de prueba de interfaz para validar la agenda.');
    await page.locator('#myAppointmentDate').fill(futureWeekday(30, 11, 15));
    await page.getByRole('button', { name: 'Solicitar cita' }).click();
    await expect(page.getByText('Solicitud enviada. La administración confirmará la cita.')).toBeVisible();

    const card = page.locator('strong', { hasText: subject }).locator('..').locator('..').locator('..');
    await expect(card).toBeVisible();
    await card.getByRole('button', { name: 'Cancelar cita' }).click();
    await page.getByRole('alertdialog', { name: 'Cancelar cita' })
      .getByRole('button', { name: 'Cancelar cita' }).click();
    await expect(page.getByText('Cita cancelada.')).toBeVisible();
    await expect(card.getByText('Cancelado', { exact: true })).toBeVisible();
    await deleteAppointmentBySubject(subject);
  });
});

test.describe('Módulos comunitarios en pantalla móvil', () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test('las pantallas nuevas no desbordan horizontalmente', async ({ page }) => {
    await loginTenant(page, 'admin@vecindad.local', 'Demo Norte');
    const paths = [
      ['/app/junta', 'Junta directiva'],
      ['/app/citas', 'Citas con la administración'],
      ['/app/capacitaciones', 'Capacitaciones'],
      ['/app/personal', 'Personal operativo'],
      ['/app/expedientes', 'Expediente del inmueble'],
      ['/app/roles', 'Roles y permisos']
    ];
    for (const [path, heading] of paths) {
      await page.goto(path);
      await expect(page.getByRole('heading', { name: heading, exact: true }).first()).toBeVisible();
      const widths = await page.locator('.shell-main').evaluate((element) => ({
        scrollWidth: element.scrollWidth,
        clientWidth: element.clientWidth
      }));
      expect(widths.scrollWidth).toBeLessThanOrEqual(widths.clientWidth + 1);
    }
  });
});
