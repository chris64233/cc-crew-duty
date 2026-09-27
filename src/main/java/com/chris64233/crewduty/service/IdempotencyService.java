package com.chris64233.crewduty.service;

import com.chris64233.crewduty.api.BusinessException;
import com.chris64233.crewduty.domain.IdempotencyRecord;
import com.chris64233.crewduty.repo.IdempotencyRecordRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 幂等控制：以业务号为唯一键，记录请求指纹和原始响应。
 * 相同业务号 + 相同内容 → 重放原结果；相同业务号 + 不同内容 → 409 冲突。
 * 失败响应（如资格校验未通过）同样记录，重放时返回原失败。
 */
@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRecordRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * 在幂等保护下执行业务操作。
     *
     * @param idemKey     业务号
     * @param operation   操作类型（PUBLISH / SWAP_SUBMIT）
     * @param fingerprint 请求关键内容指纹
     * @param action      业务操作
     * @param resultType  结果类型（用于重放反序列化）
     */
    public <T> T execute(String idemKey, String operation, String fingerprint,
                         Supplier<T> action, Class<T> resultType) {
        var existing = repository.findByIdemKey(idemKey);
        if (existing.isPresent()) {
            return replay(existing.get(), operation, fingerprint, resultType);
        }

        T result;
        try {
            result = action.get();
        } catch (BusinessException e) {
            save(idemKey, operation, fingerprint, e.getStatus().value(), errorBody(e));
            throw e;
        }
        save(idemKey, operation, fingerprint, 200, toJson(result));
        return result;
    }

    private <T> T replay(IdempotencyRecord record, String operation, String fingerprint,
                         Class<T> resultType) {
        if (!record.getOperation().equals(operation)
                || !record.getRequestFingerprint().equals(fingerprint)) {
            throw BusinessException.conflict("IDEMPOTENCY_CONFLICT",
                    "业务号已被使用且请求内容不一致");
        }
        if (record.getHttpStatus() >= 400) {
            throw fromJson(record);
        }
        try {
            return objectMapper.readValue(record.getResponseBody(), resultType);
        } catch (Exception e) {
            throw new IllegalStateException("幂等记录反序列化失败", e);
        }
    }

    private void save(String idemKey, String operation, String fingerprint,
                      int status, String body) {
        repository.save(new IdempotencyRecord(idemKey, operation, fingerprint, status, body));
    }

    private String toJson(Object result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new IllegalStateException("响应序列化失败", e);
        }
    }

    private String errorBody(BusinessException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", e.getStatus().value());
        body.put("code", e.getCode());
        body.put("message", e.getMessage());
        body.put("details", e.getDetails());
        return toJson(body);
    }

    private BusinessException fromJson(IdempotencyRecord record) {
        try {
            JsonNode node = objectMapper.readTree(record.getResponseBody());
            String code = node.get("code").asText();
            String message = node.get("message").asText();
            List<String> details = new ArrayList<>();
            node.get("details").forEach(d -> details.add(d.asText()));
            return new BusinessException(HttpStatus.valueOf(record.getHttpStatus()), code, message, details);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("幂等记录解析失败", e);
        }
    }
}
