import { Injectable } from '@angular/core';
import { environment } from '../../environments/environment';

interface RuntimeConfig {
  apiBase?: string;
}

@Injectable({ providedIn: 'root' })
export class RuntimeConfigService {
  private base = environment.apiBase.replace(/\/$/, '');

  get apiBase(): string {
    return this.base;
  }

  async init(): Promise<void> {
    const controller = new AbortController();
    const timer = window.setTimeout(() => controller.abort(), 5000);
    try {
      const response = await fetch('runtime-config.json', {
        cache: 'no-store',
        headers: { Accept: 'application/json' },
        signal: controller.signal
      });
      if (!response.ok) return;
      const config = (await response.json()) as RuntimeConfig;
      const base = config.apiBase?.trim();
      if (base) this.base = base.replace(/\/$/, '');
    } catch {
      // The environment value remains the fallback for web builds and offline startup.
    } finally {
      window.clearTimeout(timer);
    }
  }
}
