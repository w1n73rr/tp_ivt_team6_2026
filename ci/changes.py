"""Choose checks conservatively from the complete pushed Git diff."""
import os
import subprocess


def choose(paths, ref, event):
    frontend = backend = integration = browser = False
    if event == 'workflow_dispatch' or ref in ('refs/heads/dev', 'refs/heads/master'):
        frontend = backend = integration = True
        browser = ref == 'refs/heads/master' or os.environ.get('FULL') == 'true'
    else:
        for path in paths:
            if path in ('README.md', 'README', 'LICENSE') or path.startswith('docs/'):
                continue
            if path.startswith('ui-tests/'):
                integration = browser = True
            elif path in ('frontend/Dockerfile', 'frontend/.dockerignore', 'backend/Dockerfile',
                          'backend/.dockerignore', 'backend/backend-api.yaml'):
                integration = True
            elif path.startswith('frontend/'):
                frontend = True
            elif path.startswith('backend/'):
                backend = True
            else:
                # Workflow, Compose, scripts, shared configuration and unknown paths.
                integration = True
    if integration:
        frontend = backend = True
    return dict(frontend=frontend, backend=backend, integration=integration, browser=browser)


def main():
    before, after = os.environ.get('BEFORE', ''), os.environ['AFTER']
    try:
        if not before or set(before) == {'0'}:
            # First push of a branch: compare its full contribution to dev.
            before = subprocess.check_output(['git', 'merge-base', after, 'origin/dev'], text=True).strip()
        data = subprocess.check_output(['git', 'diff', '--name-only', '-z', '--no-renames', before, after])
        paths = [p.decode('utf-8', errors='replace') for p in data.split(b'\0') if p]
    except subprocess.CalledProcessError:
        paths = ['unknown-diff']  # No trustworthy base: never silently skip checks.
    selected = choose(paths, os.environ['REF'], os.environ['EVENT'])
    labels = {'frontend': 'Интерфейс', 'backend': 'Сервер',
              'integration': 'Compose и smoke', 'browser': 'Браузерные тесты'}
    with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as output:
        for key, value in selected.items():
            output.write(f'{key}={str(value).lower()}\n')
            print(f'{labels[key]}: {"запуск" if value else "пропуск"}')


if __name__ == '__main__':
    main()
