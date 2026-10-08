<template>
  <ContentWrap title="内容物定义">
    <!-- 搜索 -->
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
      <el-form-item label="编码" prop="contentCode">
        <el-input v-model="queryParams.contentCode" placeholder="请输入内容物编码" clearable @keyup.enter="handleQuery" class="!w-220px" />
      </el-form-item>
      <el-form-item label="名称" prop="contentName">
        <el-input v-model="queryParams.contentName" placeholder="请输入内容物名称" clearable @keyup.enter="handleQuery" class="!w-220px" />
      </el-form-item>
      <el-form-item label="类型" prop="contentType">
        <el-select v-model="queryParams.contentType" placeholder="全部" clearable class="!w-180px">
          <el-option v-for="item in contentTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['wms:content-def:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新增
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="图片" align="center" width="80">
        <template #default="scope">
          <el-image
            :src="scope.row.imageUrl || defaultContentImage"
            class="w-40px h-40px"
            fit="cover"
            :preview-src-list="[scope.row.imageUrl || defaultContentImage]"
            preview-teleported
          />
        </template>
      </el-table-column>
      <el-table-column label="内容物编码" align="center" prop="contentCode" :show-overflow-tooltip="true" />
      <el-table-column label="内容物名称" align="center" prop="contentName" :show-overflow-tooltip="true" />
      <el-table-column label="类型" align="center" width="100">
        <template #default="scope">{{ contentTypeLabel(scope.row.contentType) }}</template>
      </el-table-column>
      <el-table-column label="浓度" align="center" prop="concentration" width="120" :show-overflow-tooltip="true" />
      <el-table-column label="存储条件" align="center" width="110">
        <template #default="scope">{{ storageCondLabel(scope.row.storageCond) }}</template>
      </el-table-column>
      <el-table-column label="危险等级" align="center" width="100">
        <template #default="scope">
          <el-tag v-if="scope.row.hazardLevel && scope.row.hazardLevel !== 'NONE'" :type="hazardTagType(scope.row.hazardLevel)" size="small">
            {{ hazardLevelLabel(scope.row.hazardLevel) }}
          </el-tag>
          <span v-else>无</span>
        </template>
      </el-table-column>
      <el-table-column label="供应商" align="center" prop="supplier" :show-overflow-tooltip="true" />
      <el-table-column label="状态" align="center" width="80">
        <template #default="scope">
          <el-tag :type="scope.row.status === 0 ? 'success' : 'info'">{{ scope.row.status === 0 ? '开启' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="160" fixed="right">
        <template #default="scope">
          <el-button v-hasPermi="['wms:content-def:update']" link type="primary" @click="openForm('update', scope.row.id)">
            编辑
          </el-button>
          <el-button v-hasPermi="['wms:content-def:delete']" link type="danger" @click="handleDelete(scope.row.id)">
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
  <ContentDefForm ref="formRef" @success="getList" />
</template>

<script lang="ts" setup>
import { ContentDefApi, ContentDefVO } from '@/api/wms/content'
import ContentDefForm from './ContentDefForm.vue'
import defaultContentImage from '@/assets/imgs/wms/content-default.svg'

defineOptions({ name: 'WmsContentDef' })

const message = useMessage()

const loading = ref(false)
const list = ref<ContentDefVO[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  contentCode: undefined,
  contentName: undefined,
  contentType: undefined
})
const queryFormRef = ref()

const contentTypeOptions = [
  { label: '试剂', value: 'REAGENT' },
  { label: '标准品', value: 'STANDARD' },
  { label: '缓冲液', value: 'BUFFER' },
  { label: '样本', value: 'SAMPLE' },
  { label: '废液', value: 'WASTE' },
  { label: '培养基', value: 'MEDIA' },
  { label: '溶剂', value: 'SOLVENT' },
  { label: '其他', value: 'OTHER' }
]

const contentTypeLabel = (v?: string) => contentTypeOptions.find((i) => i.value === v)?.label || v || '—'
const storageCondLabel = (v?: string) => {
  const map: Record<string, string> = {
    RT: '常温',
    C2_8: '2~8℃',
    F20: '-20℃',
    Ultra80: '-80℃',
    FROZTHAW: '冻融'
  }
  return map[v || ''] || v || '—'
}
const hazardLevelLabel = (v?: string) => {
  const map: Record<string, string> = {
    NONE: '无',
    LOW: '低危',
    MEDIUM: '中危',
    HIGH: '高危',
    FLAMMABLE: '易燃',
    TOXIC: '有毒'
  }
  return map[v || ''] || v || '—'
}
const hazardTagType = (v: string): 'success' | 'warning' | 'danger' | 'info' => {
  if (v === 'HIGH' || v === 'TOXIC' || v === 'FLAMMABLE') return 'danger'
  if (v === 'MEDIUM') return 'warning'
  return 'info'
}

const getList = async () => {
  loading.value = true
  try {
    const data = await ContentDefApi.getContentDefPage(queryParams)
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

const handleDelete = async (id: number) => {
  try {
    await message.delConfirm()
  } catch {
    return
  }
  await ContentDefApi.deleteContentDef(id)
  message.success('删除成功')
  getList()
}

onMounted(() => {
  getList()
})
</script>
