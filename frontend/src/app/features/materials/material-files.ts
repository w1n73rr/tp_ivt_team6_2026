import { DestroyRef, Injectable, inject } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Material } from './materials.models';

export function fileName(response: HttpResponse<Blob>, item: Material): string {
  const disposition = response.headers.get('Content-Disposition') ?? '';
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1];
  const plain = /filename="([^"]+)"/i.exec(disposition)?.[1];
  let value = plain ?? `${item.name}.${item.format.toLowerCase()}`;
  if (encoded) { try { value = decodeURIComponent(encoded); } catch { /* Use the safe fallback. */ } }
  return value.replace(/[\x00-\x1f\x7f/\\]/g, '_');
}
export async function previewBlob(blob: Blob, format: Material['format']): Promise<Blob> {
  if (format !== 'DOCX') return new Blob([blob], {type: format === 'PDF' ? 'application/pdf' : 'text/plain;charset=utf-8'});
  const arrayBuffer = await new Promise<ArrayBuffer>((resolve, reject) => {
    const reader = new FileReader(); reader.onload = () => resolve(reader.result as ArrayBuffer);
    reader.onerror = () => reject(new Error('Не удалось прочитать документ')); reader.readAsArrayBuffer(blob);
  });
  const {default: JSZip} = await import('jszip');
  const zip = await JSZip.loadAsync(arrayBuffer);
  const entry = zip.file('word/document.xml');
  if (!entry) throw new Error('В DOCX отсутствует текст документа');
  const xml = await new Promise<string>((resolve, reject) => {
    const chunks: Uint8Array[] = []; let size = 0;
    const stream = (entry as typeof entry & {internalStream(type: 'uint8array'): import('jszip').JSZipStreamHelper<Uint8Array>}).internalStream('uint8array');
    stream.on('data', chunk => {
      size += chunk.length;
      if (size > 20 * 1024 * 1024) { stream.pause(); reject(new Error('Документ слишком большой для предпросмотра. Скачайте оригинал.')); return; }
      chunks.push(chunk);
    }).on('error', reject).on('end', () => {
      const bytes = new Uint8Array(size); let offset = 0;
      chunks.forEach(chunk => { bytes.set(chunk, offset); offset += chunk.length; });
      resolve(new TextDecoder().decode(bytes));
    }).resume();
  });
  const document = new DOMParser().parseFromString(xml, 'application/xml');
  if (document.querySelector('parsererror')) throw new Error('Повреждённый DOCX');
  const value = Array.from(document.getElementsByTagNameNS('*', 'p')).map(p =>
    Array.from(p.getElementsByTagNameNS('*', 't')).map(t => t.textContent ?? '').join('')
  ).join('\n');
  return new Blob(['Предпросмотр DOCX: только текст, без оформления и изображений.\nОригинал можно скачать на странице материалов.\n\n'+value], {type: 'text/plain;charset=utf-8'});
}
@Injectable({providedIn: 'root'})
export class MaterialFiles {
  private readonly urls = new Map<string, number>();
  constructor() { inject(DestroyRef).onDestroy(() => {
    for (const [url, timer] of this.urls) { window.clearInterval(timer); URL.revokeObjectURL(url); }
    this.urls.clear();
  }); }
  reservePreview(): Window | null {
    const tab = window.open('about:blank', '_blank');
    if (tab) { tab.opener = null; tab.document.title = 'Просмотр материала'; tab.document.body.textContent = 'Загрузка документа…'; }
    return tab;
  }
  async preview(response: HttpResponse<Blob>, item: Material, tab: Window): Promise<void> {
    if (!response.body) throw new Error('Empty file response');
    const blob = await previewBlob(response.body, item.format);
    if (tab.closed) return;
    const url = URL.createObjectURL(blob);
    tab.location.replace(url);
    const timer = window.setInterval(() => {
      if (tab.closed) { URL.revokeObjectURL(url); window.clearInterval(timer); this.urls.delete(url); }
    }, 1000);
    this.urls.set(url, timer);
  }
  download(response: HttpResponse<Blob>, item: Material): void {
    if (!response.body) throw new Error('Empty file response');
    const url = URL.createObjectURL(response.body);
    const link = document.createElement('a'); link.href = url; link.download = fileName(response, item);
    document.body.append(link); link.click(); link.remove();
    const timer = window.setTimeout(() => { URL.revokeObjectURL(url); this.urls.delete(url); }, 30000);
    this.urls.set(url, timer);
  }
}
