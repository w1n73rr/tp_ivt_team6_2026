import { GenerationJob } from './generation-source';
const stages: Record<string, string> = {
  PREPARING: 'Подготавливаем материал', GENERATING: 'Генерируем вопросы',
  VALIDATING: 'Проверяем вопросы', SAVING: 'Сохраняем тест',
};
const details: Record<string, string> = {
  EXTRACTING: 'Читаем текст материала', PREPARING_PROMPT: 'Подготавливаем запрос к модели',
  WAITING_FOR_MODEL: 'Ожидаем ответ модели', RECEIVING_RESPONSE: 'Получаем ответ модели',
  VALIDATING: 'Проверяем структуру вопросов', REVIEWING_CONTENT: 'Проверяем содержание вопросов',
  REVISING: 'Исправляем вопросы после проверки', VALIDATED: 'Вопросы проверены, подготавливаем сохранение',
};
export function generationStage(job: GenerationJob): string {
  if (job.status === 'QUEUED') return 'Генерация в очереди';
  if (job.stage === 'SAVING') return stages['SAVING'];
  return details[job.detailStage ?? ''] ?? stages[job.stage ?? ''] ?? 'Ожидаем результат генерации';
}
const errors: Record<string, string> = {
  DOCUMENT_TOO_MANY_PAGES: 'В PDF больше 10 страниц. Загрузите документ до 10 страниц и повторите генерацию.',
  DOCUMENT_TOO_LARGE: 'Файл слишком большой. Допустимый размер — до 10 МБ.',
  DOCUMENT_TEXT_TOO_LARGE: 'В материале слишком много текста. Разделите его на несколько файлов и выберите один.',
  DOCUMENT_NO_TEXT: 'В документе не найден текст. Для скана сначала нужно распознавание текста.',
  DOCUMENT_PROTECTED: 'Документ защищён. Загрузите доступную для чтения копию.',
  DOCUMENT_UNREADABLE: 'Не удалось прочитать документ. Проверьте файл или загрузите его в другом поддерживаемом формате.',
  MODEL_OUTPUT_LIMIT: 'Ответ модели превысил лимит вывода. Попробуйте повторить генерацию или сократить материал.',
  MODEL_TIMEOUT: 'Модель не успела ответить. Попробуйте повторить генерацию.',
  RATE_LIMITED: 'Сервис модели ограничил число запросов. Подождите и повторите генерацию.',
  NOT_CONFIGURED: 'Сервис генерации ещё не настроен. Обратитесь к администратору.',
  ML_NOT_CONFIGURED: 'Сервис генерации ещё не настроен. Обратитесь к администратору.',
  INVALID_MODEL_OUTPUT: 'Модель вернула некорректный результат. Попробуйте повторить генерацию.',
  ML_INVALID_RESULT: 'Модель вернула некорректный результат. Попробуйте повторить генерацию.',
  QUALITY_REJECTED: 'Сгенерированные вопросы не прошли проверку качества. Попробуйте повторить генерацию.',
};
export function generationFailure(code?: string): string {
  return errors[code ?? ''] ?? 'Не удалось обработать материал или сохранить тест. Попробуйте повторить генерацию.';
}

export function generationStep(job: GenerationJob): number {
  if (job.status === 'QUEUED' || job.stage === 'PREPARING') return 0;
  if (job.detailStage === 'REVISING') return 1;
  if (job.stage === 'VALIDATING' || job.stage === 'SAVING' || job.status === 'COMPLETED') return 2;
  return job.stage === 'GENERATING' ? 1 : 0;
}
