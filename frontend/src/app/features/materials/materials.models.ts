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
