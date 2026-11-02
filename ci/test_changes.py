import unittest
from changes import choose


class SelectionTest(unittest.TestCase):
    def test_frontend_only(self):
        self.assertEqual(choose(['frontend/src/app.ts'], 'feature', 'push'),
                         dict(frontend=True, backend=False, integration=False, browser=False))

    def test_backend_only(self):
        self.assertEqual(choose(['backend/src/App.java'], 'feature', 'push'),
                         dict(frontend=False, backend=True, integration=False, browser=False))

    def test_documentation(self):
        self.assertFalse(any(choose(['README.md', 'docs/ci.md'], 'feature', 'push').values()))

    def test_infrastructure_and_unknown_paths(self):
        for path in ['docker-compose.yml', '.github/workflows/ci.yml',
                     'backend/backend-api.yaml', 'frontend/Dockerfile', 'ci/changes.py', 'unknown']:
            with self.subTest(path=path):
                r = choose([path], 'feature', 'push')
                self.assertTrue(r['frontend'] and r['backend'] and r['integration'])

    def test_release_and_dev(self):
        self.assertTrue(choose(['README.md'], 'refs/heads/dev', 'push')['integration'])
        self.assertFalse(choose([], 'refs/heads/dev', 'push')['browser'])
        self.assertTrue(choose([], 'refs/heads/master', 'push')['browser'])

    def test_ui_tests(self):
        self.assertTrue(choose(['ui-tests/build.gradle.kts'], 'feature', 'push')['browser'])

    def test_manual(self):
        self.assertTrue(choose([], 'feature', 'workflow_dispatch')['integration'])


if __name__ == '__main__':
    unittest.main()
