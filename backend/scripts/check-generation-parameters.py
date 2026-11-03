"""PostgreSQL + real HTTP smoke check in disposable Compose containers (Python 3.10+).

Run from any directory after `gradlew build`. Never uses the developer database.
"""
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request
import uuid

BACKEND = Path(__file__).resolve().parents[1]
PROJECT = "generation-check-" + uuid.uuid4().hex[:8]
COMPOSE = ["docker", "compose", "-p", PROJECT, "-f", str(BACKEND / "compose.generation-test.yml")]
BASE = "http://127.0.0.1:" + os.environ.get("GENERATION_TEST_PORT", "18080")


def compose(*args, stdin=None, check=True):
    return subprocess.run(COMPOSE + list(args), input=stdin, text=True, encoding="utf-8",
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=check)


def http(path, method="GET", payload=None, token=None):
    headers = {"Content-Type": "application/json", "Idempotency-Key": str(uuid.uuid4())}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(BASE + path, method=method, headers=headers,
                                     data=json.dumps(payload).encode() if payload is not None else None)
    try:
        response = urllib.request.urlopen(request, timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        raw = response.read()
        return response.status, json.loads(raw) if raw else None, response.headers


def sql(statement, check=True):
    return compose("exec", "-T", "db", "psql", "-U", "generation_test", "-d", "generation_test",
                   "-v", "ON_ERROR_STOP=1", "-At", stdin=statement, check=check)


def update(category, value, available, make_default, check=True):
    return compose("exec", "-T", "db", "psql", "-U", "generation_test", "-d", "generation_test",
                   "-v", "ON_ERROR_STOP=1", "-v", "category=" + category, "-v", "value=" + value,
                   "-v", "available=" + str(available).lower(), "-v", "make_default=" + str(make_default).lower(),
                   stdin=(BACKEND / "scripts/update-generation-option.sql").read_text(encoding="utf-8"), check=check)


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def run():
    require((BACKEND / "build/libs/app.jar").is_file(), "Run gradlew build first")
    try:
        print("Starting isolated PostgreSQL and the tested backend jar...", flush=True)
        result = compose("up", "--build", "-d")
        print(result.stdout, flush=True)
        deadline = time.monotonic() + 180
        while True:
            try:
                if http("/api/generation/parameters")[0] == 401:
                    break
            except (OSError, TimeoutError):
                pass
            require(time.monotonic() < deadline, "Backend did not start in 180 seconds")
            time.sleep(2)
        status, auth, _ = http("/api/auth/register", "POST", {
            "username": "generation_smoke", "email": "generation@example.test", "password": "test-password-123"})
        require(status == 200, "Registration failed")
        token = auth["token"]
        status, initial, headers = http("/api/generation/parameters", token=token)
        require(status == 200 and headers.get("Cache-Control") == "no-store", "Catalog response/cache policy failed")
        require(initial["defaults"] == {"questionCount": 10, "difficulty": "MEDIUM", "questionType": "SINGLE_CHOICE"},
                "Unexpected seed defaults")
        require([v["value"] for v in initial["questionCounts"]] == [5, 10], "Unexpected question counts")
        require(len(initial["difficulties"]) == 3 and len(initial["questionTypes"]) == 3, "Missing options")
        require(all(v["available"] for group in ("questionCounts", "difficulties", "questionTypes")
                    for v in initial[group]), "Seeds must be available")
        require(initial["questionTypeSelection"] == "SINGLE", "Wrong selection mode")
        request = {"materialId": str(uuid.uuid4()), **initial["defaults"]}
        require(http("/api/quizzes", "POST", request, token)[0] == 501, "Valid settings should reach the lifecycle stub")
        for patch in ({"questionCount": 20}, {"questionCount": 5.5}, {"difficulty": "UNKNOWN"}, {"questionType": "OPEN"},
                      {"questionType": ["SINGLE_CHOICE", "TRUE_FALSE"]}, {"topicIds": []},
                      {"prioritizeWeakTopics": True}, {"questionTypes": ["TRUE_FALSE"]}):
            require(http("/api/quizzes", "POST", {**request, **patch}, token)[0] == 400,
                    "Forbidden settings accepted: " + repr(patch))
        for field in ("materialId", "questionCount", "difficulty", "questionType"):
            require(http("/api/quizzes", "POST", {k: v for k, v in request.items() if k != field}, token)[0] == 400,
                    "Missing required field accepted: " + field)
        # Service-level snapshot insert/read is covered by GenerationParametersDatabaseTest.
        # PostgreSQL proves the independent storage is not changed by the maintenance script.
        job = str(uuid.uuid4())
        sql("INSERT INTO generation_settings_snapshots(job_id, question_count, difficulty, question_type) "
            f"VALUES ('{job}', 10, 'MEDIUM', 'SINGLE_CHOICE');")
        update("QUESTION_COUNT", "5", True, True)
        update("QUESTION_COUNT", "10", False, False)
        update("DIFFICULTY", "HARD", True, True)
        update("DIFFICULTY", "MEDIUM", False, False)
        update("QUESTION_TYPE", "TRUE_FALSE", True, True)
        update("QUESTION_TYPE", "SINGLE_CHOICE", False, False)
        status, changed, _ = http("/api/generation/parameters", token=token)
        require(status == 200 and changed["defaults"] ==
                {"questionCount": 5, "difficulty": "HARD", "questionType": "TRUE_FALSE"}, "Changes not visible after commit")
        require(not changed["questionCounts"][1]["available"], "Disabled value absent from response")
        for patch in ({"questionCount": 10}, {"difficulty": "MEDIUM"}, {"questionType": "SINGLE_CHOICE"}):
            status, error, _ = http("/api/quizzes", "POST", {**request, **changed["defaults"], **patch}, token)
            require(status == 400 and error["code"] == "GENERATION_OPTION_NOT_AVAILABLE", "Disabled option accepted")
        require(sql(f"SELECT question_count, difficulty, question_type FROM generation_settings_snapshots WHERE job_id = '{job}';")
                .stdout.strip() == "10|MEDIUM|SINGLE_CHOICE", "Historical snapshot changed")
        require(update("QUESTION_COUNT", "5", False, False, check=False).returncode != 0, "Disabled default accepted")
        require(update("QUESTION_COUNT", "20", True, True, check=False).returncode != 0, "Unsupported config accepted")
        require(http("/api/generation/parameters", token=token)[1] == changed, "Rejected change was not rolled back")
        # Broken catalog fails closed; no hidden fallback to source-code defaults.
        sql("DELETE FROM generation_options WHERE category = 'DIFFICULTY' AND option_value = 'EASY';")
        status, error, _ = http("/api/generation/parameters", token=token)
        require(status == 503 and error["code"] == "GENERATION_CONFIG_UNAVAILABLE", "Broken catalog not rejected")
        print("PASS: JWT/401, seed/defaults, all options, HTTP validation, committed changes, disabled values, "
              "snapshot stability, invalid update rollback, broken-catalog 503. PostgreSQL 16 + real HTTP.", flush=True)
    except BaseException:
        print(compose("logs", "--tail", "100", check=False).stdout, flush=True)
        raise
    finally:
        print(compose("down", "--volumes", "--remove-orphans", check=False).stdout, flush=True)


if __name__ == "__main__":
    run()
