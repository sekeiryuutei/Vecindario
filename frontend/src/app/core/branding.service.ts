import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom, timeout } from 'rxjs';
import { ApiService } from './api.service';
import { PublicConfig } from './models';

@Injectable({ providedIn: 'root' })
export class BrandingService {
  private readonly api = inject(ApiService);
  private initialized = false;
  readonly appName = signal('Portal de copropiedad');
  readonly company = signal('');

  async init(): Promise<void> {
    if (this.initialized) return;
    this.initialized = true;
    try {
      const config = await firstValueFrom(this.api.get<PublicConfig>('/public/config').pipe(timeout(5000)));
      this.apply(config);
    } catch {
      this.applyTitle();
    }
  }

  apply(config: PublicConfig | null | undefined): void {
    const name = config?.appName?.trim();
    const company = config?.company?.trim();
    if (name) this.appName.set(name);
    if (company) this.company.set(company);
    this.applyTitle();
  }

  private applyTitle(): void {
    const name = this.appName();
    document.title = name;
    document.querySelector('meta[name="apple-mobile-web-app-title"]')?.setAttribute('content', name);
    document.querySelector('meta[name="application-name"]')?.setAttribute('content', name);
  }
}
