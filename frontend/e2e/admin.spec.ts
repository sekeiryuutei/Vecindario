import { expect, test } from '@playwright/test';
import { loginTenant } from './helpers';

test.describe('Administración de copropiedad', () => {
  test('el administrador entra a la copropiedad y consulta la nómina', async ({ page }) => {
    await loginTenant(page, 'admin@vecindad.local', 'Demo Norte');

    await expect(page.getByRole('heading', { name: /Hola, / })).toBeVisible();
    await page.getByRole('link', { name: 'Nómina' }).click();
    await expect(page.getByRole('heading', { name: 'Nómina' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Corridas' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Empleados' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Parámetros' })).toBeVisible();

    await page.getByRole('button', { name: 'Parámetros' }).click();
    await expect(page.getByRole('heading', { name: 'Parámetros de liquidación' })).toBeVisible();
    await expect(page.getByLabel('Salud (%)').first()).toBeVisible();

    await page.getByRole('button', { name: 'Empleados' }).click();
    await expect(page.getByRole('heading', { name: 'Empleados', exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Nuevo empleado' })).toBeVisible();
  });

  test('el administrador edita los datos de la copropiedad', async ({ page }) => {
    await loginTenant(page, 'admin@vecindad.local', 'Demo Norte');

    await page.getByRole('link', { name: 'Configuración' }).click();
    await expect(page.getByRole('heading', { name: 'Configuración', exact: true })).toBeVisible();
    await expect(page.getByLabel('Nombre *')).toHaveValue('Conjunto Demo Norte');
    await expect(page.getByLabel('Correo de la copropiedad')).toBeVisible();

    await page.getByLabel('Teléfono').fill('3001234567');
    await page.getByRole('button', { name: 'Guardar datos' }).click();
    await expect(page.getByText('Datos de la copropiedad guardados.')).toBeVisible();
  });
});
