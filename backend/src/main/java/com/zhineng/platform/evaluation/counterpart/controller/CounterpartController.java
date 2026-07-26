package com.zhineng.platform.evaluation.counterpart.controller;

import com.zhineng.platform.evaluation.counterpart.dto.CounterpartDtos;
import com.zhineng.platform.evaluation.counterpart.service.CounterpartService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/counterpart-evaluation")
public class CounterpartController {
    private final CounterpartService service;

    public CounterpartController(CounterpartService service) {
        this.service = service;
    }

    @GetMapping("/organizations")
    public List<Map<String, Object>> organizations() {
        return service.organizations();
    }

    @GetMapping("/relations")
    public List<Map<String, Object>> relations(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long subjectOrgId
    ) {
        return service.relations(status, subjectOrgId);
    }

    @PostMapping("/relations")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createRelation(
            @RequestBody CounterpartDtos.RelationRequest request
    ) {
        return service.createRelation(request);
    }

    @PutMapping("/relations/{id}")
    public Map<String, Object> updateRelation(
            @PathVariable long id,
            @RequestBody CounterpartDtos.RelationRequest request
    ) {
        return service.updateRelation(id, request);
    }

    @PutMapping("/relations/{id}/verify")
    public Map<String, Object> verifyRelation(
            @PathVariable long id,
            @RequestBody CounterpartDtos.VerifyRequest request
    ) {
        return service.verifyRelation(id, request);
    }

    @PutMapping("/relations/{id}/status")
    public Map<String, Object> relationStatus(
            @PathVariable long id,
            @RequestBody CounterpartDtos.StatusRequest request
    ) {
        return service.relationStatus(id, request);
    }

    @PostMapping("/relation-suggestions/generate")
    public Map<String, Object> suggestions() {
        return service.generateSuggestions();
    }

    @GetMapping("/questionnaires")
    public List<Map<String, Object>> questionnaires(
            @RequestParam(required = false) String status
    ) {
        return service.questionnaires(status);
    }

    @GetMapping("/questionnaires/{id}")
    public Map<String, Object> questionnaire(@PathVariable long id) {
        return service.questionnaire(id);
    }

    @PostMapping("/questionnaires")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createQuestionnaire(
            @RequestBody CounterpartDtos.QuestionnaireRequest request
    ) {
        return service.createQuestionnaire(request);
    }

    @PostMapping("/questionnaires/{id}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> copyQuestionnaire(
            @PathVariable long id,
            @RequestBody CounterpartDtos.CopyQuestionnaireRequest request
    ) {
        return service.copyQuestionnaire(id, request);
    }

    @PostMapping("/questionnaires/{id}/recipients")
    public List<Map<String, Object>> addRecipients(
            @PathVariable long id,
            @RequestBody CounterpartDtos.RecipientRequest request
    ) {
        return service.addRecipients(id, request);
    }

    @GetMapping("/questionnaires/{id}/recipients")
    public List<Map<String, Object>> recipients(@PathVariable long id) {
        return service.recipients(id);
    }

    @PostMapping("/questionnaires/{id}/publish")
    public Map<String, Object> publish(@PathVariable long id) {
        return service.publishQuestionnaire(id);
    }

    @PostMapping("/questionnaires/{id}/deadline")
    public Map<String, Object> deadline(@PathVariable long id) {
        return service.closeQuestionnaire(id, "DEADLINE");
    }

    @PostMapping("/questionnaires/{id}/close")
    public Map<String, Object> close(@PathVariable long id) {
        return service.closeQuestionnaire(id, "CLOSED");
    }

    @PostMapping("/questionnaires/{id}/simulate-push")
    public List<Map<String, Object>> push(@PathVariable long id) {
        return service.simulatePush(id);
    }

    @GetMapping("/questionnaires/{id}/push-logs")
    public List<Map<String, Object>> pushLogs(@PathVariable long id) {
        return service.pushLogs(id);
    }

    @GetMapping("/questionnaires/{id}/statistics")
    public Map<String, Object> statistics(@PathVariable long id) {
        return service.statistics(id);
    }

    @GetMapping("/fill/{token}")
    public Map<String, Object> fill(@PathVariable String token) {
        return service.tokenQuestionnaire(token);
    }

    @PostMapping("/fill/{token}/submit")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> submit(
            @PathVariable String token,
            @RequestBody CounterpartDtos.SubmitRequest request
    ) {
        return service.submit(token, request);
    }

    @PostMapping("/anonymous-mappings/{recipientId}/restore")
    public Map<String, Object> restore(@PathVariable long recipientId) {
        return service.restore(recipientId);
    }

    @PostMapping("/questionnaires/{id}/anomaly-runs")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> detect(@PathVariable long id) {
        return service.detectAnomalies(id);
    }

    @GetMapping("/anomaly-runs")
    public List<Map<String, Object>> runs(
            @RequestParam(required = false) Long questionnaireId
    ) {
        return service.anomalyRuns(questionnaireId);
    }

    @GetMapping("/anomaly-runs/{id}/cases")
    public List<Map<String, Object>> anomalies(
            @PathVariable long id,
            @RequestParam(required = false) String status
    ) {
        return service.anomalies(id, status);
    }

    @GetMapping("/anomaly-cases/{id}")
    public Map<String, Object> anomaly(@PathVariable long id) {
        return service.anomaly(id);
    }

    @PutMapping("/anomaly-cases/{id}/assign")
    public Map<String, Object> assign(
            @PathVariable long id,
            @RequestBody CounterpartDtos.AssignRequest request
    ) {
        return service.assign(id, request);
    }

    @PutMapping("/anomaly-cases/{id}/review")
    public Map<String, Object> review(
            @PathVariable long id,
            @RequestBody CounterpartDtos.ReviewRequest request
    ) {
        return service.review(id, request);
    }
}
