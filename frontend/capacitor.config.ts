import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.codevam.vecindad',
  appName: process.env['APP_NAME']?.trim() || 'Portal de copropiedad',
  webDir: 'dist/vecindad/browser',
  server: {
    androidScheme: 'https'
  },
  android: {
    allowMixedContent: false,
    backgroundColor: '#0f172a'
  },
  ios: {
    contentInset: 'automatic',
    backgroundColor: '#0f172a'
  },
  plugins: {
    SplashScreen: {
      launchShowDuration: 1200,
      backgroundColor: '#0f172a',
      showSpinner: false
    }
  }
};

export default config;
