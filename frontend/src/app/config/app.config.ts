import { provideAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { ApplicationConfig } from '@angular/core';
import { provideRouter as baseRouter } from '@angular/router';
import { provideHttpClient as baseHttp } from '@angular/common/http';

import { providePrimeNG } from 'primeng/config';
import { definePreset } from '@primeng/themes';
import Aura from '@primeng/themes/aura';

import { rotas } from './app.routes';
import { autenticacaoInterceptor } from '../core/interceptor-authenticacao';

// Predefinição visual FlowTask: mesma paleta do styles.scss.
const PredefinicaoFlowTask = definePreset(Aura, {
  name: 'flowtask',
  semantic: {
    primary: {
      50: '#F0F1FF',
      100: '#DDE1FF',
      200: '#B9C2FF',
      300: '#A5B4FC',
      400: '#919FFD',
      500: '#8293FF',
      600: '#6B7CE0',
      700: '#5462B5',
      800: '#3D4888',
      900: '#262E56',
      950: '#141730'
    }
  }
});

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(rotas),
    provideHttpClient(withInterceptors([autenticacaoInterceptor])),
    provideAnimations(),
    providePrimeNG({
      theme: {
        preset: PredefinicaoFlowTask,
        options: {
          darkModeSelector: '.app-dark',
          ripple: false
        }
      }
    })
  ]
};

// exporta aliases para evitar ciclos de importação em outros arquivos
export { baseRouter, baseHttp };
