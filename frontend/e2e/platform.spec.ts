import { expect, test } from '@playwright/test';
import { login } from './helpers';

test.describe('Plataforma', () => {
  test('el superadministrador entra al panel de plataforma', async ({ page }) => {
    await login(page, 'superadmin@vecindad.local');
    await expect(page).toHaveURL(/\/app\/plataforma/);
    await expect(page.getByRole('heading', { name: 'Plataforma' })).toBeVisible();
    await expect(page.getByRole('navigation', { name: 'Menú principal' })
      .getByRole('link', { name: 'Empresas' })).toBeVisible();
  });

  test('el superadministrador configura integraciones sin ver los secretos', async ({ page }) => {
    await login(page, 'superadmin@vecindad.local');
    await page.goto('/app/plataforma/copropiedades');
    await expect(page.locator('h1').filter({ hasText: 'Copropiedades' })).toBeVisible();

    await page.getByRole('button', { name: 'Gestionar' }).first().click();
    await expect(page.getByText(/Integraciones de la copropiedad/i)).toBeVisible();
    await expect(page.getByLabel('Llave privada')).toHaveValue('');
    await expect(page.getByLabel('Secreto de integridad')).toHaveValue('');
    await expect(page.getByLabel('Clave del proveedor')).toHaveValue('');
  });
});
