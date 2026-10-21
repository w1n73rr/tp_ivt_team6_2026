import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { fileName, previewBlob } from './material-files';
import { Material } from './materials.models';
import JSZip from 'jszip';
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

