import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { fileName, previewBlob } from './material-files';
import { Material } from './materials.models';
import JSZip from 'jszip';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from '../../core/auth/auth.service';
import { MaterialFiles } from './material-files';
const item:Material={id:'one',name:'Лекция',format:'PDF',sizeBytes:10,subjectId:'s',subjectName:'S',uploadedAt:''};
function text(blob:Blob):Promise<string>{return new Promise(resolve=>{const r=new FileReader();r.onload=()=>resolve(r.result as string);r.readAsText(blob);});}
describe('Protected document files',()=>{
 it('uses the server filename and sanitizes path characters',()=>{
  const response=new HttpResponse<Blob>({headers:new HttpHeaders({'Content-Disposition':"attachment; filename*=UTF-8''%D0%9B%D0%B5%D0%BA%D1%86%D0%B8%D1%8F%2F1.pdf"})});
  expect(fileName(response,item)).toBe('Лекция_1.pdf');expect(fileName(new HttpResponse(),item)).toBe('Лекция.pdf');
 });
 it('forces TXT preview to plain text rather than executable HTML',async()=>{
  const blob=await previewBlob(new Blob(['<script>alert(1)</script>'],{type:'text/html'}),'TXT');
  expect(blob.type).toBe('text/plain;charset=utf-8');expect(await text(blob)).toContain('<script>');
 });
 it('extracts DOCX text locally and displays it as plain text',async()=>{
  const zip=new JSZip();zip.file('word/document.xml','<w:document xmlns:w="urn:word"><w:p><w:r><w:t>Текст документа</w:t></w:r></w:p></w:document>');const data=await zip.generateAsync({type:'arraybuffer'});const blob=await previewBlob(new Blob([data]),'DOCX');expect(blob.type).toBe('text/plain;charset=utf-8');expect(await text(blob)).toContain('только текст');
 });
});

describe('Private preview lifecycle', () => {
 let auth: AuthService, files: MaterialFiles, tab: Window;
 beforeEach(() => {
  sessionStorage.clear();
  TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting()]});
  auth=TestBed.inject(AuthService);
  auth.login({username:'A',password:'password'}).subscribe();
  TestBed.inject(HttpTestingController).expectOne('/api/auth/login').flush({token:'fixture-jwt'});
  files=TestBed.inject(MaterialFiles);
  const stub={closed:false,opener:window,document:document.implementation.createHTMLDocument(),location:{replace:vi.fn()},close:vi.fn()};
  stub.close.mockImplementation(()=>{stub.closed=true;}); tab=stub as unknown as Window;
  vi.spyOn(window,'open').mockReturnValue(tab);
  vi.stubGlobal('URL',Object.assign(class extends URL {},{createObjectURL:vi.fn(()=>'blob:private'),revokeObjectURL:vi.fn()}));
 });
 afterEach(()=>{TestBed.inject(HttpTestingController).verify();vi.unstubAllGlobals();sessionStorage.clear();});
 it('revokes a loaded preview and closes its tab on logout',async()=>{
  files.reservePreview();
  await files.preview(new HttpResponse({body:new Blob(['private'])}),item,tab);
  expect(tab.opener).toBeNull(); expect(tab.location.replace).toHaveBeenCalledWith('blob:private');
  auth.logout(); expect(tab.close).toHaveBeenCalled();expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:private');
 });
 it('does not create a private blob URL if conversion finishes after logout',async()=>{
  files.reservePreview();
  const pending=files.preview(new HttpResponse({body:new Blob(['private'])}),item,tab);
  auth.logout();await pending;
  expect(URL.createObjectURL).not.toHaveBeenCalled();expect(tab.location.replace).not.toHaveBeenCalled();expect(tab.closed).toBe(true);
 });
});

