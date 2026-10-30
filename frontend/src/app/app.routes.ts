import { MaterialUpload } from './features/materials/upload/upload';
import { Materials } from './features/materials/materials';
import { Routes } from '@angular/router';

import { Login } from './features/auth/login/login';
import { Register } from './features/auth/register/register';
import { Dashboard } from './features/dashboard/dashboard';
import { NotFound } from './not-found/not-found';
import { Generation } from './features/generation/generation';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'login',
    pathMatch: 'full',
  },
  {
    path: 'login',
    component: Login,
  },
  {
    path: 'register',
    component: Register,
  },
  {
    path: 'dashboard',
    component: Dashboard,
  },
  { path: 'materials/upload', component: MaterialUpload },
  { path: 'materials/:materialId/generate', component: Generation },
  { path: 'materials', component: Materials },
  {
    path: '**',
    component: NotFound,
  },
];
