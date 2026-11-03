import unittest
from generation_check import verify_unconfigured


class UnconfiguredGenerationTest(unittest.TestCase):
    def test_accepts_real_ml_adapter_error(self):
        verify_unconfigured({'status': 'FAILED', 'error': {'code': 'NOT_CONFIGURED'}}, {'items': []})

    def test_does_not_misclassify_other_failures_as_unconfigured(self):
        for code in ('ML_NOT_CONFIGURED', 'DOCUMENT_NO_TEXT', 'MODEL_TIMEOUT'):
            with self.subTest(code=code), self.assertRaises(RuntimeError):
                verify_unconfigured({'status': 'FAILED', 'error': {'code': code}}, {'items': []})

    def test_failed_job_must_not_have_saved_quiz(self):
        with self.assertRaises(RuntimeError):
            verify_unconfigured({'status': 'FAILED', 'error': {'code': 'NOT_CONFIGURED'}}, {'items': [{'id': 'quiz'}]})


if __name__ == '__main__':
    unittest.main()
