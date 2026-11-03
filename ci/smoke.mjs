import { randomUUID } from 'node:crypto';
import { pathToFileURL } from 'node:url';

// Только одноразовый стенд CI: сценарий создаёт пользователя и материал.
export async function smoke(baseUrl) {
  const base = baseUrl.replace(/\/$/, '');
  async function request(method, path, expected, payload, token) {
    const headers = { Origin: base };
    if (token) headers.Authorization = `Bearer ${token}`;
    let body = payload;
    if (payload && !(payload instanceof FormData)) {
      headers['Content-Type'] = 'application/json';
      body = JSON.stringify(payload);
    }
    let response;
    try {
      response = await fetch(base + path, { method, headers, body, redirect: 'manual', signal: AbortSignal.timeout(30_000) });
    } catch {
      throw new Error(`${method} ${path}: ошибка соединения или превышено время ожидания`);
    }
    if (response.status !== expected) throw new Error(`${method} ${path}: ожидался ${expected}, получен ${response.status}`);
    if (response.headers.get('content-type')?.includes('application/json')) {
      try { return await response.json(); } catch { throw new Error(`${method} ${path}: некорректный JSON`); }
    }
    return Buffer.from(await response.arrayBuffer());
  }
  const page = await request('GET', '/', 200);
  if (!Buffer.isBuffer(page) || !page.includes(Buffer.from('<app-root'))) throw new Error('nginx не отдаёт приложение Angular');
  await request('GET', '/api/materials', 401);
  const suffix = randomUUID().replaceAll('-', '').slice(0, 12);
  const username = `ci_${suffix}`, password = 'CiOnlyPassword123!';
  const registered = await request('POST', '/api/auth/register', 200, { username, email: `${username}@example.com`, password });
  if (!registered.token) throw new Error('Регистрация не вернула токен');
  const login = await request('POST', '/api/auth/login', 200, { username, password });
  if (!login.token) throw new Error('Вход не вернул токен');
  const token = login.token;
  await request('POST', '/api/auth/login', 401, { username, password: 'wrong-password' });
  await request('GET', '/api/materials', 200, undefined, token);
  const content = Buffer.from('CI storage round-trip: PostgreSQL and SeaweedFS.\n');
  const form = new FormData();
  form.append('metadata', new Blob([JSON.stringify({ name: `CI material ${suffix}`, subjectName: 'CI subject',
    description: '', tagIds: [], newTagNames: [] })], { type: 'application/json' }));
  form.append('file', new Blob([content], { type: 'text/plain' }), 'ci.txt');
  const uploaded = await request('POST', '/api/materials', 201, form, token);
  const id = uploaded.material?.id;
  if (id === undefined || id === null) throw new Error('Загрузка не вернула ID материала');
  try {
    const list = await request('GET', '/api/materials', 200, undefined, token);
    if (!list.items.some(item => item.id === id)) throw new Error('Загруженный материал отсутствует в списке');
    const downloaded = await request('GET', `/api/materials/${id}/download`, 200, undefined, token);
    if (!Buffer.isBuffer(downloaded) || !downloaded.equals(content)) throw new Error('Скачанный файл отличается от загруженного');
  } finally {
    await request('DELETE', `/api/materials/${id}`, 204, undefined, token);
  }
  await request('GET', `/api/materials/${id}`, 404, undefined, token);
  console.log('Успех: интерфейс, доступ, регистрация и вход, БД, загрузка, скачивание и удаление');
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const index = process.argv.indexOf('--base-url');
  try {
    if (index < 0 || !process.argv[index + 1]) throw new Error('Требуется --base-url');
    await smoke(process.argv[index + 1]);
  } catch (error) {
    // Не выводим тела ответов, заголовки или токены в публичные журналы.
    console.error(`Ошибка smoke: ${error.message}`);
    process.exitCode = 1;
  }
}
