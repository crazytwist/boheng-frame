package cn.boheng.frame.module.device.framework.mybatis;

import cn.boheng.frame.framework.mybatis.core.type.EncryptTypeHandler;
import cn.hutool.core.lang.Assert;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.extra.spring.SpringUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 设备登录密码这类敏感列的读写。
 *
 * 写入一律用框架的 AES 加密；读出时解不开就当作历史明文原样返回，下次保存时自然变成密文，
 * 这样已有数据不用停机迁移。密钥与框架共用 mybatis-plus.encryptor.password。
 */
public class DeviceSecretTypeHandler extends BaseTypeHandler<String> {

    /**
     * 密钥配置项
     */
    private static final String ENCRYPTOR_PROPERTY_NAME = "mybatis-plus.encryptor.password";

    /**
     * 解密器，首次使用时创建
     */
    private static volatile AES aes;

    /**
     * 写库前加密
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, EncryptTypeHandler.encrypt(parameter));
    }

    /**
     * 按列名读出并解密
     */
    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return decrypt(rs.getString(columnName));
    }

    /**
     * 按列序号读出并解密
     */
    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return decrypt(rs.getString(columnIndex));
    }

    /**
     * 存储过程结果读出并解密
     */
    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return decrypt(cs.getString(columnIndex));
    }

    /**
     * 解密；不是本密钥加密的内容视为历史明文
     */
    private static String decrypt(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        try {
            return encryptor().decryptStr(value);
        } catch (Exception ex) {
            return value;
        }
    }

    /**
     * 与框架同一把 AES 密钥
     */
    private static AES encryptor() {
        if (aes == null) {
            String password = SpringUtil.getProperty(ENCRYPTOR_PROPERTY_NAME);
            Assert.notEmpty(password, "配置项({}) 不能为空", ENCRYPTOR_PROPERTY_NAME);
            aes = SecureUtil.aes(password.getBytes());
        }
        return aes;
    }

}
