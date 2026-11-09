import { randomUUID, randomBytes } from 'node:crypto';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { basename, dirname } from 'node:path';
import { parseArgs } from 'node:util';
import { pathToFileURL } from 'node:url';

function require(condition, message) { if (!condition) throw new Error(message); }
const safeCode = code => typeof code === 'string' && /^[A-Z_]{1,64}$/.test(code) ? code : 'UNKNOWN';

export function verifyResult(status, quizzes, expectUnconfigured = false) {
  require(Array.isArray(quizzes?.items), 'API не вернул список тестов');
  if (expectUnconfigured) {
    require(status.status === 'FAILED' && status.error?.code === 'NOT_CONFIGURED', 'Ожидался FAILED/NOT_CONFIGURED');
    require(quizzes.items.length === 0, 'После ошибки не должно быть сохранённого теста');
    return;
  }
  require(status.status === 'COMPLETED', `Генерация не завершилась успешно: ${safeCode(status.error?.code)}`);
  require(quizzes.items.length === 1 && quizzes.items[0].id === status.quizId, 'Ожидался ровно один сохранённый тест этой задачи');
  require(quizzes.items[0].questionCount === 5, 'В сохранённом тесте должно быть ровно 5 вопросов');
  require(quizzes.items[0].questionType === 'SINGLE_CHOICE', 'Неверный тип сохранённых вопросов');
}

// Создаёт учётные записи и данные. Использовать только одноразовый стенд.
export async function checkGeneration({ baseUrl, file, timeout = 450, expectUnconfigured = false, report = 'ci/test-results/generation.json' }) {
  const base = baseUrl.replace(/\/$/, '');
  const started = Date.now();
  const evidence = { result: 'FAILED', questionCount: 5, difficulty: 'MEDIUM', questionType: 'SINGLE_CHOICE' };
  async function request(method, path, expected, payload, token, extra = {}) {
    const headers = { ...extra };
    if (token) headers.Authorization = `Bearer ${token}`;
    let body = payload;
    if (payload && !(payload instanceof FormData)) { body = JSON.stringify(payload); headers['Content-Type'] = 'application/json'; }
    let response;
    try { response = await fetch(base + path, { method, headers, body, redirect: 'manual', signal: AbortSignal.timeout(30_000) }); }
    catch { throw new Error(`${method} ${path}: ошибка соединения или тайм-аут`); }
    require(response.status === expected, `${method} ${path}: ожидался HTTP ${expected}, получен ${response.status}`);
    let value;
    try { const text = await response.text(); value = text ? JSON.parse(text) : null; }
    catch { throw new Error(`${method} ${path}: некорректный JSON`); }
    return { value, headers: response.headers };
  }
  async function register() {
    const username = `ty5_${randomUUID().replaceAll('-', '').slice(0, 16)}`;
    const { value } = await request('POST', '/api/auth/register', 200,
      { username, email: `${username}@example.org`, password: `T5!${randomBytes(20).toString('hex')}` });
    require(typeof value?.token === 'string' && value.token.length > 0, 'Регистрация не вернула токен');
    return value.token;
  }
  try {
    require(Number.isFinite(timeout) && timeout > 0, 'Тайм-аут должен быть положительным числом');
    const token = await register();
    const filename = file ? basename(file) : 'lesson.txt';
    require(/\.(pdf|docx|txt)$/i.test(filename) && !/[\r\n"]/.test(filename), 'Нужен файл PDF, DOCX или TXT');
    let content;
    try { content = file ? await readFile(file) : Buffer.from('Database transactions provide atomicity, consistency, isolation and durability. A primary key identifies a row. Foreign keys preserve references between tables. Indexes accelerate lookups. Normalization reduces redundancy. SQL SELECT reads rows, INSERT adds rows, UPDATE changes rows and DELETE removes rows.\n'); }
    catch { throw new Error('Не удалось прочитать тестовый материал'); }
    const form = new FormData();
    form.append('metadata', new Blob([JSON.stringify({ name: `TY5 ${randomUUID()}`, subjectName: 'TY5 check' })], { type: 'application/json' }));
    form.append('file', new Blob([content], { type: 'application/octet-stream' }), filename);
    const upload = (await request('POST', '/api/materials', 201, form, token)).value;
    const parameters = { materialId: upload.material.id, name: 'TY5 check', questionCount: 5, difficulty: 'MEDIUM', questionType: 'SINGLE_CHOICE' };
    const key = { 'Idempotency-Key': randomUUID() };
    await request('POST', '/api/quizzes', 400, { ...parameters, questionCount: 20 }, token, key);
    const accepted = await request('POST', '/api/quizzes', 202, parameters, token, key);
    const id = accepted.value?.id;
    require(typeof id === 'string' && /^[0-9a-f-]{36}$/i.test(id), 'Не получен UUID задачи');
    evidence.jobId = id;
    const path = `/api/generation-jobs/${id}`;
    require(accepted.headers.get('location') === path, 'Неверный Location для проверки статуса');
    require((await request('POST', '/api/quizzes', 202, parameters, token, key)).value.id === id, 'Повтор создал дубликат задачи');
    await request('POST', '/api/quizzes', 409, { ...parameters, questionCount: 10 }, token, key);
    await request('GET', path, 401);
    await request('GET', path, 404, undefined, await register());
    const deadline = Date.now() + timeout * 1000;
    let status, previous;
    while (true) {
      status = (await request('GET', path, 200, undefined, token)).value;
      require(status?.id === id && ['QUEUED', 'RUNNING', 'COMPLETED', 'FAILED'].includes(status.status), 'Некорректный статус задачи');
      evidence.status = status.status;
      if (status.status !== previous) { console.log(`Задача ${id}: ${status.status}`); previous = status.status; }
      if (['COMPLETED', 'FAILED'].includes(status.status)) break;
      require(Date.now() < deadline, 'Не дождались конечного статуса');
      await new Promise(resolve => setTimeout(resolve, 2000));
    }
    if (status.status === 'FAILED') evidence.errorCode = safeCode(status.error?.code);
    const quizzes = (await request('GET', '/api/quizzes', 200, undefined, token)).value;
    verifyResult(status, quizzes, expectUnconfigured);
    verifyResult(status, (await request('GET', '/api/quizzes', 200, undefined, token)).value, expectUnconfigured);
    evidence.result = 'PASSED';
    console.log(expectUnconfigured ? 'Успех: проверен NOT_CONFIGURED без сохранения; реальная модель не проверена' : 'Успех: реальная генерация, идемпотентность, доступ и повторное чтение сохранённого теста');
  } finally {
    evidence.durationSeconds = Math.round((Date.now() - started) / 1000);
    await mkdir(dirname(report), { recursive: true });
    await writeFile(report, JSON.stringify(evidence, null, 2) + '\n');
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const { values } = parseArgs({ options: { 'base-url': { type: 'string' }, file: { type: 'string' }, timeout: { type: 'string', default: '450' }, 'expect-unconfigured': { type: 'boolean', default: false } } });
    require(values['base-url'], 'Требуется --base-url');
    await checkGeneration({ baseUrl: values['base-url'], file: values.file, timeout: Number(values.timeout), expectUnconfigured: values['expect-unconfigured'] });
  } catch (error) { console.error(`Проверка генерации: ${error.message}`); process.exitCode = 1; }
}
