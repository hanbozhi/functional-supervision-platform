package com.zhineng.platform.evaluation.counterpart.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhineng.platform.common.audit.OperationLogRepository;
import com.zhineng.platform.common.user.dto.CurrentUserResponse;
import com.zhineng.platform.common.user.service.CurrentUserService;
import com.zhineng.platform.evaluation.counterpart.dto.CounterpartDtos;
import com.zhineng.platform.evaluation.counterpart.repository.CounterpartRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CounterpartService {
    private static final Set<String> STOP_WORDS = Set.of(
            "负责", "工作", "有关", "相关", "管理", "组织", "开展", "实施", "以及", "本市"
    );
    private static final Pattern WORD = Pattern.compile("[\\p{IsHan}]{2,6}");
    private final CounterpartRepository repository;
    private final CurrentUserService currentUser;
    private final OperationLogRepository operationLog;
    private final ObjectMapper objectMapper;

    public CounterpartService(
            CounterpartRepository repository,
            CurrentUserService currentUser,
            OperationLogRepository operationLog,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.operationLog = operationLog;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> organizations() {
        return repository.activeOrgs();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> relations(String status, Long subjectOrgId) {
        return repository.relations(upper(status), subjectOrgId);
    }

    public Map<String, Object> createRelation(CounterpartDtos.RelationRequest request) {
        RelationValues values = validateRelation(request);
        if (repository.relationExists(
                values.subjectId, values.targetId, values.item)) {
            conflict("DUPLICATE_RELATION", "相同机构和协作事项的关系已存在");
        }
        CurrentUserResponse user = currentUser.getCurrentUser();
        long id = repository.insertRelation(
                values.subjectId, values.targetId, values.item,
                "MANUAL", values.confidence, "CONFIRMED", user.id());
        repository.verifyRelation(id, "CONFIRMED", "人工新增并确认", 0, user.id());
        Map<String, Object> after = requireRelation(id);
        log("M2-1", "COUNTERPART_RELATION", id, "CREATE", null, after);
        return after;
    }

    public Map<String, Object> updateRelation(
            long id, CounterpartDtos.RelationRequest request
    ) {
        Map<String, Object> before = requireRelation(id);
        RelationValues values = validateRelation(request);
        if (repository.updateRelation(
                id, values.subjectId, values.targetId, values.item, values.confidence,
                rowVersion(request.rowVersion()), currentUser.getCurrentUser().id()) != 1) {
            conflict("STALE_RELATION", "协作关系已变化，请刷新后重试");
        }
        Map<String, Object> after = requireRelation(id);
        log("M2-1", "COUNTERPART_RELATION", id, "UPDATE", before, after);
        return after;
    }

    public Map<String, Object> verifyRelation(
            long id, CounterpartDtos.VerifyRequest request
    ) {
        Map<String, Object> before = requireRelation(id);
        String result = upper(request == null ? null : request.result());
        if (!Set.of("CONFIRMED", "REJECTED").contains(result)) {
            bad("INVALID_VERIFY_RESULT", "核验结果必须为CONFIRMED或REJECTED");
        }
        String opinion = required(request.opinion(), "核验意见");
        long userId = currentUser.getCurrentUser().id();
        if (repository.verifyRelation(
                id, result, opinion, rowVersion(request.rowVersion()), userId) != 1) {
            conflict("STALE_RELATION", "协作关系已变化，请刷新后重试");
        }
        Map<String, Object> after = requireRelation(id);
        log("M2-1", "COUNTERPART_RELATION", id, "VERIFY", before, after);
        return after;
    }

    public Map<String, Object> relationStatus(
            long id, CounterpartDtos.StatusRequest request
    ) {
        Map<String, Object> before = requireRelation(id);
        String status = upper(request == null ? null : request.status());
        if (!Set.of("CONFIRMED", "INACTIVE").contains(status)) {
            bad("INVALID_RELATION_STATUS", "关系状态只能启用或停用");
        }
        if (repository.relationStatus(
                id, status, rowVersion(request.rowVersion()),
                currentUser.getCurrentUser().id()) != 1) {
            conflict("STALE_RELATION", "协作关系已变化，请刷新后重试");
        }
        Map<String, Object> after = requireRelation(id);
        log("M2-1", "COUNTERPART_RELATION", id, "STATUS", before, after);
        return after;
    }

    public Map<String, Object> generateSuggestions() {
        List<Map<String, Object>> orgs = repository.activeOrgs();
        int created = 0;
        long userId = currentUser.getCurrentUser().id();
        for (int left = 0; left < orgs.size(); left++) {
            long leftId = number(orgs.get(left).get("id"));
            Set<String> leftWords = keywords(repository.confirmedDuty(leftId));
            if (leftWords.isEmpty()) continue;
            for (int right = left + 1; right < orgs.size(); right++) {
                long rightId = number(orgs.get(right).get("id"));
                Set<String> shared = new HashSet<>(leftWords);
                shared.retainAll(keywords(repository.confirmedDuty(rightId)));
                if (shared.isEmpty()) continue;
                String item = String.join("、", shared.stream().sorted().limit(3).toList());
                if (repository.relationExists(leftId, rightId, item)) continue;
                double confidence = Math.min(90, 55 + shared.size() * 10);
                repository.insertRelation(
                        leftId, rightId, item, "RULE_SUGGESTION",
                        confidence, "SUGGESTED", userId);
                created++;
            }
        }
        Map<String, Object> result = Map.of(
                "created", created,
                "rule", "已确认三定主要职责中共享的2～6字关键词"
        );
        log("M2-1", "RELATION_SUGGESTION", 0, "GENERATE", null, result);
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> questionnaires(String status) {
        return repository.questionnaires(upper(status));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> questionnaire(long id) {
        Map<String, Object> result = requireQuestionnaire(id);
        result.put("dimensions", repository.dimensions(id));
        result.put("questions", repository.questions(id));
        result.put("recipients", repository.recipients(id));
        return result;
    }

    public Map<String, Object> createQuestionnaire(
            CounterpartDtos.QuestionnaireRequest request
    ) {
        if (request == null) bad("QUESTIONNAIRE_REQUIRED", "问卷信息不能为空");
        String code = code(request.batchCode());
        String title = required(request.title(), "问卷标题");
        int year = request.evaluationYear() == null
                ? LocalDate.now().getYear() : request.evaluationYear();
        if (year < 1900 || year > 2999) bad("INVALID_YEAR", "评价年度无效");
        validateDeadline(request.deadlineAt());
        if (request.questions() == null || request.questions().isEmpty()) {
            bad("QUESTIONS_REQUIRED", "至少配置一道问卷题目");
        }
        CurrentUserResponse user = currentUser.getCurrentUser();
        long id = repository.insertQuestionnaire(
                code, title, year, trim(request.deadlineAt()),
                trim(request.description()), request.indicatorVersionId(), user.id());
        Map<String, Long> dimensionIds = new HashMap<>();
        if (request.dimensions() != null) {
            int index = 0;
            for (CounterpartDtos.DimensionInput dimension : request.dimensions()) {
                String dimensionCode = code(dimension.code());
                long dimensionId = repository.insertDimension(
                        id, dimensionCode, required(dimension.name(), "维度名称"),
                        dimension.sortOrder() == null ? index++ : dimension.sortOrder());
                dimensionIds.put(dimensionCode, dimensionId);
            }
        }
        int index = 0;
        for (CounterpartDtos.QuestionInput question : request.questions()) {
            String type = upper(question.type());
            if (!Set.of("SCORE", "TEXT").contains(type)) {
                bad("INVALID_QUESTION_TYPE", "题目类型只能为SCORE或TEXT");
            }
            Long dimensionId = question.dimensionId();
            if (dimensionId == null && question.dimensionCode() != null) {
                dimensionId = dimensionIds.get(code(question.dimensionCode()));
            }
            repository.insertQuestion(
                    id, dimensionId, code(question.code()),
                    required(question.text(), "题目内容"), type,
                    question.required() == null || question.required(),
                    question.indicatorItemId(),
                    question.sortOrder() == null ? index++ : question.sortOrder());
        }
        Map<String, Object> after = questionnaire(id);
        log("M2-2", "COUNTERPART_QUESTIONNAIRE", id, "CREATE", null, after);
        return after;
    }

    public Map<String, Object> publishQuestionnaire(long id) {
        Map<String, Object> before = requireQuestionnaire(id);
        if (!"DRAFT".equals(before.get("status"))) {
            conflict("QUESTIONNAIRE_READ_ONLY", "只有草稿问卷可以发布");
        }
        if (repository.questions(id).stream()
                .noneMatch(row -> "SCORE".equals(row.get("question_type")))) {
            bad("SCORE_QUESTION_REQUIRED", "发布前至少需要一道1～5分题");
        }
        if (repository.recipients(id).isEmpty()) {
            bad("RECIPIENTS_REQUIRED", "发布前至少选择一个已确认协作关系");
        }
        if (repository.publishQuestionnaire(id, currentUser.getCurrentUser().id()) != 1) {
            conflict("QUESTIONNAIRE_STATE_CHANGED", "问卷状态已变化");
        }
        Map<String, Object> after = questionnaire(id);
        log("M2-2", "COUNTERPART_QUESTIONNAIRE", id, "PUBLISH", before, after);
        return after;
    }

    public Map<String, Object> closeQuestionnaire(long id, String targetStatus) {
        Map<String, Object> before = requireQuestionnaire(id);
        String status = upper(targetStatus);
        if (!Set.of("DEADLINE", "CLOSED").contains(status)) {
            bad("INVALID_QUESTIONNAIRE_STATUS", "目标状态必须为DEADLINE或CLOSED");
        }
        if (repository.closeQuestionnaire(
                id, status, currentUser.getCurrentUser().id()) != 1) {
            conflict("QUESTIONNAIRE_STATE_CHANGED", "只有已发布问卷可以截止或关闭");
        }
        Map<String, Object> after = questionnaire(id);
        log("M2-2", "COUNTERPART_QUESTIONNAIRE", id, status, before, after);
        return after;
    }

    public Map<String, Object> copyQuestionnaire(
            long id, CounterpartDtos.CopyQuestionnaireRequest request
    ) {
        Map<String, Object> source = questionnaire(id);
        List<CounterpartDtos.DimensionInput> dimensions =
                repository.dimensions(id).stream().map(row -> new CounterpartDtos.DimensionInput(
                        row.get("dimension_code").toString(),
                        row.get("dimension_name").toString(),
                        ((Number) row.get("sort_order")).intValue())).toList();
        Map<Long, String> dimensionCodes = new HashMap<>();
        repository.dimensions(id).forEach(row -> dimensionCodes.put(
                number(row.get("id")), row.get("dimension_code").toString()));
        List<CounterpartDtos.QuestionInput> questions =
                repository.questions(id).stream().map(row -> new CounterpartDtos.QuestionInput(
                        null,
                        row.get("dimension_id") == null ? null
                                : dimensionCodes.get(number(row.get("dimension_id"))),
                        row.get("question_code").toString(),
                        row.get("question_text").toString(),
                        row.get("question_type").toString(),
                        number(row.get("required")) == 1,
                        row.get("indicator_item_id") == null ? null
                                : number(row.get("indicator_item_id")),
                        ((Number) row.get("sort_order")).intValue())).toList();
        return createQuestionnaire(new CounterpartDtos.QuestionnaireRequest(
                request.batchCode(), request.title(), request.evaluationYear(),
                source.get("deadline_at") == null ? null : source.get("deadline_at").toString(),
                trim(source.get("description")), source.get("indicator_version_id") == null
                ? null : number(source.get("indicator_version_id")), dimensions, questions));
    }

    public List<Map<String, Object>> addRecipients(
            long questionnaireId, CounterpartDtos.RecipientRequest request
    ) {
        Map<String, Object> questionnaire = requireQuestionnaire(questionnaireId);
        if (!"DRAFT".equals(questionnaire.get("status"))) {
            conflict("QUESTIONNAIRE_READ_ONLY", "发布后不能修改接收对象");
        }
        if (request == null || request.relationIds() == null
                || request.relationIds().isEmpty()) {
            bad("RELATIONS_REQUIRED", "请选择已确认协作关系");
        }
        long userId = currentUser.getCurrentUser().id();
        for (Long relationId : request.relationIds().stream().distinct().toList()) {
            Map<String, Object> relation = requireRelation(relationId);
            if (!"CONFIRMED".equals(relation.get("status"))) {
                bad("RELATION_NOT_CONFIRMED", "只能使用已确认协作关系");
            }
            if (repository.recipientExists(questionnaireId, relationId)) continue;
            long recipientId = repository.insertRecipient(
                    questionnaireId, relation, userId);
            repository.insertMapping(
                    recipientId, anonymousCode(recipientId), token());
        }
        List<Map<String, Object>> recipients = repository.recipients(questionnaireId);
        log("M2-2", "QUESTIONNAIRE_RECIPIENT", questionnaireId,
                "ADD_RECIPIENTS", null, Map.of("count", recipients.size()));
        return recipients;
    }

    public List<Map<String, Object>> simulatePush(long questionnaireId) {
        Map<String, Object> questionnaire = requireQuestionnaire(questionnaireId);
        if (!"PUBLISHED".equals(questionnaire.get("status"))) {
            conflict("QUESTIONNAIRE_NOT_PUBLISHED", "只有已发布问卷可以模拟推送");
        }
        long userId = currentUser.getCurrentUser().id();
        for (Map<String, Object> recipient : repository.recipients(questionnaireId)) {
            if ("SUBMITTED".equals(recipient.get("status"))) continue;
            repository.logPush(
                    questionnaireId, number(recipient.get("id")), "DELIVERED",
                    "本地模拟短信：请使用匿名Token填写“" + questionnaire.get("title") + "”",
                    null, userId);
        }
        List<Map<String, Object>> logs = repository.pushLogs(questionnaireId);
        log("M2-2", "SIMULATED_PUSH", questionnaireId,
                "PUSH", null, Map.of("logCount", logs.size()));
        return logs;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> recipients(long questionnaireId) {
        requireQuestionnaire(questionnaireId);
        return repository.recipients(questionnaireId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> pushLogs(long questionnaireId) {
        requireQuestionnaire(questionnaireId);
        return repository.pushLogs(questionnaireId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> tokenQuestionnaire(String token) {
        Map<String, Object> recipient = requireToken(token);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", recipient.get("title"));
        result.put("anonymousCode", recipient.get("anonymous_code"));
        result.put("status", recipient.get("status"));
        result.put("deadlineAt", recipient.get("deadline_at"));
        result.put("questions", repository.questions(number(recipient.get("questionnaire_id"))));
        return result;
    }

    public Map<String, Object> submit(
            String token, CounterpartDtos.SubmitRequest request
    ) {
        Map<String, Object> recipient = requireToken(token);
        if ("SUBMITTED".equals(recipient.get("status"))) {
            conflict("ALREADY_SUBMITTED", "该匿名问卷已经提交，不能重复提交");
        }
        if (!"PUBLISHED".equals(recipient.get("questionnaire_status"))) {
            conflict("QUESTIONNAIRE_NOT_OPEN", "问卷当前不在开放填写状态");
        }
        List<Map<String, Object>> questions =
                repository.questions(number(recipient.get("questionnaire_id")));
        Map<Long, CounterpartDtos.AnswerInput> answers = new HashMap<>();
        if (request != null && request.answers() != null) {
            request.answers().forEach(answer -> answers.put(answer.questionId(), answer));
        }
        for (Map<String, Object> question : questions) {
            long questionId = number(question.get("id"));
            CounterpartDtos.AnswerInput answer = answers.get(questionId);
            boolean required = number(question.get("required")) == 1;
            if (required && answer == null) bad("ANSWER_REQUIRED", "存在未填写的必答题");
            if (answer == null) continue;
            if ("SCORE".equals(question.get("question_type"))
                    && (answer.scoreValue() == null
                    || answer.scoreValue() < 1 || answer.scoreValue() > 5)) {
                bad("INVALID_SCORE", "评分题必须填写1～5分");
            }
            if ("TEXT".equals(question.get("question_type"))
                    && required && trim(answer.textValue()) == null) {
                bad("TEXT_ANSWER_REQUIRED", "文字必答题不能为空");
            }
        }
        String submittedAt = Instant.now().toString();
        int elapsed = request == null || request.elapsedSeconds() == null
                ? 0 : Math.max(0, request.elapsedSeconds());
        long responseId = repository.insertResponse(
                number(recipient.get("id")),
                recipient.get("anonymous_code").toString(), elapsed, submittedAt);
        for (CounterpartDtos.AnswerInput answer : answers.values()) {
            repository.insertAnswer(
                    responseId, answer.questionId(), answer.scoreValue(),
                    trim(answer.textValue()));
        }
        repository.markSubmitted(number(recipient.get("id")), submittedAt);
        Map<String, Object> result = Map.of(
                "responseId", responseId,
                "anonymousCode", recipient.get("anonymous_code"),
                "submittedAt", submittedAt
        );
        log("M2-3", "ANONYMOUS_RESPONSE", responseId, "SUBMIT", null, result);
        return result;
    }

    public Map<String, Object> restore(long recipientId) {
        Map<String, Object> recipient = repository.recipient(recipientId);
        if (recipient == null) notFound("RECIPIENT_NOT_FOUND", "接收对象不存在");
        repository.restore(recipientId, currentUser.getCurrentUser().id());
        log("M2-3", "ANONYMOUS_MAPPING", recipientId, "SIMULATED_RESTORE",
                null, Map.of("recipientId", recipientId));
        recipient.put("warning", "模拟管理还原，不代表真实安全匿名");
        return recipient;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> statistics(long questionnaireId) {
        requireQuestionnaire(questionnaireId);
        return Map.of(
                "organizations", repository.organizationStats(questionnaireId),
                "questions", repository.resultStats(questionnaireId)
        );
    }

    public Map<String, Object> detectAnomalies(long questionnaireId) {
        requireQuestionnaire(questionnaireId);
        List<Map<String, Object>> answers = repository.scoreAnswers(questionnaireId);
        CurrentUserResponse user = currentUser.getCurrentUser();
        String runCode = "RULE-" + questionnaireId + "-"
                + Instant.now().toEpochMilli();
        long runId = repository.insertAnomalyRun(
                questionnaireId, runCode,
                "{\"extreme\":[1,5],\"meanDeviation\":1.5,\"rapidSeconds\":20,\"minSamples\":5}",
                answers.size(), user.id());
        Map<Long, List<Map<String, Object>>> byQuestion = new HashMap<>();
        answers.forEach(answer -> byQuestion.computeIfAbsent(
                number(answer.get("question_id")), ignored -> new ArrayList<>()).add(answer));
        for (Map<String, Object> answer : answers) {
            long responseId = number(answer.get("response_id"));
            long questionId = number(answer.get("question_id"));
            double score = decimal(answer.get("score_value"));
            if (score == 1 || score == 5) {
                repository.insertAnomaly(
                        runId, responseId, questionId, "EXTREME_SCORE",
                        score, null, "评分为量表端点" + (int) score + "分");
            }
            List<Map<String, Object>> samples = byQuestion.get(questionId);
            if (samples.size() >= 5) {
                double mean = samples.stream()
                        .mapToDouble(row -> decimal(row.get("score_value"))).average().orElse(0);
                if (Math.abs(score - mean) >= 1.5) {
                    repository.insertAnomaly(
                            runId, responseId, questionId, "MEAN_DEVIATION",
                            score, rounded(mean), "与同题均值偏差达到1.5分");
                }
            }
            if (number(answer.get("client_elapsed_seconds")) > 0
                    && number(answer.get("client_elapsed_seconds")) < 20) {
                repository.insertAnomaly(
                        runId, responseId, null, "RAPID_SUBMISSION",
                        decimal(answer.get("client_elapsed_seconds")), 20.0,
                        "整份问卷填写时间少于20秒");
            }
        }
        repository.finishAnomalyRun(runId);
        Map<String, Object> result = Map.of(
                "runId", runId,
                "cases", repository.anomalies(runId, null),
                "ruleLabel", "规则识别"
        );
        log("M2-4", "ANOMALY_RUN", runId, "DETECT", null, result);
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> anomalyRuns(Long questionnaireId) {
        return repository.anomalyRuns(questionnaireId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> anomalies(long runId, String status) {
        return repository.anomalies(runId, upper(status));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> anomaly(long id) {
        Map<String, Object> result = requireAnomaly(id);
        result.put("reviews", repository.reviews(id));
        return result;
    }

    public Map<String, Object> assign(
            long id, CounterpartDtos.AssignRequest request
    ) {
        Map<String, Object> before = requireAnomaly(id);
        Long assignee = request == null || request.userId() == null
                ? currentUser.getCurrentUser().id() : request.userId();
        if (repository.updateAnomaly(
                id, "ASSIGNED", assignee, rowVersion(request.rowVersion()),
                currentUser.getCurrentUser().id(), "ASSIGN", trim(request.opinion())) != 1) {
            conflict("STALE_ANOMALY", "异常记录已变化，请刷新后重试");
        }
        Map<String, Object> after = anomaly(id);
        log("M2-4", "ANOMALY_CASE", id, "ASSIGN", before, after);
        return after;
    }

    public Map<String, Object> review(
            long id, CounterpartDtos.ReviewRequest request
    ) {
        Map<String, Object> before = requireAnomaly(id);
        String action = upper(request == null ? null : request.action());
        if (!Set.of("ACCEPT", "REJECT").contains(action)) {
            bad("INVALID_REVIEW_ACTION", "复核结果必须为ACCEPT或REJECT");
        }
        String opinion = required(request.opinion(), "复核意见");
        String status = "ACCEPT".equals(action) ? "ACCEPTED" : "REJECTED";
        Long assignee = before.get("assigned_to") == null
                ? currentUser.getCurrentUser().id() : number(before.get("assigned_to"));
        if (repository.updateAnomaly(
                id, status, assignee, rowVersion(request.rowVersion()),
                currentUser.getCurrentUser().id(), action, opinion) != 1) {
            conflict("STALE_ANOMALY", "异常记录已变化，请刷新后重试");
        }
        Map<String, Object> after = anomaly(id);
        log("M2-4", "ANOMALY_CASE", id, action, before, after);
        return after;
    }

    private RelationValues validateRelation(CounterpartDtos.RelationRequest request) {
        if (request == null || request.subjectOrgId() == null
                || request.counterpartOrgId() == null) {
            bad("ORGANIZATIONS_REQUIRED", "评价主体和对口机构不能为空");
        }
        if (request.subjectOrgId().equals(request.counterpartOrgId())) {
            bad("SAME_ORGANIZATION", "评价主体不能与对口机构相同");
        }
        requireActiveOrg(request.subjectOrgId());
        requireActiveOrg(request.counterpartOrgId());
        double confidence = request.confidence() == null ? 100 : request.confidence();
        if (confidence < 0 || confidence > 100) {
            bad("INVALID_CONFIDENCE", "置信度必须在0～100之间");
        }
        return new RelationValues(
                request.subjectOrgId(), request.counterpartOrgId(),
                required(request.collaborationItem(), "协作事项"), confidence);
    }

    private void requireActiveOrg(long id) {
        Map<String, Object> org = repository.org(id);
        if (org == null || !"ACTIVE".equals(org.get("status"))
                || Set.of("ROOT", "GROUP").contains(org.get("unit_type"))) {
            bad("INVALID_ORGANIZATION", "只能选择启用的业务机构");
        }
    }

    private Map<String, Object> requireRelation(long id) {
        Map<String, Object> row = repository.relation(id);
        if (row == null) notFound("RELATION_NOT_FOUND", "协作关系不存在");
        return row;
    }

    private Map<String, Object> requireQuestionnaire(long id) {
        Map<String, Object> row = repository.questionnaire(id);
        if (row == null) notFound("QUESTIONNAIRE_NOT_FOUND", "问卷批次不存在");
        return row;
    }

    private Map<String, Object> requireToken(String token) {
        Map<String, Object> row = repository.recipientByToken(required(token, "填写Token"));
        if (row == null) notFound("TOKEN_NOT_FOUND", "填写Token无效");
        return row;
    }

    private Map<String, Object> requireAnomaly(long id) {
        Map<String, Object> row = repository.anomaly(id);
        if (row == null) notFound("ANOMALY_NOT_FOUND", "异常记录不存在");
        return row;
    }

    private Set<String> keywords(String text) {
        if (text == null || text.isBlank()) return Set.of();
        Set<String> result = new HashSet<>();
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            String value = matcher.group();
            if (!STOP_WORDS.contains(value)) result.add(value);
        }
        return result;
    }

    private void validateDeadline(String value) {
        if (value == null || value.isBlank()) return;
        try {
            OffsetDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            bad("INVALID_DEADLINE", "截止时间必须为ISO-8601时间");
        }
    }

    private String anonymousCode(long recipientId) {
        return "BM-" + Long.toString(recipientId, 36).toUpperCase(Locale.ROOT)
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
    }

    private String token() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String required(Object value, String label) {
        String text = trim(value);
        if (text == null) bad("REQUIRED_FIELD", label + "不能为空");
        return text;
    }

    private String code(String value) {
        String code = required(value, "编码").toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9][A-Z0-9_-]{0,63}")) {
            bad("INVALID_CODE", "编码只能使用字母、数字、下划线和连字符");
        }
        return code;
    }

    private String upper(String value) {
        return value == null || value.isBlank()
                ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String trim(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private int rowVersion(Integer value) {
        if (value == null || value < 0) bad("ROW_VERSION_REQUIRED", "缺少有效版本号");
        return value;
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private double decimal(Object value) {
        return ((Number) value).doubleValue();
    }

    private double rounded(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String json(Object value) {
        try {
            return value == null ? null : objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法记录操作日志", exception);
        }
    }

    private void log(String module, String businessType, long id, String action,
                     Object before, Object after) {
        operationLog.success(
                module, businessType, id, action, currentUser.getCurrentUser().id(),
                "LOCAL", "/api/counterpart-evaluation", json(before), json(after));
    }

    private void bad(String code, String message) {
        throw new CounterpartException(HttpStatus.BAD_REQUEST, code, message);
    }

    private void conflict(String code, String message) {
        throw new CounterpartException(HttpStatus.CONFLICT, code, message);
    }

    private void notFound(String code, String message) {
        throw new CounterpartException(HttpStatus.NOT_FOUND, code, message);
    }

    private record RelationValues(
            long subjectId, long targetId, String item, double confidence
    ) {
    }
}
