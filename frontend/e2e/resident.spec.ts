import { expect, test } from '@playwright/test';
import { loginTenant } from './helpers';

test.describe('Portal del residente', () => {
  test('el propietario consulta su estado de cuenta', async ({ page }) => {
    await loginTenant(page, 'propietario@demo-norte.local', 'Demo Sur');

    await expect(page.getByRole('heading', { name: 'Mi copropiedad' })).toBeVisible();
    await page.getByRole('navigation', { name: 'Menú principal' })
      .getByRole('link', { name: 'Estado de cuenta' }).click();
    await expect(page.getByRole('heading', { name: 'Estado de cuenta' })).toBeVisible();
    await expect(page.getByText('Movimientos del período')).toBeVisible();
  });

  test('el propietario ve el desglose de la tarifa antes de pagar en línea', async ({ page }) => {
    await loginTenant(page, 'propietario@demo-norte.local', 'Demo Norte');

    await page.getByRole('navigation', { name: 'Menú principal' })
      .getByRole('link', { name: 'Estado de cuenta' }).click();
    await expect(page.getByRole('heading', { name: 'Estado de cuenta' })).toBeVisible();

    await page.locator('#amount').fill('25000');
    await expect(page.getByText('Tarifa de la plataforma')).toBeVisible();
    await expect(page.getByText('Comisión de la pasarela')).toBeVisible();
    await expect(page.getByText('Total a pagar')).toBeVisible();
  });
});
