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

test.describe('Portería en pantalla móvil', () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test('el formulario de visitantes queda accesible al desplazarse', async ({ page }) => {
    await loginTenant(page, 'portero@demo-norte.local', 'Demo Norte');

    await page.goto('/app/porteria/visitantes');
    await expect(page.getByRole('heading', { name: 'Visitantes', exact: true })).toBeVisible();
    await page.locator('#walkName').scrollIntoViewIfNeeded();
    await expect(page.locator('#walkName')).toBeVisible();
    const scrollTop = await page.locator('.shell-main')
      .evaluate((element) => (element as HTMLElement).scrollTop);
    expect(scrollTop).toBeGreaterThan(0);
    const widths = await page.locator('.shell-main').evaluate((element) => ({
      scrollWidth: element.scrollWidth,
      clientWidth: element.clientWidth
    }));
    expect(widths.scrollWidth).toBeLessThanOrEqual(widths.clientWidth + 1);
  });
});
