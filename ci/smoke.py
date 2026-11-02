"""Exercise the real API through nginx. Run only against the disposable CI stack."""
import argparse
import json
import sys
import urllib.error
import urllib.request
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', required=True)
    args = parser.parse_args()
    base = args.base_url.rstrip('/')

    def request(method, path, expected, payload=None, token=None, content_type=None):
        # Exercise the browser's same-origin CORS path as well as nginx routing.
        headers = {'Origin': base}
        if isinstance(payload, dict):
            payload = json.dumps(payload).encode()
            content_type = 'application/json'
        if content_type:
            headers['Content-Type'] = content_type
        if token:
            headers['Authorization'] = 'Bearer ' + token
        req = urllib.request.Request(base + path, data=payload, headers=headers, method=method)
        try:
            response = urllib.request.urlopen(req, timeout=30)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            body = response.read()
            if response.status != expected:
                raise RuntimeError(f'{method} {path}: expected {expected}, got {response.status}')
            return json.loads(body) if 'application/json' in response.headers.get('Content-Type', '') else body

    page = request('GET', '/', 200)
    if b'<app-root' not in page:
        raise RuntimeError('nginx is not serving the Angular application')
    request('GET', '/api/materials', 401)
    suffix = uuid.uuid4().hex[:12]
    username = 'ci_' + suffix
    password = 'CiOnlyPassword123!'
    registered = request('POST', '/api/auth/register', 200, {
        'username': username, 'email': username + '@example.com', 'password': password,
    })
    if not registered.get('token'):
        raise RuntimeError('Registration returned no token')
    token = request('POST', '/api/auth/login', 200, {
        'username': username, 'password': password,
    })['token']
    request('POST', '/api/auth/login', 401, {'username': username, 'password': 'wrong-password'})
    request('GET', '/api/materials', 200, token=token)
    content = b'CI storage round-trip: PostgreSQL and SeaweedFS.\n'
    boundary = 'ci-' + uuid.uuid4().hex
    metadata = json.dumps({'name': 'CI material ' + suffix, 'subjectName': 'CI subject',
                           'description': '', 'tagIds': [], 'newTagNames': []}).encode()
    multipart = (f'--{boundary}\r\nContent-Disposition: form-data; name="metadata"\r\n'
                 'Content-Type: application/json\r\n\r\n').encode() + metadata
    multipart += (f'\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="ci.txt"\r\n'
                  'Content-Type: text/plain\r\n\r\n').encode() + content
    multipart += f'\r\n--{boundary}--\r\n'.encode()
    uploaded = request('POST', '/api/materials', 201, multipart, token,
                       'multipart/form-data; boundary=' + boundary)
    material_id = uploaded['material']['id']
    try:
        items = request('GET', '/api/materials', 200, token=token)['items']
        if material_id not in [item['id'] for item in items]:
            raise RuntimeError('Uploaded material is absent from database list')
        downloaded = request('GET', f'/api/materials/{material_id}/download', 200, token=token)
        if downloaded != content:
            raise RuntimeError('Downloaded bytes differ from uploaded bytes')
    finally:
        request('DELETE', f'/api/materials/{material_id}', 204, token=token)
    request('GET', f'/api/materials/{material_id}', 404, token=token)
    print('PASS: frontend, access control, registration/login, database, upload/download/delete')


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Do not print response bodies, headers or tokens into public CI logs.
        print(f'Smoke failed: {error}', file=sys.stderr)
        sys.exit(1)
