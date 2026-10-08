<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="680px">
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="110px">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="设备编码" prop="deviceCode">
            <el-input v-model="formData.deviceCode" maxlength="64" placeholder="如 DEV-001" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="设备名称" prop="deviceName">
            <el-input v-model="formData.deviceName" maxlength="128" placeholder="如 读板机 1 号" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="设备类型" prop="deviceTypeCode">
            <el-select v-model="formData.deviceTypeCode" placeholder="请选择" class="!w-1/1">
              <el-option v-for="item in deviceTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="驱动类型" prop="driverType">
            <el-select v-model="formData.driverType" placeholder="请选择" class="!w-1/1">
              <el-option label="Tecan Reader.NETwork" value="TECAN_READER_NETWORK" />
              <el-option label="仿真驱动" value="SIMULATED" />
              <el-option label="通用 HTTP" value="GENERIC_HTTP" />
              <el-option label="通用 MQTT" value="GENERIC_MQTT" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="厂商" prop="vendor">
            <el-input v-model="formData.vendor" maxlength="128" placeholder="如 Tecan" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="型号" prop="model">
            <el-input v-model="formData.model" maxlength="128" placeholder="如 Infinite 200 PRO" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="序列号" prop="serialNo">
            <el-input v-model="formData.serialNo" maxlength="128" placeholder="如 SN123456" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="接入方式" prop="connectionType">
            <el-select v-model="formData.connectionType" placeholder="请选择" class="!w-1/1">
              <el-option label="HTTP 直连" value="HTTP" />
              <el-option label="MQTT 直连" value="MQTT" />
              <el-option label="经 Node-RED" value="NODE_RED" />
              <el-option label="仿真" value="SIMULATED" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="接入地址" prop="endpointUrl">
        <el-input v-model="formData.endpointUrl" maxlength="512" placeholder="HTTP=base url；MQTT=broker host:port；Node-RED=入站节点 url" />
      </el-form-item>

      <el-form-item v-if="formData.connectionType === 'MQTT' || formData.connectionType === 'NODE_RED'" label="MQTT 主题前缀" prop="mqttTopicPrefix">
        <el-input v-model="formData.mqttTopicPrefix" maxlength="128" placeholder="如 device/DEV-001/" />
      </el-form-item>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="在线状态" prop="status">
            <el-select v-model="formData.status" placeholder="请选择" class="!w-1/1">
              <el-option label="在线" value="ONLINE" />
              <el-option label="离线" value="OFFLINE" />
              <el-option label="维护中" value="MAINTENANCE" />
              <el-option label="故障" value="FAULT" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="能否主动推送">
            <el-switch v-model="formData.callbackEnabled" active-text="能" inactive-text="否" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="轮询间隔(秒)">
            <el-input-number v-model="formData.pollIntervalSec" :min="1" controls-position="right" class="!w-1/1" placeholder="默认 5" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="仿真模式">
            <el-switch v-model="formData.simulationMode" active-text="是" inactive-text="否" />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- ★ 忙闲校验与并发策略：仪器能力差异大，必须可配 -->
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="忙闲校验" prop="busyCheckPolicy">
            <el-select v-model="formData.busyCheckPolicy" placeholder="请选择" class="!w-1/1">
              <el-option label="按动作决定 (AUTO)" value="AUTO" />
              <el-option label="下发前强制预检 (ALWAYS)" value="ALWAYS" />
              <el-option label="从不预检 (NEVER)" value="NEVER" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="并发策略" prop="concurrencyPolicy">
            <el-select v-model="formData.concurrencyPolicy" placeholder="请选择" class="!w-1/1">
              <el-option label="平台独占，一次一条 (EXCLUSIVE)" value="EXCLUSIVE" />
              <el-option label="仪器自带本地队列 (DEVICE_QUEUED)" value="DEVICE_QUEUED" />
              <el-option label="平台侧排队 (PLATFORM_QUEUED)" value="PLATFORM_QUEUED" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="最大在途数">
        <el-input-number
          v-model="formData.maxInflight"
          :min="1"
          :max="99"
          :disabled="formData.concurrencyPolicy === 'EXCLUSIVE'"
          controls-position="right"
        />
        <span class="form-tip">独占模式恒为 1；仪器本地排队时填其队列深度</span>
      </el-form-item>

      <el-alert class="!mb-18px" type="info" :closable="false" show-icon>
        <template #title>
          仪器若不返回空闲状态，请把「忙闲校验」设为<strong>从不预检</strong>，否则命令永远发不出去；
          仪器自带本地队列时把「并发策略」设为<strong>仪器自带本地队列</strong>，平台即可连续下发。
        </template>
      </el-alert>

      <el-form-item label="设备图片">
        <div class="image-upload-wrap">
          <UploadImg v-model="formData.imageUrl" />
          <span class="image-tip">可选，未上传时展示默认图</span>
        </div>
      </el-form-item>

      <el-form-item label="备注">
        <el-input v-model="formData.remark" maxlength="512" placeholder="请输入备注" type="textarea" :rows="2" />
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
  mqttTopicPrefix: undefined,
  callbackEnabled: false,
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

const formRules = reactive({
  deviceCode: [{ required: true, message: '设备编码不能为空', trigger: 'blur' }],
  deviceName: [{ required: true, message: '设备名称不能为空', trigger: 'blur' }],
  deviceTypeCode: [{ required: true, message: '设备类型不能为空', trigger: 'change' }],
  driverType: [{ required: true, message: '驱动类型不能为空', trigger: 'change' }],
  status: [{ required: true, message: '在线状态不能为空', trigger: 'change' }],
  connectionType: [{ required: true, message: '接入方式不能为空', trigger: 'change' }]
})
const formRef = ref()

const deviceTypeOptions = [
  { label: '酶标仪/读板机', value: 'PLATE_READER' },
  { label: '液体处理工作站', value: 'LIQUID_HANDLER' },
  { label: '机械臂', value: 'ROBOTIC_ARM' },
  { label: '培养箱', value: 'INCUBATOR' },
  { label: '离心机', value: 'CENTRIFUGE' },
  { label: '其他', value: 'OTHER' }
]

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
  gap: 12px;
}
.image-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 32px;
}
.form-tip {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
