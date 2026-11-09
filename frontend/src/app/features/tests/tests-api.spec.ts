import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AuthService } from '../../core/auth/auth.service';
import { ApiTestsSource } from './tests-source';
describe('Saved quiz HTTP mutations', () => {
 let http: HttpTestingController; let api: ApiTestsSource;
 beforeEach(() => { TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AuthService, useValue: { token: 'fixture-jwt' } }] }); http=TestBed.inject(HttpTestingController); api=TestBed.inject(ApiTestsSource); });
 afterEach(() => http.verify());
 it('PATCH sends only a trimmed name and Bearer JWT, and uses the server result', () => {
  api.rename('selected-id','  Новое имя  ').subscribe(result => expect(result.name).toBe('Новое имя'));
  const req=http.expectOne('/api/quizzes/selected-id'); expect(req.request.method).toBe('PATCH'); expect(req.request.headers.get('Authorization')).toBe('Bearer fixture-jwt'); expect(req.request.body).toEqual({name:'Новое имя'}); req.flush({id:'selected-id',name:'Новое имя'});
 });
 it('DELETE targets only the selected ID and accepts 204', () => {
  const next=vi.fn(); api.remove('selected-id').subscribe(next); const req=http.expectOne('/api/quizzes/selected-id'); expect(req.request.method).toBe('DELETE'); expect(req.request.headers.get('Authorization')).toBe('Bearer fixture-jwt'); req.flush(null,{status:204,statusText:'No Content'}); expect(next).toHaveBeenCalledOnce();
 });
 it.each([400,401,404,409,503])('preserves server error %i without inventing a success', status => {
  const next=vi.fn(),error=vi.fn(); api.rename('selected','Имя').subscribe({next,error});http.expectOne('/api/quizzes/selected').flush({code:'ERROR'},{status,statusText:'Error'}); expect(next).not.toHaveBeenCalled(); expect(error.mock.calls[0][0].status).toBe(status);
 });
});
