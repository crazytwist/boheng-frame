package cn.boheng.frame.module.device.service.template;

import cn.hutool.core.util.StrUtil;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 报文模板里的内置函数占位符。
 *
 * <p>参数替换只能表达「调用方给的值」，像 RequestId / EventId / reqCode 这类每次下发都要变的
 * 流水号用参数没法表达，所以在替换参数之前先把函数占位符展开。</p>
 *
 * <p>约定：</p>
 * <ul>
 *   <li>{@code ${uuid()}} —— 每次出现都生成一个新的小写带连字符 UUID（同一模板写两次得到两个不同的值）</li>
 *   <li>{@code ${randomHex(16)}} —— 生成指定位数的大写十六进制串，位数省略时按 16 位。
 *       海康 RCS 的 {@code reqCode} 这类「定长十六进制流水号」用它，UUID 的格式对不上</li>
 *   <li>函数名忽略大小写和括号内空白</li>
 * </ul>
 *
 * @author yinan
 */
public final class MessageTemplateFunctions {

    /**
     * UUID 函数名。保存期校验与运行期展开共用，避免两处各写一份。
     */
    public static final String UUID_FUNCTION_NAME = "uuid";

    /**
     * 定长十六进制流水号函数名（比较时统一用小写）
     */
    public static final String RANDOM_HEX_FUNCTION_NAME = "randomhex";

    /**
     * 允许出现在模板里的函数名。保存期校验据此判断「这个函数认不认识」
     */
    private static final Set<String> SUPPORTED_FUNCTIONS =
            Set.of(UUID_FUNCTION_NAME, RANDOM_HEX_FUNCTION_NAME);

    /**
     * 省略位数时的默认长度
     */
    private static final int DEFAULT_HEX_LENGTH = 16;

    /**
     * 位数上限。放太长没意义，还会把报文撑大
     */
    private static final int MAX_HEX_LENGTH = 128;

    /**
     * 十六进制字节生成器
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 十六进制输出，统一大写
     */
    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    /**
     * 内置函数占位符，如 ${uuid()}、${randomHex(16)}
     */
    private static final Pattern FUNCTION_CALL = Pattern.compile(
            "\\$\\{\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\(\\s*([^{}()]*)\\s*\\)\\s*}");

    /**
     * 工具类，不创建实例
     */
    private MessageTemplateFunctions() {
    }

    /**
     * 展开模板里的内置函数占位符。没有函数占位符时原样返回。
     */
    public static String apply(String template) {
        if (StrUtil.isBlank(template) || !hasFunction(template)) {
            return template;
        }
        Matcher matcher = FUNCTION_CALL.matcher(template);
        StringBuilder rendered = new StringBuilder(template.length() + 64);
        while (matcher.find()) {
            String replacement = evaluate(matcher.group(1), matcher.group(2));
            // 值里可能带 $ 或 \，必须转义，否则 appendReplacement 会当成分组引用
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    /**
     * 函数名是否是内置支持的。比较时忽略大小写。
     *
     * @param name 模板里写的函数名
     */
    public static boolean isSupportedFunction(String name) {
        return name != null && SUPPORTED_FUNCTIONS.contains(name.toLowerCase(Locale.ROOT));
    }

    /**
     * 按函数名算出替换值。不认识的函数原样保留，由保存期校验拦住，运行期也不会静默吃掉。
     */
    private static String evaluate(String name, String argument) {
        String function = name.toLowerCase(Locale.ROOT);
        if (UUID_FUNCTION_NAME.equals(function)) {
            return UUID.randomUUID().toString();
        }
        if (RANDOM_HEX_FUNCTION_NAME.equals(function)) {
            return randomHex(hexLength(argument));
        }
        return "${" + name + "(" + argument + ")}";
    }

    /**
     * 读取调用里写的位数，非法或省略时用默认值，超界时夹到上限
     */
    private static int hexLength(String argument) {
        String text = StrUtil.trimToEmpty(argument);
        if (text.isEmpty()) {
            return DEFAULT_HEX_LENGTH;
        }
        try {
            return Math.min(Math.max(Integer.parseInt(text), 1), MAX_HEX_LENGTH);
        } catch (NumberFormatException ex) {
            return DEFAULT_HEX_LENGTH;
        }
    }

    /**
     * 生成定长大写十六进制串
     */
    private static String randomHex(int length) {
        byte[] bytes = new byte[(length + 1) / 2];
        RANDOM.nextBytes(bytes);
        return HEX.formatHex(bytes).substring(0, length);
    }

    /**
     * 粗筛一下，避免每个报文都跑正则
     */
    private static boolean hasFunction(String template) {
        return template.contains("${") && template.contains("(");
    }

}
