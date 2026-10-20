import { expect, Page } from '@playwright/test';

export const DEMO_PASSWORD = process.env.E2E_PASSWORD ?? 'Demo#2026!';

export async function login(page: Page, email: string): Promise<void> {
  await page.goto('/login');
  await page.locator('#email').fill(email);
  await page.locator('#password').fill(DEMO_PASSWORD);
  await Promise.all([
    page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 20_000 }),
    page.getByRole('button', { name: 'Ingresar', exact: true }).click()
  ]);
}

export async function selectTenant(page: Page, name: string): Promise<void> {
  const current = await page.locator('.topbar-title').innerText().catch(() => '');
  if (new RegExp(name, 'i').test(current)) return;

  if (!page.url().includes('seleccionar-copropiedad')) {
    const switchLink = page.getByRole('link', { name: 'Cambiar copropiedad' });
    if (await switchLink.isVisible().catch(() => false)) {
      await switchLink.click();
    } else {
      await page.goto('/seleccionar-copropiedad');
    }
  }

  await page.getByRole('button', { name: new RegExp(name, 'i') }).click();
  await page.waitForURL('**/app/inicio', { timeout: 20_000 });
}

export async function expectTenantShell(page: Page, tenantName: string): Promise<void> {
  await expect(page.locator('.topbar-title')).toContainText(tenantName);
  await expect(page.locator('.sidebar-nav')).toBeVisible();
}

export async function loginTenant(page: Page, email: string, tenantName: string): Promise<void> {
  await login(page, email);
  await selectTenant(page, tenantName);
  await expectTenantShell(page, tenantName);
}
