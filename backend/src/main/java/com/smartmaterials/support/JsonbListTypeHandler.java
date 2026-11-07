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
import java.util.ArrayList;
import java.util.List;

/** PostgreSQL jsonb ↔ List&lt;String&gt; 映射（流水附件、照片等）。 */
@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonbListTypeHandler extends BaseTypeHandler<List<String>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {
    };

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter,
                                    JdbcType jdbcType) throws SQLException {
        try {
            ps.setObject(i, MAPPER.writeValueAsString(parameter), Types.OTHER);
        } catch (Exception e) {
            throw new SQLException("无法序列化 jsonb 数组: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private List<String> parse(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(json, LIST_TYPE);
        } catch (Exception e) {
            throw new SQLException("无法解析 jsonb 数组: " + e.getMessage(), e);
        }
    }
}
