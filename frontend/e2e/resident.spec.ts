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
});
