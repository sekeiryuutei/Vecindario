import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import { appConfig } from './app/app.config';
import './app/core/icons';

bootstrapApplication(AppComponent, appConfig).catch((error) => {
  console.error('No fue posible iniciar la aplicación', error);
});
