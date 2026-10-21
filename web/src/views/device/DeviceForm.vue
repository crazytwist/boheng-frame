<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="760px">
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="120px">
      <el-divider content-position="left">基本信息</el-divider>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="设备编码" prop="deviceCode">
            <el-input v-model="formData.deviceCode" maxlength="64" placeholder="请输入设备编码，如 DEV-001" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="设备名称" prop="deviceName">
            <el-input v-model="formData.deviceName" maxlength="128" placeholder="请输入设备名称" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="设备类型" prop="deviceTypeCode">
            <el-select v-model="formData.deviceTypeCode" placeholder="请选择设备类型" class="!w-1/1">
              <el-option v-for="item in deviceTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="设备状态" prop="status">
            <el-select v-model="formData.status" placeholder="请选择设备状态" class="!w-1/1">
              <el-option label="在线" value="ONLINE" />
              <el-option label="离线" value="OFFLINE" />
              <el-option label="维护中" value="MAINTENANCE" />
              <el-option label="故障" value="FAULT" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="厂商" prop="vendor">
            <el-input v-model="formData.vendor" maxlength="128" placeholder="请输入厂商" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="型号" prop="model">
            <el-input v-model="formData.model" maxlength="128" placeholder="请输入型号" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="序列号" prop="serialNo">
            <el-input v-model="formData.serialNo" maxlength="128" placeholder="请输入序列号" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="设备图片">
        <div class="image-upload-wrap">
          <UploadImg v-model="formData.imageUrl" />
          <span class="form-tip">选填，未上传时显示默认图片</span>
        </div>
      </el-form-item>

      <el-divider content-position="left">接入配置</el-divider>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="接入方式" prop="connectionType">
            <el-select v-model="formData.connectionType" placeholder="请选择接入方式" class="!w-1/1">
              <el-option label="HTTP 直连" value="HTTP" />
              <el-option label="MQTT 直连" value="MQTT" />
              <el-option label="Node-RED 转接" value="NODE_RED" />
              <el-option label="仿真" value="SIMULATED" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="驱动类型" prop="driverType">
            <el-select v-model="formData.driverType" placeholder="请选择驱动类型" class="!w-1/1">
              <el-option label="Tecan Reader.NETwork" value="TECAN_READER_NETWORK" />
              <el-option label="仿真驱动" value="SIMULATED" />
              <el-option label="通用 HTTP" value="GENERIC_HTTP" />
              <el-option label="通用 MQTT" value="GENERIC_MQTT" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item v-if="formData.connectionType !== 'SIMULATED'" label="接入地址" prop="endpointUrl">
        <el-input v-model="formData.endpointUrl" maxlength="512" :placeholder="endpointPlaceholder" />
      </el-form-item>
      <el-form-item v-if="formData.connectionType === 'MQTT' || formData.connectionType === 'NODE_RED'" label="MQTT 主题前缀" prop="mqttTopicPrefix">
        <el-input v-model="formData.mqttTopicPrefix" maxlength="128" placeholder="请输入主题前缀，如 device/DEV-001/" />
      </el-form-item>
      <el-form-item label="仿真模式">
        <el-switch v-model="formData.simulationMode" active-text="开启" inactive-text="关闭" />
        <span class="form-tip">开启后命令由平台仿真器应答，不访问真实设备</span>
      </el-form-item>

      <template v-if="formData.connectionType === 'HTTP' || formData.connectionType === 'NODE_RED'">
        <el-divider content-position="left">登录认证</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="请求方法" prop="loginMethod">
              <el-select v-model="formData.loginMethod" clearable placeholder="POST" class="!w-1/1">
                <el-option label="POST" value="POST" />
                <el-option label="GET" value="GET" />
                <el-option label="PUT" value="PUT" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="登录路径" label-width="90px">
              <el-input v-model="formData.loginPath" maxlength="255" placeholder="相对路径，如 /api/login；为空表示设备无需登录" />
            </el-form-item>
          </el-col>
        </el-row>
        <template v-if="formData.loginPath">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="用户名">
                <el-input v-model="formData.loginUsername" maxlength="128" placeholder="请输入用户名" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="密码">
                <el-input
                  v-model="formData.loginPassword"
                  maxlength="255"
                  type="password"
                  show-password
                  autocomplete="new-password"
                  :placeholder="formData.loginConfigured ? '已保存，留空表示不修改' : '请输入密码'"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="用户名字段">
                <el-input v-model="formData.loginUsernameKey" maxlength="64" placeholder="默认 username" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="密码字段">
                <el-input v-model="formData.loginPasswordKey" maxlength="64" placeholder="默认 password" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="令牌路径">
                <el-input v-model="formData.tokenPath" maxlength="128" placeholder="响应中令牌的 JSON 路径，如 data.accessToken" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="令牌有效期(秒)" prop="tokenTtlSec">
                <el-input-number v-model="formData.tokenTtlSec" :min="1" :max="86400" controls-position="right" class="!w-1/1" placeholder="默认 1800" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="令牌请求头">
                <el-input v-model="formData.tokenHeader" maxlength="64" placeholder="默认 Authorization" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="令牌前缀">
                <el-input v-model="formData.tokenPrefix" maxlength="32" placeholder="默认 Bearer" />
              </el-form-item>
            </el-col>
          </el-row>
        </template>
      </template>

      <el-divider content-position="left">结果获取</el-divider>
      <el-form-item label="结果回调">
        <el-switch v-model="formData.callbackEnabled" active-text="支持" inactive-text="不支持" />
        <span class="form-tip">设备执行完成后能否主动将结果推送给平台</span>
      </el-form-item>
      <template v-if="formData.callbackEnabled">
        <el-form-item label="回调地址" prop="callbackUrl">
          <div class="w-1/1">
            <el-input v-model="formData.callbackUrl" maxlength="512" clearable placeholder="选填，为空时使用平台默认回调地址" />
            <div class="field-tip">
              平台下发命令时通过请求头 X-Callback-Url 告知设备回调地址，请求模板中也可引用 ${callbackUrl}。
              仅当设备只能回调固定地址时需要填写，支持 ${commandNo}、${callbackToken} 占位符；地址中不含令牌时，设备须在请求头 X-Callback-Token 中回传令牌。
            </div>
          </div>
        </el-form-item>
        <el-form-item label="回调超时(秒)" prop="callbackTimeoutSec">
          <el-input-number v-model="formData.callbackTimeoutSec" :min="1" :max="600" controls-position="right" placeholder="300" />
          <span class="form-tip">动作未单独配置时采用此值，超时未回调的命令记为“已超时”</span>
        </el-form-item>
      </template>
      <el-form-item label="轮询间隔(秒)" prop="pollIntervalSec">
        <el-input-number v-model="formData.pollIntervalSec" :min="1" :max="3600" controls-position="right" placeholder="默认 5" />
        <span class="form-tip">以轮询方式获取结果时，两次查询之间的间隔</span>
      </el-form-item>

      <el-divider content-position="left">调度策略</el-divider>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="空闲检查" prop="busyCheckPolicy">
            <el-select v-model="formData.busyCheckPolicy" placeholder="请选择" class="!w-1/1">
              <el-option label="按动作配置" value="AUTO" />
              <el-option label="下发前必须检查" value="ALWAYS" />
              <el-option label="不检查" value="NEVER" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="并发策略" prop="concurrencyPolicy">
            <el-select v-model="formData.concurrencyPolicy" placeholder="请选择" class="!w-1/1">
              <el-option label="平台独占（一次一条）" value="EXCLUSIVE" />
              <el-option label="设备本地排队" value="DEVICE_QUEUED" />
              <el-option label="平台排队" value="PLATFORM_QUEUED" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="最大并发数" prop="maxInflight">
        <el-input-number
          v-model="formData.maxInflight"
          :min="1"
          :max="100"
          :disabled="formData.concurrencyPolicy !== 'DEVICE_QUEUED'"
          controls-position="right"
        />
        <span class="form-tip">仅“设备本地排队”时生效，填写设备队列深度；其他策略固定为 1</span>
      </el-form-item>
      <el-alert class="!mb-18px" type="info" :closable="false" show-icon>
        <template #title>
          设备不上报空闲状态时，请将“空闲检查”设为<strong>不检查</strong>，否则命令无法下发；
          设备自带任务队列时，请将“并发策略”设为<strong>设备本地排队</strong>，平台即可连续下发。
        </template>
      </el-alert>

      <el-divider content-position="left">其他</el-divider>
      <el-form-item label="备注">
        <el-input v-model="formData.remark" maxlength="512" show-word-limit placeholder="请输入备注" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import { DeviceInfoApi, DeviceVO } from '@/api/device'
import { UploadImg } from '@/components/UploadFile'
import { useDeviceTypeDictOptions } from './labels'

defineOptions({ name: 'WmsDeviceInfoForm' })

const { t } = useI18n()
const message = useMessage()

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')

const createEmptyFormData = (): DeviceVO => ({
  id: undefined,
  deviceCode: undefined,
  deviceName: undefined,
  imageUrl: undefined,
  deviceTypeCode: undefined,
  model: undefined,
  vendor: undefined,
  serialNo: undefined,
  driverType: 'SIMULATED',
  status: 'OFFLINE',
  connectionType: 'SIMULATED',
  endpointUrl: undefined,
  loginPath: undefined,
  loginMethod: 'POST',
  loginUsername: undefined,
  loginPassword: undefined,
  loginConfigured: false,
  loginUsernameKey: 'username',
  loginPasswordKey: 'password',
  tokenPath: 'token',
  tokenHeader: 'Authorization',
  tokenPrefix: 'Bearer',
  tokenTtlSec: 1800,
  mqttTopicPrefix: undefined,
  callbackEnabled: false,
  callbackUrl: undefined,
  callbackTimeoutSec: 300,
  pollIntervalSec: 5,
  busyCheckPolicy: 'AUTO',
  concurrencyPolicy: 'EXCLUSIVE',
  maxInflight: 1,
  inflightCount: 0,
  capabilitySource: 'MANUAL',
  telemetryJson: undefined,
  simulationMode: true,
  discoveryType: 'MANUAL',
  currentCommandId: undefined,
  lockHolder: undefined,
  lockType: undefined,
  lockAcquiredTime: undefined,
  lockExpireTime: undefined,
  lockReason: undefined,
  remark: undefined
})

const formData = ref<DeviceVO>(createEmptyFormData())

const urlPattern = /^https?:\/\/.+/i

const endpointPlaceholder = computed(() => {
  switch (formData.value.connectionType) {
    case 'MQTT':
      return '请输入 Broker 地址，如 tcp://192.168.1.10:1883'
    case 'NODE_RED':
      return '请输入 Node-RED 入站节点地址，如 http://192.168.1.10:1880/device'
    default:
      return '请输入设备服务根地址，如 http://192.168.1.100:8080'
  }
})

const formRules = reactive({
  deviceCode: [{ required: true, message: '设备编码不能为空', trigger: 'blur' }],
  deviceName: [{ required: true, message: '设备名称不能为空', trigger: 'blur' }],
  deviceTypeCode: [{ required: true, message: '设备类型不能为空', trigger: 'change' }],
  driverType: [{ required: true, message: '驱动类型不能为空', trigger: 'change' }],
  status: [{ required: true, message: '设备状态不能为空', trigger: 'change' }],
  connectionType: [{ required: true, message: '接入方式不能为空', trigger: 'change' }],
  endpointUrl: [
    {
      validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
        const type = formData.value.connectionType
        if ((type === 'HTTP' || type === 'NODE_RED') && !formData.value.simulationMode) {
          if (!value) return callback(new Error('接入地址不能为空'))
          if (!urlPattern.test(value)) return callback(new Error('接入地址须以 http:// 或 https:// 开头'))
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  callbackUrl: [{ pattern: urlPattern, message: '回调地址须以 http:// 或 https:// 开头', trigger: 'blur' }]
})
const formRef = ref()

const deviceTypeOptions = useDeviceTypeDictOptions()

const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  formData.value = createEmptyFormData()
  formRef.value?.clearValidate()
  if (id) {
    formLoading.value = true
    try {
      const data = await DeviceInfoApi.getDevice(id)
      Object.assign(formData.value, data)
    } finally {
      formLoading.value = false
    }
  }
}

const submitForm = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  formLoading.value = true
  try {
    if (formType.value === 'create') {
      await DeviceInfoApi.createDevice(formData.value)
      message.success(t('common.createSuccess'))
    } else {
      await DeviceInfoApi.updateDevice(formData.value)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

defineExpose({ open })
const emit = defineEmits<{ success: [] }>()
</script>

<style lang="scss" scoped>
.image-upload-wrap {
  display: flex;
  align-items: flex-start;
}
.form-tip {
  margin-left: 12px;
  font-size: 12px;
  line-height: 32px;
  color: var(--el-text-color-secondary);
}
.field-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 20px;
  color: var(--el-text-color-secondary);
}
</style>
