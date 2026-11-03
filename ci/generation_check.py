"""Exercise TY5 on a disposable Compose stack. Leaves its own test records for inspection."""
import argparse
import json
from pathlib import Path
import secrets
import sys
import time
import urllib.error
import urllib.request
import uuid


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def verify_unconfigured(status, quizzes):
    require(status['status'] == 'FAILED', 'Expected FAILED without a configured ML provider')
    require(status.get('error', {}).get('code') == 'NOT_CONFIGURED',
            'Expected NOT_CONFIGURED; inspect the document or dependency state')
    require(not quizzes['items'], 'Failed generation must not save a quiz')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', required=True)
    parser.add_argument('--file', type=Path, help='PDF/DOCX/UTF-8 TXT; defaults to a small TXT fixture')
    parser.add_argument('--expect-unconfigured', action='store_true', help='Expect FAILED/NOT_CONFIGURED, not real ML success')
    parser.add_argument('--timeout', type=float, default=450,
                        help='Client wait limit; allow for the configured whole-job deadline and queue time')
    args = parser.parse_args()
    base = args.base_url.rstrip('/')

    def request(method, path, expected, payload=None, token=None, extra=None, content_type='application/json'):
        headers = dict(extra or {})
        if token:
            headers['Authorization'] = 'Bearer ' + token
        if isinstance(payload, dict):
            payload = json.dumps(payload, ensure_ascii=False).encode('utf-8')
        if payload is not None:
            headers['Content-Type'] = content_type
        req = urllib.request.Request(base + path, data=payload, headers=headers, method=method)
        try:
            response = urllib.request.urlopen(req, timeout=30)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            require(response.status == expected, f'{method} {path}: expected HTTP {expected}, got {response.status}')
            body = response.read()
            return (json.loads(body) if body else None), response.headers

    def register():
        username = 'ty5_' + uuid.uuid4().hex[:16]
        body, _ = request('POST', '/api/auth/register', 200, {
            'username': username, 'email': username + '@example.org',
            'password': 'T5!' + secrets.token_urlsafe(20),
        })
        require(bool(body.get('token')), 'Registration returned no token')
        return body['token']

    token = register()
    filename = args.file.name if args.file else 'lesson.txt'
    require(filename.lower().endswith(('.pdf', '.docx', '.txt')), 'Use PDF, DOCX or TXT')
    require(not any(c in filename for c in '\r\n"'), 'Unsafe filename')
    content = args.file.read_bytes() if args.file else (
        'Database transactions provide atomicity, consistency, isolation and durability. '
        'A primary key identifies a row. Foreign keys preserve references between tables. '
        'Indexes accelerate lookups. Normalization reduces redundancy. '
        'SQL SELECT reads rows, INSERT adds rows, UPDATE changes rows and DELETE removes rows.\n'
    ).encode('utf-8')
    boundary = 'ty5-' + uuid.uuid4().hex
    metadata = json.dumps({'name': 'TY5 ' + uuid.uuid4().hex[:12], 'subjectName': 'TY5 check'}).encode()
    multipart = (f'--{boundary}\r\nContent-Disposition: form-data; name="metadata"\r\n'
                 'Content-Type: application/json\r\n\r\n').encode() + metadata
    multipart += (f'\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
                  'Content-Type: application/octet-stream\r\n\r\n').encode() + content
    multipart += f'\r\n--{boundary}--\r\n'.encode()
    uploaded, _ = request('POST', '/api/materials', 201, multipart, token,
                          content_type='multipart/form-data; boundary=' + boundary)
    material_id = uploaded['material']['id']
    parameters = {'materialId': material_id, 'name': 'TY5 check', 'questionCount': 5,
                  'difficulty': 'MEDIUM', 'questionType': 'SINGLE_CHOICE'}
    key = {'Idempotency-Key': str(uuid.uuid4())}
    request('POST', '/api/quizzes', 400, {**parameters, 'questionCount': 20}, token, key)
    job, headers = request('POST', '/api/quizzes', 202, parameters, token, key)
    job_path = '/api/generation-jobs/' + job['id']
    require(headers.get('Location') == job_path, 'Missing or incorrect polling Location')
    replay, _ = request('POST', '/api/quizzes', 202, parameters, token, key)
    require(replay['id'] == job['id'], 'Repeated request created a duplicate job')
    request('POST', '/api/quizzes', 409, {**parameters, 'questionCount': 10}, token, key)
    request('GET', job_path, 401)
    request('GET', job_path, 404, token=register())
    deadline = time.monotonic() + args.timeout
    while True:
        status, _ = request('GET', job_path, 200, token=token)
        if status['status'] in ('COMPLETED', 'FAILED'):
            break
        require(time.monotonic() < deadline, 'Timed out before terminal status')
        time.sleep(2)
    quizzes, _ = request('GET', '/api/quizzes', 200, token=token)
    if args.expect_unconfigured:
        verify_unconfigured(status, quizzes)
        print('PASS: acceptance, retry, conflict, owner-only status, FAILED/NOT_CONFIGURED (not real ML integration)')
    else:
        require(status['status'] == 'COMPLETED', 'Expected COMPLETED; inspect the safe job error as its owner')
        matching = [item for item in quizzes['items'] if item['id'] == status.get('quizId')]
        require(len(matching) == 1 and len(quizzes['items']) == 1, 'Expected exactly one saved quiz')
        require(matching[0]['questionCount'] == 5, 'Unexpected saved question count')
        print('PASS: generation, retry, persistence, owner-only terminal status and quiz list')
    print('Job ID:', job['id'])


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Never include response bodies, headers, tokens or credentials in shared logs.
        print('Generation check failed:', type(error).__name__, file=sys.stderr)
        if isinstance(error, RuntimeError):
            print(str(error), file=sys.stderr)
        sys.exit(1)
