import request from '@/config/axios'

/**
 * 步骤类型「类型配置 / 默认入参」的表单化 schema。
 *
 * 背景：typeConfig / defaultParams 后端存的是 JSON 字符串，此前前端只能手敲 JSON。
 * 这里把每种 step type 的可配置字段声明成一份 schema，SchemaForm 组件据此渲染表单，
 * 提交时序列化回 JSON 字符串——后端存储格式完全不变。
 */

/** 字段控件类型 */
export type FieldType =
  | 'string' // 单行输入
  | 'textarea' // 多行输入
  | 'number' // 数字
  | 'boolean' // 开关
  | 'enum' // 固定枚举下拉
  | 'device' // 设备远程下拉
  | 'action' // 动作远程下拉（可按所选设备类型过滤）
  | 'codec' // 编解码远程下拉
  | 'beanAction' // bean.invoke 白名单下拉
  | 'router' // route 决策器白名单下拉
  | 'validator' // validate 校验 Bean 白名单下拉
  | 'json' // 自由 JSON（键值对编辑器）

/** 一条字段声明 */
export interface ParamField {
  /** 字段路径，支持点分嵌套（如 route 的 router.kind） */
  key: string
  /** 中文标签 */
  label: string
  /** 控件类型 */
  type: FieldType
  /** 是否必填 */
  required?: boolean
  /** 占位提示 */
  placeholder?: string
  /** 枚举选项 */
  options?: { label: string; value: string }[]
  /** 附加说明 */
  hint?: string
  /** 条件显示：当某字段等于某值时显示 */
  when?: { key: string; value: string }
  /** 依赖的字段 key（action 依赖 device，选设备后按类型过滤动作） */
  dependsOn?: string
}

/**
 * 各 step type 的类型配置字段表。
 * 未在此声明的字段（如 route 的 matchMode）在表单里不显示、但会被原样保留，
 * 不会被覆盖丢失。
 *
 * ┌───────────────────── 能力目录（step_type，11 种）速查 ─────────────────────┐
 * │ 能力节点（执行实际动作）                                                    │
 * │  · device.command     下发一条指令给指定设备，可选编解码解析设备回复        │
 * │  · device.task        向设备任务队列派发异步任务，动作决定任务类型          │
 * │  · bean.invoke        直接调用后端白名单内的动作 Bean（同步执行）           │
 * │  · script.run         运行一段表达式，结果可写回节点输出                    │
 * │  · manual.task        人工任务，流程暂停等操作员在界面处理                   │
 * │  · notification.send  发一条站内通知                                       │
 * │ 控制节点（不执行能力，只编排流程走向）                                     │
 * │  · route              路由分支：按表达式或 Bean 决定走哪条出边              │
 * │  · loop               循环：重复执行循环体直到条件不满足或达最大轮数        │
 * │  · subflow            子流程：调用另一个已发布流程，可单派或动态扇出        │
 * │  · timer.wait         定时等待：暂停指定秒数或等到某时间点                  │
 * │  · validate           前置校验：调用校验 Bean 做资源/规则校验               │
 * └────────────────────────────────────────────────────────────────────────────┘
 *
 * 字段联动说明：
 *  · dependsOn   —— action 依赖 device，选中设备后按设备类型过滤可选动作
 *  · when        —— 条件显示，如 route 的 expression/beanName 只随 router.kind 切换
 *  · 点分 key     —— 写入嵌套对象，如 route 的 router.kind → typeConfig.router.kind
 */
export const STEP_TYPE_FIELDS: Record<string, ParamField[]> = {
  /** 设备指令：选设备 → 选动作（按设备能力过滤）→ 可选编解码解析回复 */
  'device.command': [
    { key: 'deviceCode', label: '设备', type: 'device', required: true, placeholder: '请选择设备' },
    { key: 'actionCode', label: '动作', type: 'action', required: true, dependsOn: 'deviceCode', placeholder: '请选择动作' },
    { key: 'codecCode', label: '编解码', type: 'codec', placeholder: '选填，选设备回复解析规则' }
  ],
  /** 设备异步任务：派发到任务队列，设备Code可留空（不限设备），动作限定任务类型 */
  'device.task': [
    { key: 'deviceCode', label: '设备', type: 'device', placeholder: '可留空，不限设备' },
    { key: 'actionCode', label: '任务类型', type: 'action', required: true, dependsOn: 'deviceCode', placeholder: '请选择任务类型' }
  ],
  /** Bean 调用：同步执行后端白名单动作 Bean，固定参数以键值对传入 */
  'bean.invoke': [
    { key: 'beanName', label: '调用 Bean', type: 'string', required: true, placeholder: '请输入已注册的 Bean 名称，如 waterRegisterAction' },
    { key: 'argsJson', label: '固定参数', type: 'json', hint: '传给 Bean 的固定参数（键值对）' }
  ],
  /** 脚本执行：运行一段表达式（可引用 input），结果可写回指定键名 */
  'script.run': [
    { key: 'expression', label: '表达式', type: 'textarea', required: true, placeholder: '如 input.speed * 2' },
    { key: 'resultKey', label: '结果键', type: 'string', placeholder: '选填，写进节点输出的键名' }
  ],
  /** 人工任务：流程暂停，等操作员在界面按说明处理后继续 */
  'manual.task': [
    { key: 'instruction', label: '操作说明', type: 'textarea', required: true, placeholder: '告诉操作员要做什么' },
    { key: 'requireComment', label: '需要备注', type: 'boolean' }
  ],
  /** 通知：给指定接收人发站内通知 */
  'notification.send': [
    { key: 'title', label: '标题', type: 'string', placeholder: '通知标题' },
    { key: 'content', label: '内容', type: 'textarea', placeholder: '通知内容' },
    { key: 'receiverIds', label: '接收人', type: 'string', placeholder: '多个用逗号分隔' }
  ],
  /** 路由分支：先选决策方式（表达式/Bean），再填对应配置，结果决定走哪条出边 */
  route: [
    {
      key: 'router.kind',
      label: '决策方式',
      type: 'enum',
      required: true,
      options: [
        { label: '表达式', value: 'expression' },
        { label: 'Bean', value: 'bean' }
      ]
    },
    {
      key: 'router.expression',
      label: '表达式',
      type: 'textarea',
      placeholder: "如 input.od > 1.0 ? 'high' : 'default'",
      when: { key: 'router.kind', value: 'expression' }
    },
    { key: 'router.beanName', label: '决策 Bean', type: 'router', placeholder: '请选择路由决策器', when: { key: 'router.kind', value: 'bean' } }
  ],
  /** 循环：指定循环体入口/出口节点，按条件重复执行，可设最大轮数兜底 */
  loop: [
    { key: 'bodyEntryNodeId', label: '循环体入口', type: 'string', placeholder: '入口节点编码' },
    { key: 'bodyExitNodeId', label: '循环体出口', type: 'string', placeholder: '出口节点编码' },
    { key: 'maxIterations', label: '最大轮数', type: 'number', placeholder: '空则不设上限' },
    { key: 'whileExpression', label: '循环条件', type: 'textarea', placeholder: '如 sys.needMore == true' }
  ],
  /** 子流程：调用另一个已发布流程；单派填编码/版本/入参，扇出则运行时动态展开 */
  subflow: [
    {
      key: 'dispatchMode',
      label: '派发方式',
      type: 'enum',
      options: [
        { label: '单一子流程', value: 'SINGLE' },
        { label: '动态扇出', value: 'FANOUT' }
      ]
    },
    { key: 'pipelineKey', label: '子流程编码', type: 'string', placeholder: '目标流程编码', when: { key: 'dispatchMode', value: 'SINGLE' } },
    { key: 'version', label: '版本', type: 'number', placeholder: '空则取已发布最新版', when: { key: 'dispatchMode', value: 'SINGLE' } },
    { key: 'inputJson', label: '入参', type: 'json', hint: '传给子流程的入参（键值对）', when: { key: 'dispatchMode', value: 'SINGLE' } }
  ],
  /** 定时等待：暂停指定秒数后继续 */
  'timer.wait': [
    { key: 'waitSeconds', label: '等待秒数', type: 'number', required: true, placeholder: '等待多少秒（1~86400）' }
  ],
  /** 前置校验：调用校验 Bean 做资源/规则校验，固定参数键值对传入 */
  validate: [
    { key: 'beanName', label: '校验 Bean', type: 'validator', required: true, placeholder: '请选择校验动作' },
    { key: 'argsJson', label: '固定参数', type: 'json', hint: '传给校验 Bean 的固定参数（键值对）' }
  ]
}

/** 字段是否为远程白名单下拉（数据来自 /flow/step-type/beans） */
export const isBeanField = (type: FieldType) =>
  type === 'beanAction' || type === 'router' || type === 'validator'

/** 步骤类型 Bean 白名单 VO */
export interface FlowStepTypeBeansVO {
  beanActions?: string[]
  routers?: string[]
  validators?: string[]
}

/** 步骤类型元数据 API */
export const FlowStepTypeApi = {
  /** 获得可调用 Bean 白名单（bean.invoke / route / validate 三套） */
  getBeans: async () => {
    return await request.get({ url: '/flow/step-type/beans' })
  }
}
