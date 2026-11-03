import { inject } from '@angular/core';
import { ActivatedRoute, CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const testsAccess: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.token?.trim() ? true : inject(Router).createUrlTree(['/login'], { queryParams: { returnTo: '/tests' } });
};

// Allow only an explicitly supported local destination, never an arbitrary URL.
export function authDestination(route: ActivatedRoute): string {
  return route.snapshot.queryParamMap.get('returnTo') === '/tests' ? '/tests' : '/dashboard';
}
