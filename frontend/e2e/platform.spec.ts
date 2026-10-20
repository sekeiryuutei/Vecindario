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
});
