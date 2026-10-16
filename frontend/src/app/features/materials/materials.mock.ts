import { Injectable, signal } from '@angular/core';
import { Observable, defer, of, throwError, delay, timer, map } from 'rxjs';
export type MaterialFormat = 'PDF' | 'DOCX' | 'TXT';
export interface Material {
  id: string;
  name: string;
  format: MaterialFormat;
  sizeBytes: number;
  subjectId: string;
  subjectName: string;
  uploadedAt: string;
  description?: string;
  tags?: string[];
}
export interface MaterialsResponse {
  items: Material[];
}
@Injectable({ providedIn: 'root' })
export class MaterialsMock {
  readonly records = signal<Material[]>([
    {
      id: 'demo-1',
      name: 'Операционные системы. Лекция 1',
      format: 'PDF',
      sizeBytes: 3355443,
      subjectId: 'os',
      subjectName: 'Операционные системы',
      uploadedAt: '2026-10-08T12:00:00Z',
    },
    {
      id: 'demo-2',
      name: 'Базы данных. Лекция 3 — Нормализация',
      format: 'PDF',
      sizeBytes: 2516582,
      subjectId: 'db',
      subjectName: 'Базы данных',
      uploadedAt: '2026-10-01T12:30:00Z',
    },
    {
      id: 'demo-3',
      name: 'SQL: запросы и соединения таблиц',
      format: 'TXT',
      sizeBytes: 52224,
      subjectId: 'db',
      subjectName: 'Базы данных',
      uploadedAt: '2026-09-28T12:00:00Z',
    },
    {
      id: 'demo-4',
      name: 'Индексы и производительность',
      format: 'DOCX',
      sizeBytes: 860160,
      subjectId: 'db',
      subjectName: 'Базы данных',
      uploadedAt: '2026-09-25T12:00:00Z',
    },
    {
      id: 'demo-5',
      name: 'Конспект: ключи и связи таблиц',
      format: 'TXT',
      sizeBytes: 36864,
      subjectId: 'db',
      subjectName: 'Базы данных',
      uploadedAt: '2026-09-20T12:00:00Z',
    },
  ]);
  upload(
    file: File,
    data: { name: string; subjectName: string; description: string; tags: string[] },
    fail = false,
  ): Observable<Material> {
    return timer(600).pipe(
      map(() => {
        if (fail) {
          throw new Error('Демонстрационная ошибка загрузки');
        }
        const item: Material = {
          id: crypto.randomUUID(),
          name: data.name,
          format: file.name.split('.').pop()!.toUpperCase() as MaterialFormat,
          sizeBytes: file.size,
          subjectId: 'demo-' + data.subjectName,
          subjectName: data.subjectName,
          uploadedAt: new Date().toISOString(),
          description: data.description,
          tags: [...data.tags],
        };
        this.records.update((items) => [item, ...items]);
        return { ...item };
      }),
    );
  }
  list(mode: 'data' | 'empty' | 'error' = 'data'): Observable<MaterialsResponse> {
    return defer(() =>
      mode === 'error'
        ? throwError(() => new Error('Демонстрационная ошибка получения списка'))
        : of({ items: mode === 'empty' ? [] : this.records().map((item) => ({ ...item })) }),
    ).pipe(delay(350));
  }
}
