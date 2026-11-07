package com.smartmaterials.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PostgreSQL jsonb ↔ Map 映射。
 *
 * <p>必须用 {@code Types.OTHER} 写参数，否则驱动会按 varchar 发送，
 * PG 在 {@code column jsonb} 上不会做隐式转换，直接报类型错误。</p>
 */
@MappedTypes(Map.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonbTypeHandler extends BaseTypeHandler<Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Map<String, Object> parameter,
                                    JdbcType jdbcType) throws SQLException {
        try {
            ps.setObject(i, MAPPER.writeValueAsString(parameter), Types.OTHER);
        } catch (Exception e) {
            throw new SQLException("无法序列化 jsonb 字段: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private Map<String, Object> parse(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new SQLException("无法解析 jsonb 字段: " + e.getMessage(), e);
        }
    }
}
