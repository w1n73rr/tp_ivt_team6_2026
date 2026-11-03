import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { authDestination } from './tests-access';
describe('Safe generation login return', () => {
  it.each([
    ['/materials/b32b20bc-143c-4a72-a833-2797896e3926/generate', '/materials/b32b20bc-143c-4a72-a833-2797896e3926/generate'],
    ['https://example.com', '/dashboard'], ['//example.com', '/dashboard'],
    ['/materials/../login/generate', '/dashboard'], ['/materials/id/generate?redirect=https://example.com', '/dashboard'],
  ])('validates return %s', (destination, expected) => {
    const route = { snapshot: { queryParamMap: convertToParamMap({ returnTo: destination }) } } as ActivatedRoute;
    expect(authDestination(route)).toBe(expected);
  });
});
