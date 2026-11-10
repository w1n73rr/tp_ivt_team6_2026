import { generationFailure, generationStage, generationStep } from './generation-status';
import { GenerationJob } from './generation-source';
const job: GenerationJob = { id: 'job', materialId: 'material', status: 'RUNNING', version: 1, stage: 'VALIDATING' };
describe('Real generation stages and safe errors', () => {
  it.each([
    ['EXTRACTING', 'Читаем текст материала'], ['PREPARING_PROMPT', 'Подготавливаем запрос к модели'],
    ['WAITING_FOR_MODEL', 'Ожидаем ответ модели'], ['RECEIVING_RESPONSE', 'Получаем ответ модели'],
    ['VALIDATING', 'Проверяем структуру вопросов'], ['REVIEWING_CONTENT', 'Проверяем содержание вопросов'],
    ['REVISING', 'Исправляем вопросы после проверки'], ['VALIDATED', 'Вопросы проверены, подготавливаем сохранение'],
  ])('shows backend detail %s', (detailStage, text) => expect(generationStage({ ...job, detailStage })).toBe(text));
  it('keeps queue/saving authoritative and unknown stages safe', () => {
    expect(generationStage({ ...job, status: 'QUEUED' })).toBe('Генерация в очереди');
    expect(generationStage({ ...job, stage: 'SAVING', detailStage: 'VALIDATED' })).toBe('Сохраняем тест');
    expect(generationStage({ ...job, detailStage: 'future-detail' })).toBe('Проверяем вопросы');
    expect(generationStage({ ...job, stage: 'future-stage' })).toBe('Ожидаем результат генерации');
  });
  it('explains page/output limits without exposing arbitrary server text', () => {
    expect(generationFailure('DOCUMENT_TOO_MANY_PAGES')).toContain('10 страниц');
    expect(generationFailure('MODEL_OUTPUT_LIMIT')).toContain('лимит вывода');
    expect(generationFailure('internal stacktrace')).not.toContain('stacktrace');
  });
});

describe('Generation step indicator', () => {
 it('shows actual phases and allows a return to generation after review', () => {
  expect(generationStep({...job,status:'QUEUED'})).toBe(0);
  expect(generationStep({...job,stage:'GENERATING'})).toBe(1);
  expect(generationStep({...job,stage:'VALIDATING',detailStage:'REVIEWING_CONTENT'})).toBe(2);
  expect(generationStep({...job,stage:'GENERATING',detailStage:'REVISING'})).toBe(1);
  expect(generationStep({...job,stage:'SAVING'})).toBe(2);
 });
});
