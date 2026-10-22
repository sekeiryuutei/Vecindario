import { expect, test } from '@playwright/test';
import { loginTenant } from './helpers';

test.describe('Mascotas', () => {
  test('el administrador registra y elimina una mascota', async ({ page }) => {
    await loginTenant(page, 'admin@vecindad.local', 'Demo Norte');

    await page.getByRole('navigation', { name: 'Menú principal' })
      .getByRole('link', { name: 'Mascotas', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Mascotas', exact: true })).toBeVisible();
    await expect(page.getByText('Luna').first()).toBeVisible();

    const name = `Firulais QA ${Date.now()}`;
    await page.getByRole('button', { name: 'Registrar mascota' }).click();
    const dialog = page.getByRole('dialog', { name: 'Registrar mascota' });
    await expect(dialog).toBeVisible();
    await dialog.getByLabel('Inmueble *').selectOption({ index: 1 });
    await dialog.getByLabel('Nombre *').fill(name);
    await dialog.getByLabel('Especie *').selectOption('PERRO');
    await dialog.getByLabel('Raza').fill('Criollo');
    await dialog.getByRole('button', { name: 'Registrar mascota' }).click();
    await expect(page.getByText('Mascota registrada.')).toBeVisible();

    const row = page.getByRole('row', { name: new RegExp(name) });
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: 'Eliminar' }).click();
    await page.getByRole('alertdialog', { name: 'Eliminar mascota' })
      .getByRole('button', { name: 'Eliminar' }).click();
    await expect(page.getByText('Mascota eliminada.')).toBeVisible();
    await expect(row).toHaveCount(0);
  });

  test('el propietario registra una mascota en su inmueble', async ({ page }) => {
    await loginTenant(page, 'propietario@demo-norte.local', 'Demo Norte');

    await page.getByRole('navigation', { name: 'Menú principal' })
      .getByRole('link', { name: 'Mis mascotas' }).click();
    await expect(page.getByRole('heading', { name: 'Mis mascotas' })).toBeVisible();
    await expect(page.getByText('Luna').first()).toBeVisible();

    const name = `Nube QA ${Date.now()}`;
    await page.getByRole('button', { name: 'Registrar mascota' }).click();
    const dialog = page.getByRole('dialog', { name: 'Registrar mascota' });
    await expect(dialog).toBeVisible();
    await dialog.getByLabel('Inmueble *').selectOption({ index: 1 });
    await dialog.getByLabel('Nombre *').fill(name);
    await dialog.getByLabel('Especie *').selectOption('GATO');
    await dialog.getByRole('button', { name: 'Registrar mascota' }).click();
    await expect(page.getByText('Mascota registrada.')).toBeVisible();

    const card = page.locator('.card', { hasText: name });
    await expect(card).toBeVisible();
    await card.getByRole('button', { name: 'Eliminar' }).click();
    await page.getByRole('alertdialog', { name: 'Eliminar mascota' })
      .getByRole('button', { name: 'Eliminar' }).click();
    await expect(page.getByText('Mascota eliminada.')).toBeVisible();
    await expect(card).toHaveCount(0);
  });
});
