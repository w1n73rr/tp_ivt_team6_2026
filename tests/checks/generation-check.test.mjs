import { test } from 'node:test';
import assert from 'node:assert/strict';
import { verifyResult, checkGeneration } from './generation-check.mjs';
import { mkdtemp, readFile, unlink, rmdir } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

test('Без ключа принимается только NOT_CONFIGURED без сохранения', () => {
  verifyResult({ status: 'FAILED', error: { code: 'NOT_CONFIGURED' } }, { items: [] }, true);
  for (const code of ['MODEL_TIMEOUT', 'DOCUMENT_NO_TEXT', 'ML_NOT_CONFIGURED'])
    assert.throws(() => verifyResult({ status: 'FAILED', error: { code } }, { items: [] }, true));
  assert.throws(() => verifyResult({ status: 'FAILED', error: { code: 'NOT_CONFIGURED' } }, { items: [{}] }, true));
});
test('Успех требует сохранённый тест правильного ID, числа и типа', () => {
  const status = { status: 'COMPLETED', quizId: 'quiz' };
  const quiz = { id: 'quiz', questionCount: 5, questionType: 'SINGLE_CHOICE' };
  verifyResult(status, { items: [quiz] });
  for (const items of [[], [quiz, quiz], [{ ...quiz, id: 'other' }], [{ ...quiz, questionCount: 3 }], [{ ...quiz, questionType: 'TRUE_FALSE' }]])
    assert.throws(() => verifyResult(status, { items }));
});
test('Отказ модели не превращается в зелёный результат и печатает только безопасный код', () => {
  assert.throws(() => verifyResult({ status: 'FAILED', error: { code: 'INVALID_MODEL_OUTPUT' } }, { items: [] }), /INVALID_MODEL_OUTPUT/);
  assert.throws(() => verifyResult({ status: 'FAILED', error: { code: 'secret=private' } }, { items: [] }), error => error.message.includes('UNKNOWN') && !error.message.includes('private'));
});

test('API-сценарий проходит HTTP-последовательность и не пишет токены в отчёт', async t => {
  const id = '11111111-1111-4111-8111-111111111111';
  let users = 0, starts = 0, reads = 0;
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    const path = new URL(url).pathname;
    const json = (value, status = 200, headers = {}) => new Response(JSON.stringify(value), { status, headers });
    if (path === '/api/auth/register') return json({ token: `private-token-${++users}` });
    if (path === '/api/materials') {
      assert.ok(options.body instanceof FormData);
      return json({ material: { id: 'material' } }, 201);
    }
    if (path === '/api/quizzes' && options.method === 'POST') {
      const body = JSON.parse(options.body);
      if (body.questionCount === 20) return json({}, 400);
      if (body.questionCount === 10) return json({}, 409);
      starts++;
      assert.ok(options.headers['Idempotency-Key']);
      return json({ id }, 202, { Location: `/api/generation-jobs/${id}` });
    }
    if (path.startsWith('/api/generation-jobs/')) {
      if (!options.headers.Authorization) return json({}, 401);
      if (options.headers.Authorization.endsWith('private-token-2')) return json({}, 404);
      return json({ id, status: 'COMPLETED', quizId: 'quiz' });
    }
    if (path === '/api/quizzes' && options.method === 'GET') {
      reads++;
      return json({ items: [{ id: 'quiz', questionCount: 5, questionType: 'SINGLE_CHOICE' }] });
    }
    throw new Error('Unexpected request');
  });
  const directory = await mkdtemp(join(tmpdir(), 'generation-check-'));
  const report = join(directory, 'result.json');
  try {
    await checkGeneration({ baseUrl: 'http://test.invalid', report });
    const text = await readFile(report, 'utf8');
    assert.equal(JSON.parse(text).result, 'PASSED');
    assert.equal(starts, 2); // Одна генерация и проверка идемпотентного повтора.
    assert.equal(reads, 2);
    assert.ok(!text.includes('private-token'));
  } finally { await unlink(report); await rmdir(directory); }
});
