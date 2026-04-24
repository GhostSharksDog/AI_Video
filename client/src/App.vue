<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import request from './axios/request'
import { loginUser, registerUser, type AuthUser } from './axios/auth'

type UserInfo = {
  id?: number
  username: string
  nickname?: string
  avatar?: string
  role?: string
}

type MediaItem = {
  id: number
  userId?: number
  filename: string
  status?: string
  filePath: string
  aiSummary?: string | null
  transcriptText?: string | null
  coverUrl?: string | null
  uploadTime?: string
}

type PendingTaskType = 'transcribe' | 'summary'

const CURRENT_USER_STORAGE_KEY = 'demo-current-user'

const currentUser = ref<UserInfo | null>(loadCurrentUser())
const showAuthModal = ref(false)
const authMode = ref<'login' | 'register'>('login')
const authLoading = ref(false)
const authMessage = ref('')
const authError = ref(false)
const authForm = ref({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: '',
})

const fileInputRef = ref<HTMLInputElement | null>(null)
const isDragOver = ref(false)
const uploading = ref(false)
const loadingList = ref(false)
const actionLoading = ref<'' | 'download' | 'transcribe' | 'summary'>('')
const banner = ref('')
const bannerType = ref<'ok' | 'error'>('ok')

const mediaList = ref<MediaItem[]>([])
const selectedMediaId = ref<number | null>(null)

const showResultPanel = ref(false)
const resultType = ref<'transcript' | 'summary'>('transcript')
const resultContent = ref('')

const pendingTask = ref<{ id: number; type: PendingTaskType } | null>(null)
let pollingTimer: ReturnType<typeof setInterval> | null = null

const selectedMedia = computed(() => {
  if (selectedMediaId.value == null) return null
  return mediaList.value.find((item) => item.id === selectedMediaId.value) ?? null
})

function loadCurrentUser(): UserInfo | null {
  const raw = localStorage.getItem(CURRENT_USER_STORAGE_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as AuthUser
  } catch {
    return null
  }
}

function showBanner(message: string, type: 'ok' | 'error' = 'ok') {
  banner.value = message
  bannerType.value = type
  setTimeout(() => {
    if (banner.value === message) {
      banner.value = ''
    }
  }, 2800)
}

function openAuthModal(mode: 'login' | 'register' = 'login') {
  authMode.value = mode
  showAuthModal.value = true
  authMessage.value = ''
  authError.value = false
  authForm.value = {
    username: '',
    password: '',
    confirmPassword: '',
    nickname: '',
  }
}

function closeAuthModal() {
  showAuthModal.value = false
}

function switchAuthMode() {
  authMode.value = authMode.value === 'login' ? 'register' : 'login'
  authMessage.value = ''
  authError.value = false
  authForm.value.password = ''
  authForm.value.confirmPassword = ''
}

async function handleAuth() {
  if (!authForm.value.username.trim() || !authForm.value.password) {
    authMessage.value = '请输入完整的用户名和密码'
    authError.value = true
    return
  }

  authLoading.value = true
  authMessage.value = ''
  authError.value = false

  try {
    if (authMode.value === 'login') {
      const resp = await loginUser({
        username: authForm.value.username.trim(),
        password: authForm.value.password,
      })

      if (resp.code !== 200 || !resp.userInfo) {
        authMessage.value = resp.msg || '用户名或密码错误'
        authError.value = true
        return
      }

      currentUser.value = resp.userInfo
      localStorage.setItem(CURRENT_USER_STORAGE_KEY, JSON.stringify(resp.userInfo))
      closeAuthModal()
      await fetchMediaList(true)
      showBanner(`欢迎，${resp.userInfo.nickname || resp.userInfo.username}`)
      return
    }

    if (!authForm.value.confirmPassword) {
      authMessage.value = '请输入确认密码'
      authError.value = true
      return
    }

    if (authForm.value.password !== authForm.value.confirmPassword) {
      authMessage.value = '两次输入的密码不一致'
      authError.value = true
      return
    }

    if (authForm.value.password.length < 6) {
      authMessage.value = '密码至少需要 6 位'
      authError.value = true
      return
    }

    const username = authForm.value.username.trim()
    const resp = await registerUser({
      username,
      password: authForm.value.password,
      nickname: authForm.value.nickname.trim() || username,
    })

    if (resp.code !== 200) {
      authMessage.value = resp.msg || '注册失败'
      authError.value = true
      return
    }

    authMode.value = 'login'
    authForm.value.username = username
    authForm.value.password = ''
    authForm.value.confirmPassword = ''
    authMessage.value = '注册成功，请输入密码登录'
    authError.value = false
  } catch (error: any) {
    authMessage.value = error?.response?.data?.msg || error?.message || '请求失败'
    authError.value = true
  } finally {
    authLoading.value = false
  }
}

function logout() {
  currentUser.value = null
  localStorage.removeItem(CURRENT_USER_STORAGE_KEY)
  mediaList.value = []
  selectedMediaId.value = null
  showResultPanel.value = false
  stopPolling()
  showBanner('已退出登录')
}

async function fetchMediaList(selectFirst = false) {
  if (!currentUser.value?.id) {
    mediaList.value = []
    selectedMediaId.value = null
    return
  }

  loadingList.value = true
  try {
    const data = await request.get<any, MediaItem[]>('/media/list', {
      params: {
        userId: currentUser.value.id,
        _t: Date.now(),
      },
    })

    mediaList.value = Array.isArray(data) ? data : []

    if (selectFirst && mediaList.value.length > 0) {
      selectedMediaId.value = mediaList.value[0]?.id ?? null
    } else if (
      selectedMediaId.value != null &&
      !mediaList.value.some((item) => item.id === selectedMediaId.value)
    ) {
      selectedMediaId.value = mediaList.value[0]?.id ?? null
    }
  } catch (error: any) {
    showBanner(error?.message || '获取视频列表失败', 'error')
  } finally {
    loadingList.value = false
  }
}

function openFilePicker() {
  if (!currentUser.value) {
    openAuthModal('login')
    showBanner('请先登录后上传', 'error')
    return
  }
  fileInputRef.value?.click()
}

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  await uploadFile(file)
  input.value = ''
}

async function uploadFile(file: File) {
  if (!currentUser.value?.id) {
    openAuthModal('login')
    showBanner('请先登录后上传', 'error')
    return
  }

  if (!file.type.startsWith('video/')) {
    showBanner('仅支持上传视频文件', 'error')
    return
  }

  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('userId', String(currentUser.value.id))

    await request.post('/media/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })

    await fetchMediaList(true)
    showBanner('上传成功')
  } catch (error: any) {
    showBanner(error?.response?.data || error?.message || '上传失败', 'error')
  } finally {
    uploading.value = false
  }
}

function handleDrop(event: DragEvent) {
  event.preventDefault()
  isDragOver.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file) {
    uploadFile(file)
  }
}

function selectMedia(item: MediaItem) {
  selectedMediaId.value = item.id
}

function formatTime(time?: string) {
  if (!time) return '--'
  const d = new Date(time)
  if (Number.isNaN(d.getTime())) return '--'
  const yy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  return `${yy}-${mm}-${dd} ${hh}:${mi}`
}

async function downloadAudio() {
  if (!selectedMedia.value) return
  actionLoading.value = 'download'

  try {
    const blob = await request.get<any, Blob>('/media/download', {
      params: { id: selectedMedia.value.id },
      responseType: 'blob',
    })

    const fileName = `${selectedMedia.value.filename.replace(/\.[^.]+$/, '') || 'audio'}.mp3`
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = fileName
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
    showBanner('音频下载已开始')
  } catch (error: any) {
    showBanner(error?.message || '下载失败', 'error')
  } finally {
    actionLoading.value = ''
  }
}

async function runTranscribe() {
  if (!selectedMedia.value) return

  if (selectedMedia.value.transcriptText) {
    openResult('transcript')
    return
  }

  actionLoading.value = 'transcribe'
  try {
    const resp = await request.get<any, string>('/mission/transcribe', {
      params: { id: selectedMedia.value.id },
    })
    showBanner(resp || '已提交文字提取任务')
    startPolling(selectedMedia.value.id, 'transcribe')
  } catch (error: any) {
    showBanner(error?.message || '提取文字失败', 'error')
  } finally {
    actionLoading.value = ''
  }
}

async function runSummary() {
  if (!selectedMedia.value) return

  const summary = selectedMedia.value.aiSummary?.trim()
  if (summary && !summary.includes('等待调度') && !summary.includes('正在')) {
    openResult('summary')
    return
  }

  actionLoading.value = 'summary'
  try {
    const resp = await request.get<any, string>('/mission/ai', {
      params: { id: selectedMedia.value.id },
    })
    showBanner(resp || '已提交全文总结任务')
    startPolling(selectedMedia.value.id, 'summary')
  } catch (error: any) {
    showBanner(error?.message || '全文总结失败', 'error')
  } finally {
    actionLoading.value = ''
  }
}

function openResult(type: 'transcript' | 'summary') {
  const item = selectedMedia.value
  if (!item) return

  resultType.value = type
  resultContent.value =
    type === 'transcript'
      ? item.transcriptText?.trim() || '暂无提取文字'
      : item.aiSummary?.trim() || '暂无总结结果'
  showResultPanel.value = true
}

function hideResult() {
  showResultPanel.value = false
}

function stopPolling() {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
  pendingTask.value = null
}

function startPolling(id: number, type: PendingTaskType) {
  stopPolling()
  pendingTask.value = { id, type }

  pollingTimer = setInterval(async () => {
    await fetchMediaList(false)

    const target = mediaList.value.find((item) => item.id === id)
    if (!target) return

    if (type === 'transcribe') {
      const text = target.transcriptText?.trim()
      if (text) {
        stopPolling()
        selectedMediaId.value = id
        openResult('transcript')
        showBanner('文字提取完成')
      }
      return
    }

    const summary = target.aiSummary?.trim()
    if (!summary) return

    const done = !summary.includes('等待调度') && !summary.includes('正在')
    if (done) {
      stopPolling()
      selectedMediaId.value = id
      openResult('summary')
      showBanner(summary.includes('失败') ? '总结任务结束（包含错误）' : '全文总结完成')
    }
  }, 3000)
}

onMounted(() => {
  fetchMediaList(true)
})

onBeforeUnmount(() => {
  stopPolling()
})
</script>

<template>
  <div class="page">
    <header class="topbar">
      <div class="brand">AIVideo</div>
      <div class="user-area">
        <button v-if="!currentUser" class="ghost-btn" @click="openAuthModal('login')">登录 / 注册</button>
        <template v-else>
          <span class="nickname">{{ currentUser.nickname || currentUser.username }}</span>
          <button class="ghost-btn" @click="logout">退出</button>
        </template>
      </div>
    </header>

    <main class="main">
      <section
        class="upload-zone"
        :class="{ dragging: isDragOver, busy: uploading }"
        @click="openFilePicker"
        @dragover.prevent="isDragOver = true"
        @dragleave.prevent="isDragOver = false"
        @drop="handleDrop"
      >
        <input ref="fileInputRef" type="file" accept="video/*" hidden @change="onFileChange" />
        <h2>{{ uploading ? '上传中...' : '拖拽视频到此处，或点击选择文件' }}</h2>
        <p>{{ uploading ? '请稍候，正在写入服务器' : '支持常见视频格式，如 mp4 / mov / mkv 等' }}</p>
      </section>

      <section class="list-block">
        <div class="section-head">
          <h3>已上传视频</h3>
          <button class="ghost-btn" @click="fetchMediaList(false)">{{ loadingList ? '刷新中...' : '刷新列表' }}</button>
        </div>

        <div v-if="mediaList.length === 0" class="empty">暂无视频，先上传一个试试。</div>

        <div v-else class="video-grid">
          <button
            v-for="item in mediaList"
            :key="item.id"
            class="video-card"
            :class="{ active: selectedMediaId === item.id }"
            @click="selectMedia(item)"
          >
            <div class="video-name">{{ item.filename }}</div>
            <div class="video-meta">状态：{{ item.status || 'UNKNOWN' }}</div>
            <div class="video-meta">上传：{{ formatTime(item.uploadTime) }}</div>
          </button>
        </div>
      </section>

      <section v-if="selectedMedia" class="action-block">
        <div class="section-head">
          <h3>当前选择：{{ selectedMedia.filename }}</h3>
        </div>

        <div class="action-row">
          <button class="action-btn secondary" :disabled="actionLoading !== ''" @click="downloadAudio">
            {{ actionLoading === 'download' ? '下载中...' : '下载音频' }}
          </button>
          <button class="action-btn" :disabled="actionLoading !== ''" @click="runTranscribe">
            {{ actionLoading === 'transcribe' ? '提取中...' : '提取文字' }}
          </button>
          <button class="action-btn primary" :disabled="actionLoading !== ''" @click="runSummary">
            {{ actionLoading === 'summary' ? '总结中...' : '全文总结' }}
          </button>
        </div>

        <div class="view-row">
          <button class="tiny-btn" @click="openResult('transcript')">显示文字结果</button>
          <button class="tiny-btn" @click="openResult('summary')">显示总结结果</button>
          <button class="tiny-btn" @click="hideResult">隐藏展示</button>
        </div>

        <div v-if="pendingTask" class="task-tip">
          任务进行中：{{ pendingTask.type === 'transcribe' ? '文字提取' : '全文总结' }}（每 3 秒自动刷新）
        </div>
      </section>

      <section v-if="showResultPanel" class="result-block">
        <div class="section-head">
          <h3>{{ resultType === 'transcript' ? '文字提取结果' : '全文总结结果' }}</h3>
        </div>
        <pre class="result-content">{{ resultContent }}</pre>
      </section>
    </main>

    <transition name="fade">
      <div v-if="banner" class="banner" :class="bannerType === 'error' ? 'error' : 'ok'">{{ banner }}</div>
    </transition>

    <div v-if="showAuthModal" class="auth-backdrop" @click.self="closeAuthModal">
      <div class="auth-panel">
        <div class="auth-head">
          <h3>{{ authMode === 'login' ? '用户登录' : '用户注册' }}</h3>
          <button class="close-btn" @click="closeAuthModal">×</button>
        </div>
        <div class="auth-body">
          <input v-model.trim="authForm.username" type="text" placeholder="用户名" autocomplete="username" />
          <input v-model="authForm.password" type="password" placeholder="密码" autocomplete="current-password" />
          <input
            v-if="authMode === 'register'"
            v-model="authForm.confirmPassword"
            type="password"
            placeholder="确认密码"
            autocomplete="new-password"
          />
          <input v-if="authMode === 'register'" v-model.trim="authForm.nickname" type="text" placeholder="昵称（可选）" />

          <button class="auth-submit" :disabled="authLoading" @click="handleAuth">
            {{ authLoading ? '处理中...' : authMode === 'login' ? '登录' : '注册' }}
          </button>

          <button class="auth-switch" @click="switchAuthMode">
            {{ authMode === 'login' ? '没有账号？去注册' : '已有账号？去登录' }}
          </button>

          <p v-if="authMessage" class="auth-msg" :class="{ error: authError }">{{ authMessage }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: #f4f6fb;
  color: #17202a;
}

.topbar {
  height: 68px;
  background: #111827;
  color: #fff;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.brand {
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.nickname {
  font-size: 14px;
  opacity: 0.95;
}

.main {
  max-width: 1100px;
  margin: 0 auto;
  padding: 28px 18px 40px;
  display: grid;
  gap: 20px;
}

.upload-zone {
  min-height: 290px;
  border: 2px dashed #8ea0b8;
  border-radius: 18px;
  background: linear-gradient(145deg, #ffffff 0%, #eef4ff 100%);
  display: grid;
  place-items: center;
  text-align: center;
  cursor: pointer;
  transition: all 0.25s ease;
  padding: 20px;
}

.upload-zone h2 {
  margin: 0;
  font-size: 32px;
  color: #1c3d68;
}

.upload-zone p {
  margin-top: 12px;
  color: #50627a;
}

.upload-zone.dragging {
  border-color: #1e90ff;
  transform: translateY(-2px);
  box-shadow: 0 14px 30px rgba(30, 144, 255, 0.2);
}

.upload-zone.busy {
  pointer-events: none;
  opacity: 0.75;
}

.list-block,
.action-block,
.result-block {
  background: #fff;
  border-radius: 14px;
  border: 1px solid #d9e2ef;
  padding: 18px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-head h3 {
  margin: 0;
  font-size: 18px;
}

.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 12px;
}

.video-card {
  border: 1px solid #d2deed;
  border-radius: 10px;
  background: #fafcff;
  text-align: left;
  padding: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.video-card:hover {
  border-color: #5d9df0;
  transform: translateY(-1px);
}

.video-card.active {
  border-color: #1e66d0;
  background: #eef5ff;
  box-shadow: inset 0 0 0 1px #1e66d0;
}

.video-name {
  font-size: 15px;
  font-weight: 600;
  color: #12263f;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.video-meta {
  font-size: 13px;
  color: #5f738f;
  margin-top: 2px;
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.action-btn {
  min-width: 116px;
  height: 40px;
  border: none;
  border-radius: 8px;
  background: #2f76d2;
  color: #fff;
  cursor: pointer;
  font-size: 14px;
}

.action-btn.primary {
  background: #0f9d7a;
}

.action-btn.secondary {
  background: #2f3d4f;
}

.action-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.view-row {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.tiny-btn {
  height: 34px;
  border: 1px solid #c5d5ea;
  border-radius: 7px;
  background: #fff;
  color: #2d4f77;
  padding: 0 12px;
  cursor: pointer;
}

.task-tip {
  margin-top: 12px;
  font-size: 13px;
  color: #267167;
}

.result-content {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.65;
  max-height: 320px;
  overflow-y: auto;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 8px;
  padding: 12px;
}

.empty {
  color: #687d99;
  padding: 16px 4px;
}

.ghost-btn {
  height: 34px;
  border-radius: 7px;
  border: 1px solid rgba(255, 255, 255, 0.35);
  background: transparent;
  color: inherit;
  padding: 0 12px;
  cursor: pointer;
}

.auth-backdrop {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: grid;
  place-items: center;
  background: rgba(0, 0, 0, 0.5);
  padding: 16px;
}

.auth-panel {
  width: min(92vw, 420px);
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
}

.auth-head {
  height: 56px;
  background: #1f2937;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
}

.auth-head h3 {
  margin: 0;
  font-size: 18px;
}

.close-btn {
  border: none;
  background: transparent;
  color: #fff;
  font-size: 24px;
  cursor: pointer;
}

.auth-body {
  display: grid;
  gap: 10px;
  padding: 16px;
}

.auth-body input {
  height: 40px;
  border-radius: 8px;
  border: 1px solid #d3deec;
  padding: 0 10px;
  outline: none;
}

.auth-submit {
  height: 40px;
  border-radius: 8px;
  border: none;
  background: #1f7ae0;
  color: #fff;
  cursor: pointer;
}

.auth-submit:disabled {
  opacity: 0.65;
}

.auth-switch {
  border: none;
  background: transparent;
  color: #3268aa;
  text-decoration: underline;
  cursor: pointer;
}

.auth-msg {
  margin: 0;
  font-size: 13px;
  color: #1f7ae0;
}

.auth-msg.error {
  color: #dc2626;
}

.banner {
  position: fixed;
  left: 50%;
  bottom: 22px;
  transform: translateX(-50%);
  padding: 10px 14px;
  border-radius: 8px;
  color: #fff;
  z-index: 50;
  font-size: 14px;
}

.banner.ok {
  background: #15803d;
}

.banner.error {
  background: #b91c1c;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.18s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 720px) {
  .upload-zone {
    min-height: 220px;
  }

  .upload-zone h2 {
    font-size: 24px;
  }
}
</style>
