import { execFileSync } from 'node:child_process';
import { appendFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

export function choose(paths, ref, event, full = false) {
  let frontend = false, backend = false, integration = false, browser = false;
  if (event === 'workflow_dispatch' || ['refs/heads/dev', 'refs/heads/master'].includes(ref)) {
    frontend = backend = integration = true;
    browser = ref === 'refs/heads/master' || full;
  } else {
    for (const path of paths) {
      if (['README.md', 'README', 'LICENSE'].includes(path) || path.startsWith('docs/')) continue;
      if (path.startsWith('ui-tests/')) integration = browser = true;
      else if (['frontend/Dockerfile', 'frontend/.dockerignore', 'backend/Dockerfile',
        'backend/.dockerignore', 'backend/backend-api.yaml'].includes(path)) integration = true;
      else if (path.startsWith('frontend/')) frontend = true;
      else if (path.startsWith('backend/')) backend = true;
      else integration = true; // Неизвестное изменение проверяем консервативно.
    }
  }
  if (integration) frontend = backend = true;
  return { frontend, backend, integration, browser };
}

export function changedPaths(before, after, git = args => execFileSync('git', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] })) {
  try {
    if (!before || /^0+$/.test(before)) before = git(['merge-base', after, 'origin/dev']).trim();
    return git(['diff', '--name-only', '-z', '--no-renames', before, after]).split('\0').filter(Boolean);
  } catch {
    return ['unknown-diff'];
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const { BEFORE, AFTER, REF, EVENT, FULL, GITHUB_OUTPUT } = process.env;
  const selected = choose(changedPaths(BEFORE, AFTER), REF, EVENT, FULL === 'true');
  const labels = { frontend: 'Интерфейс', backend: 'Сервер', integration: 'Compose и smoke', browser: 'Браузерные тесты' };
  for (const [key, value] of Object.entries(selected)) {
    appendFileSync(GITHUB_OUTPUT, `${key}=${value}\n`);
    console.log(`${labels[key]}: ${value ? 'запуск' : 'пропуск'}`);
  }
}
