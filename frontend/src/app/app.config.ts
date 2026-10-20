import { ApplicationConfig, LOCALE_ID, inject, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular/standalone';
import { provideServiceWorker } from '@angular/service-worker';
import { routes } from './app.routes';
import { BrandingService } from './core/branding.service';
import { RuntimeConfigService } from './core/runtime-config.service';
import { authInterceptor, errorInterceptor, refreshInterceptor } from './core/interceptors';
import { environment } from '../environments/environment';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top', anchorScrolling: 'enabled' })
    ),
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor, refreshInterceptor])),
    provideIonicAngular({ mode: 'md', animated: true }),
    provideServiceWorker('ngsw-worker.js', {
      enabled: environment.production,
      registrationStrategy: 'registerWhenStable:30000'
    }),
    provideAppInitializer(async () => {
      await inject(RuntimeConfigService).init();
      await inject(BrandingService).init();
    }),
    { provide: LOCALE_ID, useValue: 'es-CO' }
  ]
};
