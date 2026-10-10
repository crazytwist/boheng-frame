package cn.boheng.frame.module.flow.enums;

/**
 * 汇合策略。一个节点有多个前置时，满足什么条件才算就绪。
 */
public enum JoinPolicy {

    /**
     * 全部前置成功才就绪。默认
     */
    ALL_SUCCESS,

    /**
     * 任一前置成功即就绪，其余未终态的前置继续跑但不再阻塞本节点
     */
    ANY_SUCCESS,

    /**
     * 全部前置进入终态（不论成败）即就绪
     */
    ALL_DONE

}
