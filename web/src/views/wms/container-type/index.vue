<template>
  <ContentWrap title="容器类型">
    <!-- 搜索 -->
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
      <el-form-item label="编码" prop="typeCode">
        <el-input v-model="queryParams.typeCode" placeholder="请输入容器编码" clearable @keyup.enter="handleQuery" class="!w-220px" />
      </el-form-item>
      <el-form-item label="名称" prop="typeName">
        <el-input v-model="queryParams.typeName" placeholder="请输入容器名称" clearable @keyup.enter="handleQuery" class="!w-220px" />
      </el-form-item>
      <el-form-item label="物理形态" prop="category">
        <el-select v-model="queryParams.category" placeholder="全部" clearable class="!w-180px">
          <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['wms:container-type:create']">
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
            :src="scope.row.imageUrl || defaultContainerImage"
            class="w-40px h-40px"
            fit="cover"
            :preview-src-list="[scope.row.imageUrl || defaultContainerImage]"
            preview-teleported
          />
        </template>
      </el-table-column>
      <el-table-column label="容器编码" align="center" prop="typeCode" :show-overflow-tooltip="true" />
      <el-table-column label="容器名称" align="center" prop="typeName" :show-overflow-tooltip="true" />
      <el-table-column label="物理形态" align="center" width="100">
        <template #default="scope">{{ categoryLabel(scope.row.category) }}</template>
      </el-table-column>
      <el-table-column label="层级角色" align="center" width="100">
        <template #default="scope">{{ hierarchyRoleLabel(scope.row.hierarchyRole) }}</template>
      </el-table-column>
      <el-table-column label="容量/规格" align="center" width="140">
        <template #default="scope">
          <span v-if="scope.row.maxVolUl != null">{{ scope.row.maxVolUl }} μL</span>
          <span v-else-if="scope.row.wellCount != null">{{ scope.row.wellCount }} 位（{{ scope.row.wellRows }}×{{ scope.row.wellCols }}）</span>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="使用类型" align="center" width="100">
        <template #default="scope">{{ usageTypeLabel(scope.row.usageType) }}</template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="80">
        <template #default="scope">
          <el-tag :type="scope.row.status === 0 ? 'success' : 'info'">{{ scope.row.status === 0 ? '开启' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="description" :show-overflow-tooltip="true" />
      <el-table-column label="操作" align="center" width="160" fixed="right">
        <template #default="scope">
          <el-button v-hasPermi="['wms:container-type:update']" link type="primary" @click="openForm('update', scope.row.id)">
            编辑
          </el-button>
          <el-button v-hasPermi="['wms:container-type:delete']" link type="danger" @click="handleDelete(scope.row.id)">
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
  <ContainerTypeForm ref="formRef" @success="getList" />
</template>

<script lang="ts" setup>
import { ContainerTypeApi, ContainerTypeVO } from '@/api/wms/container'
import ContainerTypeForm from './ContainerTypeForm.vue'
import defaultContainerImage from '@/assets/imgs/wms/container-default.svg'

defineOptions({ name: 'WmsContainerType' })

const message = useMessage()

const loading = ref(false)
const list = ref<ContainerTypeVO[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  typeCode: undefined,
  typeName: undefined,
  category: undefined
})
const queryFormRef = ref()

const categoryOptions = [
  { label: '孔板', value: 'PLATE' },
  { label: '试管/离心管', value: 'TUBE' },
  { label: '瓶', value: 'BOTTLE' },
  { label: '托盘架', value: 'RACK' },
  { label: '小瓶', value: 'VIAL' },
  { label: '吸头', value: 'TIP' },
  { label: '芯片', value: 'CHIP' },
  { label: '滤膜', value: 'FILTER' },
  { label: '盒', value: 'BOX' },
  { label: '袋', value: 'BAG' },
  { label: '其他', value: 'OTHER' }
]

const categoryLabel = (v?: string) => categoryOptions.find((i) => i.value === v)?.label || v || '—'
const hierarchyRoleLabel = (v?: string) => {
  const map: Record<string, string> = { CARRIER: '载体', CONTAINER: '容器', WELL: '孔位' }
  return map[v || ''] || v || '—'
}
const usageTypeLabel = (v?: string) => {
  const map: Record<string, string> = { REUSABLE: '周转复用', DISPOSABLE: '一次性' }
  return map[v || ''] || v || '—'
}

const getList = async () => {
  loading.value = true
  try {
    const data = await ContainerTypeApi.getContainerTypePage(queryParams)
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
  await ContainerTypeApi.deleteContainerType(id)
  message.success('删除成功')
  getList()
}

onMounted(() => {
  getList()
})
</script>
