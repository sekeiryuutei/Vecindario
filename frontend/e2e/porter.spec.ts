import { expect, test } from '@playwright/test';
import { loginTenant } from './helpers';

test.describe('Portería', () => {
  test('el portero consulta el control de acceso', async ({ page }) => {
    await loginTenant(page, 'portero@demo-norte.local', 'Demo Norte');

    await expect(page.getByRole('heading', { name: 'Portería' })).toBeVisible();
    await page.getByRole('navigation', { name: 'Menú principal' })
      .getByRole('link', { name: 'Control de acceso' }).click();
    await expect(page.getByRole('heading', { name: 'Control de acceso' })).toBeVisible();
    await expect(page.getByLabel('Placa').first()).toBeVisible();
  });
});
