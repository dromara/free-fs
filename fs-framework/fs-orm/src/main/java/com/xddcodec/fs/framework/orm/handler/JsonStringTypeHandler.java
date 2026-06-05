package com.xddcodec.fs.framework.orm.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * JSON 列 String 互转 TypeHandler。
 *
 * <p>适配 PostgreSQL json / MySQL json 列:写入时通过 {@link Types#OTHER} 让驱动按列元数据类型识别,
 * 避免 PostgreSQL 因不存在 varchar → json 隐式转换而抛
 * <em>"字段 ... 的类型为 json, 但表达式的类型为 character varying"</em>。</p>
 *
 * <p>使用方式:在实体字段上显式声明
 * <pre>{@code
 * @Column(jdbcType = JdbcType.OTHER, typeHandler = JsonStringTypeHandler.class)
 * private String configScheme;
 * }</pre>
 * 读端直接走 {@link ResultSet#getString},驱动会按列类型反序列化为 JSON 文本。</p>
 *
 * @author xddcode
 */
@MappedTypes(String.class)
@MappedJdbcTypes(value = JdbcType.OTHER, includeNullJdbcType = true)
public class JsonStringTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        // Types.OTHER + String -> PostgreSQL/MySQL 驱动按列元数据类型(json)解析并校验
        ps.setObject(i, parameter, Types.OTHER);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getString(columnIndex);
    }
}
