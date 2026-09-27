package com.chris64233.crewduty.api;

import com.chris64233.crewduty.api.Dtos.AuditResponse;
import com.chris64233.crewduty.api.Dtos.CreatePairingRequest;
import com.chris64233.crewduty.api.Dtos.PairingResponse;
import com.chris64233.crewduty.api.Dtos.PublishRequest;
import com.chris64233.crewduty.api.Dtos.QualificationFailure;
import com.chris64233.crewduty.domain.AuditRecord;
import com.chris64233.crewduty.repo.AuditRecordRepository;
import com.chris64233.crewduty.service.PairingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pairings")
public class PairingController {

    private final PairingService pairingService;
    private final AuditRecordRepository auditRepository;

    public PairingController(PairingService pairingService, AuditRecordRepository auditRepository) {
        this.pairingService = pairingService;
        this.auditRepository = auditRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PairingResponse create(@Valid @RequestBody CreatePairingRequest request) {
        return pairingService.createPairing(request);
    }

    @GetMapping("/{id}")
    public PairingResponse get(@PathVariable Long id) {
        return pairingService.getPairing(id);
    }

    /** 发布组合：幂等 + 全量资格校验，任何成员不合格整组拒绝。 */
    @PostMapping("/{id}/publish")
    public PairingResponse publish(@PathVariable Long id,
                                   @Valid @RequestBody PublishRequest request) {
        return pairingService.publish(id, request.idemKey());
    }

    /** 组合当前资格失败原因（预检，不改变状态）。 */
    @GetMapping("/{id}/qualification-failures")
    public List<QualificationFailure> failures(@PathVariable Long id) {
        return pairingService.getFailures(id);
    }

    /** 组合的审计记录。 */
    @GetMapping("/{id}/audits")
    public List<AuditResponse> audits(@PathVariable Long id) {
        pairingService.getPairing(id); // 不存在时抛 404
        return auditRepository.findByPairingIdOrderByCreatedAtAsc(id).stream()
                .map(this::toResponse)
                .toList();
    }

    private AuditResponse toResponse(AuditRecord record) {
        return new AuditResponse(record.getId(), record.getAction(), record.getPairingId(),
                record.getDetail(), record.getCreatedAt().toString());
    }
}
