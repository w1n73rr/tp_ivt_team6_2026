# Контракт прохождения для клиента

Источник схем: [backend-api.yaml](../backend/backend-api.yaml), модели
AttemptDetail, SavedAnswer, AttemptCompletion, AttemptResult, QuestionResult.
Backend-инструкция и воспроизводимые проверки: [ATTEMPTS.md](../backend/ATTEMPTS.md).
Этот документ фиксирует контракт для будущего экрана; подключение экрана выполняется
отдельной FE-задачей. Подтверждение команды FE необходимо приложить к PR.

| Запрос | Успешный ответ |
| --- | --- |
| POST /api/quizzes/{quizId}/attempts + Idempotency-Key UUID | 201 AttemptDetail; новый ключ для повторного прохождения |
| GET /api/attempts/{attemptId} | 200 AttemptDetail, публичные questions и собственные answers |
| PUT /api/attempts/{attemptId}/answers/{questionId} | 200 SavedAnswer; body selectedOptionIds обязателен, [] очищает |
| POST /api/attempts/{attemptId}/complete | 200 AttemptCompletion, сохранённая сводка без разбора |
| GET /api/attempts/{attemptId}/result | 200 AttemptResult, сводка и items в порядке прохождения |

Все запросы с `Authorization: Bearer <JWT>`. Ответы не кешируются. Нет входа —
401, чужой/отсутствующий ID — одинаковый 404, запись в закрытую попытку — 409,
result незавершённой попытки — 409 RESULT_NOT_READY без деталей.

AttemptCompletion: attemptId, quiz (полный QuizSummary-снимок), score (=correctCount),
scorePercent, totalQuestions, correctCount, incorrectCount, skippedCount,
durationSeconds, completedAt. Три счётчика не пересекаются и суммируются в totalQuestions.
Процент уже рассчитан/округлён сервером. Повтор complete возвращает тот же итог.

AttemptResult дополнительно содержит `items`:

```typescript
interface QuestionResult {
  question: { id: string; type: 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE';
    text: string; position: number; options: { id: string; text: string }[] };
  selectedOptionIds: string[];
  correctOptionIds: string[];
  outcome: 'CORRECT' | 'INCORRECT' | 'SKIPPED';
  score: 0 | 1;
}
```

Сохраняйте attemptId в маршруте результата. При повторном прохождении результат
старой попытки остаётся по старому ID; нельзя заменять его результатом последней
попытки теста. Вопросы, название и выбор берутся из снимка попытки.

```typescript
const response = await fetch(`/api/attempts/${encodeURIComponent(attemptId)}/result`, {
  headers: { Authorization: `Bearer ${token}` }, cache: 'no-store'
});
// Обработайте 401/404/409, затем используйте result.items в полученном порядке.
```

Отдельного `/review` в контракте и обработчиках нет. До завершения правильные
варианты и оценки не выдаются; complete содержит сводку, единственная выдача
сравнения — GET /result. Подсказки/объяснения/история в объём этих задач не входят.
