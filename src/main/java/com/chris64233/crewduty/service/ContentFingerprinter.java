package com.chris64233.crewduty.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 幂等内容指纹：把请求内容规范化为属性名排序的 JSON 后取 SHA-256。
 *
 * <p>业务号是幂等键；相同业务号重放时比较内容指纹，指纹一致返回原结果，
 * 不一致判定为冲突。Map 键统一排序，使指纹不依赖字段构造顺序。
 *
 * <p>使用 Jackson 3（Spring Boot 4 内置，包名 {@code tools.jackson}），
 * 其 JSR-310（java.time）支持已内置于 databind。
 */
@Component
public class ContentFingerprinter {

    private final ObjectMapper canonicalMapper;

    public ContentFingerprinter() {
        this.canonicalMapper = JsonMapper.builder()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .build();
    }

    public String sha256(Object content) {
        try {
            byte[] json = canonicalMapper.writeValueAsBytes(content);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 摘要算法不可用", e);
        }
    }

    /** 把审计明细对象序列化为紧凑 JSON 字符串。 */
    public String toJson(Object content) {
        return canonicalMapper.writeValueAsString(content);
    }
}
