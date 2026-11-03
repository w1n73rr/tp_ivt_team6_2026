import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { MatDialog, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';
import { Materials } from './materials';
import { MaterialEditor } from './material-dialogs';
import { MaterialsApi } from './materials.api';
import { AuthService } from '../../core/auth/auth.service';
import { Material } from './materials.models';
const item: Material = {id: 'one', name: 'Лекция', format: 'TXT', sizeBytes: 80, subjectId: 's', subjectName: 'Базы данных', uploadedAt: '2026-10-20T00:00:00Z'};
describe('Material actions', () => {
 let http: HttpTestingController;
 const dialogs = {open: vi.fn()};
 beforeEach(() => {
  sessionStorage.clear();
  TestBed.configureTestingModule({providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), {provide: MatDialog, useValue: dialogs}]});
  TestBed.overrideComponent(Materials, {add: {providers: [{provide: MatDialog, useValue: dialogs}]}});
  http = TestBed.inject(HttpTestingController);
  TestBed.inject(AuthService).login({username: 'VA', password: 'test'}).subscribe();
  http.expectOne('/api/auth/login').flush({token: 'jwt'});
  dialogs.open.mockReturnValue({afterClosed: () => of(true)});
 });
 afterEach(() => {http.verify();sessionStorage.clear();});
 function page() { const f=TestBed.createComponent(Materials); f.detectChanges(); http.expectOne('/api/materials').flush({items: [item,{...item,id:'two',name:'Вторая'}]}); return f.componentInstance; }
 it('cancels deletion without any server request', () => {
  const p=page(); dialogs.open.mockReturnValue({afterClosed: () => of(false)}); p.remove([item]);
  http.expectNone('/api/materials/one'); expect(p.items()).toHaveLength(2);
 });
 it('removes an item only after successful authenticated DELETE', () => {
  const p=page(); p.remove([item]); const r=http.expectOne('/api/materials/one');
  expect(r.request.method).toBe('DELETE'); expect(r.request.headers.get('Authorization')).toBe('Bearer jwt');
  expect(p.items()).toHaveLength(2); expect(p.busy()).toBe(true); r.flush(null,{status:204,statusText:'No Content'});
  expect(p.items().map(x=>x.id)).toEqual(['two']); expect(p.busy()).toBe(false);
 });
 it('preserves failed items and reports partial batch deletion', () => {
  const p=page();p.selected=new Set(['one','two']);p.removeSelected();
  http.expectOne('/api/materials/one').flush(null,{status:204,statusText:'No Content'});
  http.expectOne('/api/materials/two').flush({}, {status:503,statusText:'Unavailable'});
  expect(p.items().map(x=>x.id)).toEqual(['two']);expect(p.selected.has('two')).toBe(true);
  expect(p.message()).toContain('Не удалено: 1');
 });
 it('selects and deletes only visible results, and clears selection on filter changes', () => {
  const p=page(); p.toggleAll(); expect(p.selected.size).toBe(2);
  p.setQuery('вТОРАЯ'); expect(p.selected.size).toBe(0);
  p.toggleAll(); expect([...p.selected]).toEqual(['two']);
  p.removeSelected(); http.expectNone('/api/materials/one');
  http.expectOne('/api/materials/two').flush(null,{status:204,statusText:'No Content'});
  expect(p.items().map(x=>x.id)).toEqual(['one']); expect(p.visibleItems()).toEqual([]);
  p.resetFilters(); expect(p.visibleItems()).toHaveLength(1);
 });
 it('drops selection when an edited material stops matching the search', () => {
  const p=page(); p.setQuery('Лекция'); p.toggleAll();
  dialogs.open.mockReturnValue({afterClosed: () => of({...item,name:'Практика'})});
  p.edit(item); http.expectOne('/api/materials/one').flush({material:item,tags:[]});
  expect(p.visibleItems()).toEqual([]); expect(p.selected.size).toBe(0);
  expect(p.items()[0].name).toBe('Практика');
 });
 it('requests protected files as blobs with no JWT in the URL', () => {
  TestBed.inject(MaterialsApi).file('one').subscribe(r=>expect(r.body?.size).toBe(7));
  const r=http.expectOne('/api/materials/one/download');expect(r.request.responseType).toBe('blob');expect(r.request.headers.get('Authorization')).toBe('Bearer jwt');r.flush(new Blob(['lecture']));
 });
});
describe('Material editor', () => {
 const ref={close:vi.fn(),disableClose:false};
 beforeEach(() => { sessionStorage.clear();ref.close.mockClear();TestBed.configureTestingModule({imports:[MaterialEditor],providers:[provideHttpClient(),provideHttpClientTesting(),{provide:MAT_DIALOG_DATA,useValue:{material:item,tags:[{name:'SQL'}],description:'Описание'}},{provide:MatDialogRef,useValue:ref}]}); });
 afterEach(()=>TestBed.inject(HttpTestingController).verify());
 it('keeps entered fields and the dialog after a conflict, and allows retry', () => {
  const p=TestBed.createComponent(MaterialEditor).componentInstance;const h=TestBed.inject(HttpTestingController);
  p.form.setValue({name:' Новая лекция ',subjectName:' Базы данных '});p.save();p.save();
  const r=h.expectOne('/api/materials/one');expect(r.request.method).toBe('PATCH');expect(r.request.body).toEqual({name:'Новая лекция',subjectName:'Базы данных'});
  r.flush({}, {status:409,statusText:'Conflict'});expect(ref.close).not.toHaveBeenCalled();expect(p.form.controls.name.value).toBe(' Новая лекция ');expect(p.saving()).toBe(false);
  p.form.controls.name.setValue('Другая лекция');p.save();h.expectOne('/api/materials/one').flush({...item,name:'Другая лекция'});expect(ref.close).toHaveBeenCalledWith({...item,name:'Другая лекция'});
 });
 it('does not save blank or oversized metadata',()=>{const p=TestBed.createComponent(MaterialEditor).componentInstance;p.form.controls.name.setValue(' ');p.save();TestBed.inject(HttpTestingController).expectNone('/api/materials/one');expect(p.error()).not.toBe('');});
});
