<template>
  <ContentWrap title="样本管理">
    <!-- 搜索 -->
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
      <el-form-item label="样本编号" prop="sampleId">
        <el-input v-model="queryParams.sampleId" placeholder="请输入样本编号" clearable @keyup.enter="handleQuery" class="!w-220px" />
      </el-form-item>
      <el-form-item label="样本名称" prop="sampleName">
        <el-input v-model="queryParams.sampleName" placeholder="请输入样本名称" clearable @keyup.enter="handleQuery" class="!w-220px" />
      </el-form-item>
      <el-form-item label="类型" prop="sampleType">
        <el-select v-model="queryParams.sampleType" placeholder="全部" clearable class="!w-160px">
          <el-option v-for="item in sampleTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable class="!w-160px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['wms:sample:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 登记样本
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="样本编号" align="center" prop="sampleId" min-width="150" :show-overflow-tooltip="true" />
      <el-table-column label="样本名称" align="center" prop="sampleName" min-width="130" :show-overflow-tooltip="true" />
      <el-table-column label="类型" align="center" width="90">
        <template #default="scope">{{ sampleTypeLabel(scope.row.sampleType) }}</template>
      </el-table-column>
      <el-table-column label="谱系" align="center" width="110">
        <template #default="scope">{{ `${scope.row.generation ?? 0} 代` }}<span class="color-#909399 text-11px"> · {{ deriveTypeLabel(scope.row.deriveType) }}</span></template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="100">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row.status)" size="small">{{ statusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="初始体积" align="center" width="100">
        <template #default="scope">{{ scope.row.initialVolUl != null ? scope.row.initialVolUl + ' μL' : '—' }}</template>
      </el-table-column>
      <el-table-column label="批次号" align="center" prop="batchNo" width="120" :show-overflow-tooltip="true" />
      <el-table-column label="来源" align="center" width="90">
        <template #default="scope">{{ sourceLabel(scope.row.source) }}</template>
      </el-table-column>
      <el-table-column label="当前流程" align="center" prop="currentExecutionId" min-width="140" :show-overflow-tooltip="true" />
      <el-table-column label="创建时间" align="center" width="170" prop="createTime" :formatter="dateFormatter" />
      <el-table-column label="操作" align="center" width="340" fixed="right">
        <template #default="scope">
          <el-button v-hasPermi="['wms:sample:query']" link type="primary" @click="openDetail(scope.row.id)">
            详情
          </el-button>
          <el-button v-hasPermi="['wms:material-instance:create']" link type="primary" @click="openGenerateInstance(scope.row)">
            生成实例
          </el-button>
          <el-button v-hasPermi="['wms:sample:create']" link type="primary" @click="openSplit(scope.row)">
            拆分
          </el-button>
          <el-button v-hasPermi="['wms:sample:update']" link type="primary" @click="openForm('update', scope.row.id)">
            编辑
          </el-button>
          <el-button v-hasPermi="['wms:sample:delete']" link type="danger" @click="handleDelete(scope.row.id)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <!-- 分页 -->
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <!-- 表单 -->
  <SampleForm ref="formRef" @success="getList" />
  <SampleDetailDrawer ref="detailRef" />
  <InstanceForm ref="instanceFormRef" @success="getList" />
</template>

<script lang="ts" setup>
import { SampleApi, SampleVO } from '@/api/wms/sample'
import SampleForm from './SampleForm.vue'
import SampleDetailDrawer from './SampleDetailDrawer.vue'
import InstanceForm from '../instance/InstanceForm.vue'
import { formatDate } from '@/utils/formatTime'
import { sampleTypeLabel, useSampleTypeDictOptions } from './labels'

defineOptions({ name: 'WmsSample' })

const message = useMessage()

const loading = ref(false)
const list = ref<SampleVO[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  sampleId: undefined,
  sampleName: undefined,
  sampleType: undefined,
  status: undefined
})
const queryFormRef = ref()

const sampleTypeOptions = useSampleTypeDictOptions()

const STATUS_META: Record<string, { label: string; type: 'info' | 'primary' | 'warning' | 'success' | 'danger' }> = {
  REGISTERED: { label: '已登记', type: 'info' },
  LOADED: { label: '已装载', type: 'primary' },
  IN_PROCESS: { label: '处理中', type: 'warning' },
  SPLIT: { label: '已拆分', type: 'primary' },
  COMPLETED: { label: '已完成', type: 'success' },
  ARCHIVED: { label: '已归档', type: 'info' },
  REJECTED: { label: '不合格', type: 'danger' },
  LOST: { label: '丢失', type: 'danger' }
}
const statusOptions = Object.entries(STATUS_META).map(([value, meta]) => ({ value, label: meta.label }))
const statusLabel = (v?: string) => STATUS_META[v || '']?.label || v || '—'
const statusTagType = (v?: string) => STATUS_META[v || '']?.type || 'info'

const DERIVE_META: Record<string, string> = {
  ORIGINAL: '原始',
  SPLIT: '拆分',
  MERGE: '合并',
  ALIQUOT: '分装',
  ADD_REAGENT: '加试剂'
}
const deriveTypeLabel = (v?: string) => DERIVE_META[v || ''] || v || '—'

const SOURCE_META: Record<string, string> = {
  MANUAL: '手动',
  IMPORT: '导入',
  LIS: 'LIS',
  DEVICE: '设备'
}
const sourceLabel = (v?: string) => SOURCE_META[v || ''] || v || '—'

const dateFormatter = (_row: any, _col: any, val: any) => (val ? formatDate(val) : '—')

const getList = async () => {
  loading.value = true
  try {
    const data = await SampleApi.getSamplePage(queryParams)
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.pageNo = 1
  getList()
}

const formRef = ref()
const openForm = (type: string, id?: number) => {
  formRef.value.open(type, id)
}

const detailRef = ref()
const openDetail = (id: number) => {
  detailRef.value.open(id)
}

const instanceFormRef = ref()
const openGenerateInstance = (row: SampleVO) => {
  instanceFormRef.value.openFromSample(row)
}

const openSplit = (row: SampleVO) => {
  formRef.value.openForSplit(row)
}

const handleDelete = async (id: number) => {
  try {
    await message.delConfirm()
  } catch {
    return
  }
  await SampleApi.deleteSample(id)
  message.success('删除成功')
  getList()
}

onMounted(() => {
  getList()
})
</script>
