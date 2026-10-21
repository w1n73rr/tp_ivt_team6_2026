import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { MaterialsApi } from './materials.api';
import { AuthService } from '../../core/auth/auth.service';
describe('Materials HTTP contract', () => {
  beforeEach(() => { sessionStorage.clear(); TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }); });
  afterEach(() => { TestBed.inject(HttpTestingController).verify(); sessionStorage.clear(); });
  function login() {
    TestBed.inject(AuthService).login({username: 'VA', password: 'test'}).subscribe();
    TestBed.inject(HttpTestingController).expectOne('/api/auth/login').flush({token: 'test-jwt'});
  }
  it('sends JWT and requests a fresh list on every call', () => {
    login(); const api = TestBed.inject(MaterialsApi); const http = TestBed.inject(HttpTestingController);
    for (let i=0; i<2; i++) {
      api.list().subscribe(r => expect(r.items).toEqual([]));
      const req = http.expectOne('/api/materials'); expect(req.request.method).toBe('GET');
      expect(req.request.headers.get('Authorization')).toBe('Bearer test-jwt'); req.flush({items: []});
    }
  });
  it('does not turn a server stub into an empty list', () => {
    login(); const api = TestBed.inject(MaterialsApi);
    api.list().subscribe({next: () => { throw new Error('Unexpected success'); }, error: e => expect(api.errorMessage(e)).toContain('не реализовано')});
    TestBed.inject(HttpTestingController).expectOne('/api/materials').flush({}, {status: 501, statusText: 'Not Implemented'});
  });
  it('restores the same-tab session and clears it on logout', () => {
    login(); TestBed.resetTestingModule();
    TestBed.configureTestingModule({providers: [provideHttpClient(), provideHttpClientTesting()]});
    const auth = TestBed.inject(AuthService); expect(auth.username()).toBe('VA'); expect(auth.token).toBe('test-jwt');
    auth.logout(); expect(sessionStorage.getItem('exam-ai-session')).toBeNull();
    TestBed.inject(MaterialsApi).list().subscribe({error: e => expect(e.status).toBe(401)});
    TestBed.inject(HttpTestingController).expectNone('/api/materials');
  });
});
