package cn.boheng.frame.module.wms.util;

import java.util.HashMap;
import java.util.Map;

/**
 * @ClassName CodeConvertUtil
 * @Author mengke
 * @create 2025/12/4 20:25
 * @Description
 */
public final class LabwareCodeConvertUtil {

    private LabwareCodeConvertUtil() {
    }

    // ======================
    //  前缀映射（固定 1~7）
    // ======================
    private static final Map<String, Integer> PREFIX_TO_NUM = new HashMap<>();
    private static final Map<Integer, String> NUM_TO_PREFIX = new HashMap<>();

    // ======================
    //  中间码映射（你自己扩展）
    // ======================
    private static final Map<String, Integer> MID_TO_NUM = new HashMap<>();
    private static final Map<Integer, String> NUM_TO_MID = new HashMap<>();

    static {

        // ===== 前缀码（1 位） =====
        PREFIX_TO_NUM.put("EQ", 1);
        PREFIX_TO_NUM.put("LW", 2);
        PREFIX_TO_NUM.put("RG", 3);
        PREFIX_TO_NUM.put("SP", 4);
        PREFIX_TO_NUM.put("CR", 5);
        PREFIX_TO_NUM.put("CS", 6);
        PREFIX_TO_NUM.put("AREA", 7);

        PREFIX_TO_NUM.forEach((k, v) -> NUM_TO_PREFIX.put(v, k));

        // ===== 中间码（你自己添加） =====
        // 器具
        MID_TO_NUM.put("T75", 10);
        MID_TO_NUM.put("10PIP", 11);
        MID_TO_NUM.put("1PIP", 12);
        MID_TO_NUM.put("50CT", 13);
        MID_TO_NUM.put("96B", 14);
        MID_TO_NUM.put("6B", 15);
        MID_TO_NUM.put("2CT", 16);
        MID_TO_NUM.put("50SY", 17);
        MID_TO_NUM.put("2LT", 18);

        // 载架
        MID_TO_NUM.put("50ML", 40);

        // 样本
        MID_TO_NUM.put("293T", 60);
        MID_TO_NUM.put("JACKET", 61);
        MID_TO_NUM.put("UNK", 99);

        MID_TO_NUM.forEach((k, v) -> NUM_TO_MID.put(v, k));
    }

    /**
     * 物料实例 → 清华 9 位器具 id。优先 {@code instanceCode}（PREFIX-MID-SEQ），否则按主键生成 LW-UNK-xxxxxx。
     */
    public static int encodeMaterialInstance(String instanceCode, Long instanceId) {
        if (instanceCode != null && instanceCode.split("-").length == 3) {
            try {
                return encode(instanceCode);
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        long seq = instanceId != null ? (instanceId % 1_000_000) : 0;
        return encode(String.format("LW-UNK-%06d", seq));
    }

    // ===========================
//  字符串 → 9 位数字编码（修正版）
// ===========================
    public static int encode(String code) {
        // 例如：SP-T75-000009
        String[] arr = code.split("-");
        if (arr.length != 3) {
            throw new IllegalArgumentException("格式必须是 PREFIX-MID-SEQ，例如 SP-T75-000009");
        }

        String prefix = arr[0];
        String mid = arr[1];
        String seq = arr[2];

        Integer typeNum = PREFIX_TO_NUM.get(prefix);
        if (typeNum == null) throw new IllegalArgumentException("未知前缀：" + prefix);

        Integer midNum = MID_TO_NUM.get(mid);
        if (midNum == null) throw new IllegalArgumentException("未知中间码：" + mid);

        int seqNum;
        try {
            seqNum = Integer.parseInt(seq);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("序号部分必须为数字，例如 000009");
        }
        if (seqNum < 0 || seqNum > 999999) {
            throw new IllegalArgumentException("序号必须为 000000~999999");
        }

        // 修正位权：1位(type) + 2位(mid) + 6位(seq) = 9 位
        return typeNum * 100_000_000   // T × 10^8
                + midNum * 1_000_000   // MM × 10^6
                + seqNum;              // SSSSSS
    }

    // ===========================
//  9 位数字编码 → 字符串（修正版）
// ===========================
    public static String decode(int value) {
        if (value < 100_000_000 || value > 999_999_999) {
            throw new IllegalArgumentException("必须是 9 位数字编码，范围 100000000 ~ 999999999");
        }

        int typeNum = value / 100_000_000;           // 第1位
        int midNum = (value / 1_000_000) % 100;     // 中间两位
        int seq = value % 1_000_000;             // 最后 6 位

        String prefix = NUM_TO_PREFIX.get(typeNum);
        if (prefix == null) throw new IllegalArgumentException("未知前缀编号：" + typeNum);

        String mid = NUM_TO_MID.get(midNum);
        if (mid == null) mid = "UNKNOWN";

        return String.format("%s-%s-%06d", prefix, mid, seq);
    }


    public static void main(String[] args) {
        String demo = "SP-T75-000009";

        int encoded = encode(demo);
        System.out.println("encode = " + encoded);

        String decoded = decode(encoded);
        System.out.println("decode = " + decoded);
    }
}