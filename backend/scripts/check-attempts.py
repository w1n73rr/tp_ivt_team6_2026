"""Disposable Compose HTTP smoke, no external model or developer database.
Run after backend build: python backend/scripts/check-attempts.py
"""
import importlib.util
import json
from pathlib import Path
import time
import sys
import uuid

sys.dont_write_bytecode = True

spec = importlib.util.spec_from_file_location("smoke", Path(__file__).with_name("check-generation-parameters.py"))
smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)
http, sql, require, compose = smoke.http, smoke.sql, smoke.require, smoke.compose


def literal(value):
    return "'" + str(value).replace("'", "''") + "'"


def run():
    try:
        compose("up", "--build", "-d")
        deadline = time.monotonic() + 180
        while True:
            try:
                if http("/api/attempts")[0] == 401:
                    break
            except OSError:
                pass
            require(time.monotonic() < deadline, "Backend startup timeout")
            time.sleep(2)
        tokens = []
        for name in ("attempt_owner", "attempt_other"):
            status, auth, _ = http("/api/auth/register", "POST", {
                "username": name, "email": name + "@example.test", "password": "integration-only-password"})
            require(status == 200, "Registration failed")
            tokens.append(auth["token"])
        token, other = tokens
        owner = sql("SELECT id FROM users WHERE username='attempt_owner';").stdout.strip()
        quiz, job, material, question, a, b = [str(uuid.uuid4()) for _ in range(6)]
        summary = {"id": quiz, "name": "Fixture", "materialId": material, "questionCount": 1,
                   "difficulty": "MEDIUM", "questionType": "SINGLE_CHOICE"}
        questions = [{"id": question, "type": "single_choice", "text": "Fixture question",
                      "options": [{"id": a, "text": "A"}, {"id": b, "text": "B"}], "correctOptionIds": [a]}]
        # Controlled source fixture only: all attempt operations below use the real application API.
        request = {"materialId": material, "questionCount": 5, "difficulty": "MEDIUM", "questionType": "SINGLE_CHOICE"}
        sql("INSERT INTO generation_jobs(id,user_id,request_json,material_id,file_id,format,material_name,subject_id,subject_name,status) VALUES ("
            + ",".join(map(literal, [job, owner, json.dumps(request), material, str(uuid.uuid4()), "TXT", "M", str(uuid.uuid4()), "S", "COMPLETED"])) + ");"
            + "INSERT INTO quizzes(id,job_id,user_id,summary_json,result_json) VALUES ("
            + ",".join(map(literal, [quiz, job, owner, json.dumps(summary), json.dumps({"questions": questions})])) + ");")
        status, attempt, headers = http(f"/api/quizzes/{quiz}/attempts", "POST", token=token)
        require(status == 201 and "no-store" in headers.get("Cache-Control", ""), "Start failed")
        require("correctOptionIds" not in json.dumps(attempt), "Answer key leaked")
        attempt_id = attempt["attempt"]["id"]
        path = f"/api/attempts/{attempt_id}/answers/{question}"
        payload = {"selectedOptionIds": [a]}
        status, saved, _ = http(path, "PUT", payload, token)
        require(status == 200, "Save failed")
        require(http(path, "PUT", payload, token)[1] == saved, "Repeated PUT changed answer")
        require(http(path, "PUT", {"selectedOptionIds": [b]}, token)[0] == 200, "Replace failed")
        require(http(path, "PUT", {"selectedOptionIds": [str(uuid.uuid4())]}, token)[0] == 400, "Foreign option accepted")
        require(http(f"/api/attempts/{attempt_id}", token=token)[1]["answers"][0]["selectedOptionIds"] == [b], "Invalid PUT changed state")
        require(http(path, "PUT", {"selectedOptionIds": []}, token)[0] == 200, "Clear failed")
        require(http(path, "PUT", payload)[0] == 401, "Anonymous write accepted")
        foreign = http(path, "PUT", payload, other)
        missing = http(f"/api/attempts/{uuid.uuid4()}/answers/{question}", "PUT", payload, other)
        require(foreign[:2] == missing[:2] and foreign[0] == 404, "Owner isolation failed")
        print("PASS: Compose + PostgreSQL + real HTTP/JWT; start, save, replay, replace, clear, validation and ownership.", flush=True)
    finally:
        result = compose("down", "--volumes", "--remove-orphans", check=False)
        require(result.returncode == 0, "Could not remove disposable Compose project")


if __name__ == "__main__":
    run()
