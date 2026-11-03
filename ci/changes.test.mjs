import { test } from 'node:test';
import assert from 'node:assert/strict';
import { choose, changedPaths } from './changes.mjs';

test('Изменение интерфейса не запускает сервер', () => {
  assert.deepEqual(choose(['frontend/src/app.ts'], 'feature', 'push'),
    { frontend: true, backend: false, integration: false, browser: false });
});
test('Изменение сервера не запускает интерфейс', () => {
  assert.deepEqual(choose(['backend/src/App.java'], 'feature', 'push'),
    { frontend: false, backend: true, integration: false, browser: false });
});
test('Документация не запускает сборки feature-ветки', () => {
  assert.ok(Object.values(choose(['README.md', 'docs/ci.md'], 'feature', 'push')).every(v => !v));
});
test('Общие изменения включают интеграцию', () => {
  for (const path of ['docker-compose.yml', '.github/workflows/ci.yml', 'backend/backend-api.yaml',
    'frontend/Dockerfile', 'ci/changes.mjs', 'unknown']) {
    const result = choose([path], 'feature', 'push');
    assert.ok(result.frontend && result.backend && result.integration, path);
  }
});
test('Dev проверяется полностью без браузера, master с браузером', () => {
  assert.ok(choose(['README.md'], 'refs/heads/dev', 'push').integration);
  assert.equal(choose([], 'refs/heads/dev', 'push').browser, false);
  assert.ok(choose([], 'refs/heads/master', 'push').browser);
});
test('Изменение браузерных тестов включает весь стенд и браузер', () => {
  assert.deepEqual(choose(['ui-tests/build.gradle.kts'], 'feature', 'push'),
    { frontend: true, backend: true, integration: true, browser: true });
});
test('Ручной запуск включает интеграцию и учитывает полный прогон', () => {
  assert.ok(choose([], 'feature', 'workflow_dispatch').integration);
  assert.equal(choose([], 'feature', 'workflow_dispatch').browser, false);
  assert.ok(choose([], 'feature', 'workflow_dispatch', true).browser);
});
test('Первый push использует merge-base, пути разделяются нулевым байтом', () => {
  const calls = [];
  const git = args => { calls.push(args); return calls.length === 1 ? 'base\n' : 'frontend/a b.ts\0backend/deleted.java\0'; };
  assert.deepEqual(changedPaths('0000', 'head', git), ['frontend/a b.ts', 'backend/deleted.java']);
  assert.deepEqual(calls[1], ['diff', '--name-only', '-z', '--no-renames', 'base', 'head']);
});
test('Недоступная база не позволяет пропустить интеграцию', () => {
  const paths = changedPaths('base', 'head', () => { throw new Error('git failed'); });
  assert.ok(choose(paths, 'feature', 'push').integration);
});
