<template>
  <ContentWrap title="解析规则">
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
      <el-form-item label="规则编码" prop="codecCode">
        <el-input v-model="queryParams.codecCode" placeholder="请输入编码" clearable class="!w-200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="名称" prop="codecName">
        <el-input v-model="queryParams.codecName" placeholder="请输入名称" clearable class="!w-200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="类型" prop="parseType">
        <el-select v-model="queryParams.parseType" placeholder="全部" clearable class="!w-140px">
          <el-option label="JSON" value="JSON" />
          <el-option label="正则" value="REGEX" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['device:codec:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新增
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="规则编码" align="center" prop="codecCode" :show-overflow-tooltip="true" />
      <el-table-column label="名称" align="center" prop="codecName" :show-overflow-tooltip="true" />
      <el-table-column label="类型" align="center" width="100" prop="parseType" />
      <el-table-column label="状态" align="center" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status === 0 ? 'success' : 'info'">{{ scope.row.status === 0 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
      <el-table-column label="操作" align="center" width="180" fixed="right">
        <template #default="scope">
          <el-button v-hasPermi="['device:codec:update']" link type="primary" @click="openForm('update', scope.row.id)">编辑</el-button>
          <el-button v-hasPermi="['device:codec:delete']" link type="danger" @click="handleDelete(scope.row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </ContentWrap>

  <CodecForm ref="formRef" @success="getList" />
</template>

<script lang="ts" setup>
import { DeviceCodecApi, DeviceCodecVO } from '@/api/device/codec'
import CodecForm from './CodecForm.vue'

defineOptions({ name: 'DeviceCodec' })

const message = useMessage()
const loading = ref(false)
const list = ref<DeviceCodecVO[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  codecCode: undefined,
  codecName: undefined,
  parseType: undefined
})
const queryFormRef = ref()
const formRef = ref()

const getList = async () => {
  loading.value = true
  try {
    const data = await DeviceCodecApi.getCodecPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value.resetFields()
  handleQuery()
}

const openForm = (type: string, id?: number) => {
  formRef.value.open(type, id)
}

const handleDelete = async (id: number) => {
  await message.delConfirm()
  await DeviceCodecApi.deleteCodec(id)
  message.success('删除成功')
  await getList()
}

onMounted(() => getList())
</script>
