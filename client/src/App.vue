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
const urlUploading = ref(false)
const videoUrl = ref('')
const loadingList = ref(false)
const deletingId = ref<number | null>(null)
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
let pollingCount = 0

const isAnyUploading = computed(() => uploading.value || urlUploading.value)
const currentDisplayName = computed(() => currentUser.value?.nickname || currentUser.value?.username || '游客')
const totalMediaCount = computed(() => mediaList.value.length)
const completedMediaCount = computed(
  () => mediaList.value.filter((item) => (item.status || '').toUpperCase() === 'COMPLETED').length,
)
const selectedStatusText = computed(() => selectedMedia.value?.status || '未选择')

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

async function uploadByUrl() {
  if (!currentUser.value?.id) {
    openAuthModal('login')
    showBanner('请先登录后上传', 'error')
    return
  }

  const url = videoUrl.value.trim()
  if (!url) {
    showBanner('请先输入视频链接', 'error')
    return
  }
  if (!/^https?:\/\//i.test(url)) {
    showBanner('请输入合法的 http/https 链接', 'error')
    return
  }

  urlUploading.value = true
  try {
    const formData = new FormData()
    formData.append('url', url)
    formData.append('userId', String(currentUser.value.id))
    await request.post('/media/upload-url', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })
    videoUrl.value = ''
    await fetchMediaList(true)
    showBanner('链接上传成功')
  } catch (error: any) {
    showBanner(error?.response?.data || error?.message || '链接上传失败', 'error')
  } finally {
    urlUploading.value = false
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

async function deleteMedia(item: MediaItem) {
  if (!currentUser.value?.id) {
    openAuthModal('login')
    showBanner('请先登录后操作', 'error')
    return
  }

  const ok = window.confirm(`确认删除 "${item.filename}" 吗？`)
  if (!ok) return

  deletingId.value = item.id
  try {
    await request.delete('/media/delete', {
      params: {
        id: item.id,
        userId: currentUser.value.id,
      },
    })

    if (pendingTask.value?.id === item.id) {
      stopPolling()
    }

    if (selectedMediaId.value === item.id) {
      showResultPanel.value = false
      selectedMediaId.value = null
    }

    await fetchMediaList(false)
    showBanner('删除成功')
  } catch (error: any) {
    const message =
      error?.response?.data ||
      error?.message ||
      '删除失败'
    showBanner(String(message), 'error')
  } finally {
    deletingId.value = null
  }
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
  if (summary && !isSummaryPendingText(summary)) {
    openResult('summary')
    return
  }

  actionLoading.value = 'summary'
  try {
    const resp = await request.get<any, string>('/mission/ai', {
      params: { id: selectedMedia.value.id },
    })
    const message = resp || '已提交全文总结任务'
    const isHardError = message.includes('失败') || message.includes('文件不存在') || message.includes('系统繁忙')
    if (isHardError) {
      showBanner(message, 'error')
      return
    }
    showBanner(message)
    startPolling(selectedMedia.value.id, 'summary')
  } catch (error: any) {
    showBanner(error?.message || '全文总结失败', 'error')
  } finally {
    actionLoading.value = ''
  }
}

function isSummaryPendingText(raw?: string | null) {
  const text = raw?.trim()
  if (!text) return true
  return (
    text.startsWith('[MQ]') ||
    text.includes('等待调度') ||
    text.includes('任务提交中') ||
    text.includes('任务已在后台运行')
  )
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
  pollingCount = 0
}

function startPolling(id: number, type: PendingTaskType) {
  stopPolling()
  pendingTask.value = { id, type }
  pollingCount = 0

  pollingTimer = setInterval(async () => {
    pollingCount += 1
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
    if (!summary) {
      if (pollingCount >= 120) {
        stopPolling()
        showBanner('总结任务超时，请稍后手动刷新', 'error')
      }
      return
    }

    const done = !isSummaryPendingText(summary)
    if (done) {
      stopPolling()
      selectedMediaId.value = id
      openResult('summary')
      showBanner(summary.includes('失败') ? '总结任务结束（包含错误）' : '全文总结完成')
      return
    }

    if (pollingCount >= 120) {
      stopPolling()
      showBanner('总结任务超时，请稍后手动刷新', 'error')
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
    <div class="ambient ambient-one"></div>
    <div class="ambient ambient-two"></div>

    <header class="topbar">
      <div class="topbar-shell">
        <div class="brand-block">
          <div class="brand-mark">AI</div>
          <div class="brand-copy">
            <div class="brand">AIVideo</div>
            <div class="brand-sub">Cinematic analysis console for upload, transcript and long-form summary</div>
          </div>
        </div>

        <div class="topbar-actions">
          <div class="status-chip" :class="{ live: pendingTask || isAnyUploading }">
            <span class="status-dot"></span>
            <span>{{ pendingTask ? '任务处理中' : isAnyUploading ? '上传进行中' : '系统就绪' }}</span>
          </div>

          <div class="user-panel">
            <div class="user-badge">{{ currentDisplayName.slice(0, 1).toUpperCase() }}</div>
            <div class="user-copy">
              <span class="user-label">{{ currentDisplayName }}</span>
              <span class="user-sub">{{ currentUser ? '已登录工作台' : '登录后保存个人视频记录' }}</span>
            </div>
            <button v-if="!currentUser" class="ghost-btn" @click="openAuthModal('login')">登录 / 注册</button>
            <button v-else class="ghost-btn" @click="logout">退出</button>
          </div>
        </div>
      </div>
    </header>

    <main class="main">
      <section class="hero-panel">
        <div class="hero-copy">
          <span class="hero-kicker">Video Intelligence Workbench</span>
          <h1 class="hero-title">一个集上传、转写、总结和下载一体的控制台。</h1>
        </div>

        <div class="hero-stats">
          <div class="stat-card">
            <span class="stat-label">全部视频</span>
            <strong class="stat-value">{{ totalMediaCount }}</strong>
          </div>
          <div class="stat-card">
            <span class="stat-label">已完成</span>
            <strong class="stat-value">{{ completedMediaCount }}</strong>
          </div>
          <div class="stat-card">
            <span class="stat-label">当前状态</span>
            <strong class="stat-value compact">{{ selectedStatusText }}</strong>
          </div>
        </div>
      </section>

      <section class="upload-board">
        <div
          class="upload-zone"
          :class="{ dragging: isDragOver, busy: isAnyUploading }"
          @click="openFilePicker"
          @dragover.prevent="isDragOver = true"
          @dragleave.prevent="isDragOver = false"
          @drop="handleDrop"
        >
          <input ref="fileInputRef" type="file" accept="video/*" hidden @change="onFileChange" />
          <div class="upload-icon">
            <svg viewBox="0 0 64 64" aria-hidden="true">
              <path d="M32 10v28" />
              <path d="M21 22 32 10l11 12" />
              <path d="M14 38v8a8 8 0 0 0 8 8h20a8 8 0 0 0 8-8v-8" />
            </svg>
          </div>
          <h2>{{ isAnyUploading ? '素材正在注入工作台' : '拖拽视频到这里，或者点击打开本地文件' }}</h2>
          <p>{{ isAnyUploading ? '服务器正在接收并建立媒体记录' : '大面积上传入口保留，支持本地视频直传' }}</p>
        </div>

        <div class="url-upload-block">
          <div class="section-head">
            <h3>链接采集</h3>
            <span class="section-tag">URL Ingest</span>
          </div>
          <p class="panel-note">适合把 B站、YouTube 等链接直接送进同一个媒体池。</p>
          <div class="url-row">
            <input
              v-model.trim="videoUrl"
              class="url-input"
              type="text"
              placeholder="粘贴视频链接（B站 / YouTube / 抖音 等）"
              :disabled="isAnyUploading"
              @keyup.enter="uploadByUrl"
            />
            <button class="action-btn secondary" :disabled="isAnyUploading" @click="uploadByUrl">
              {{ urlUploading ? '上传中...' : '链接上传' }}
            </button>
          </div>
        </div>
      </section>

      <section class="workspace-grid">
        <section class="list-block">
          <div class="section-head">
            <div>
              <h3>媒体陈列</h3>
              <p class="section-desc">选中任意一条视频后，右侧工作区会切换到对应操作。</p>
            </div>
            <button class="ghost-btn dark" @click="fetchMediaList(false)">{{ loadingList ? '刷新中...' : '刷新列表' }}</button>
          </div>

          <div v-if="mediaList.length === 0" class="empty">
            <strong>这里还没有素材</strong>
            <span>先上传一个视频，我们再继续提取文字或生成全文总结。</span>
          </div>

          <div v-else class="video-grid">
            <div
              v-for="item in mediaList"
              :key="item.id"
              class="video-card"
              :class="{ active: selectedMediaId === item.id }"
              @click="selectMedia(item)"
              @keydown.enter="selectMedia(item)"
              tabindex="0"
              role="button"
            >
              <button
                class="card-delete-btn"
                :disabled="deletingId === item.id"
                @click.stop="deleteMedia(item)"
              >
                {{ deletingId === item.id ? '删除中' : '删除' }}
              </button>
              <div class="video-card-top">
                <span class="video-pill">{{ item.status || 'UNKNOWN' }}</span>
                <span class="video-id">#{{ item.id }}</span>
              </div>
              <div class="video-name">{{ item.filename }}</div>
              <div class="video-meta">上传时间：{{ formatTime(item.uploadTime) }}</div>
              <div class="video-meta">结果状态：{{ item.aiSummary ? '有总结' : item.transcriptText ? '有文字' : '待处理' }}</div>
            </div>
          </div>
        </section>

        <section class="control-column">
          <section v-if="selectedMedia" class="action-block">
            <div class="section-head">
              <div>
                <h3>操作面板</h3>
                <p class="section-desc">{{ selectedMedia.filename }}</p>
              </div>
              <span class="section-tag accent">{{ selectedStatusText }}</span>
            </div>

            <div class="action-row">
              <button class="action-btn secondary" :disabled="actionLoading !== ''" @click="downloadAudio">
                {{ actionLoading === 'download' ? '下载中...' : '下载音频' }}
              </button>
              <button class="action-btn cobalt" :disabled="actionLoading !== ''" @click="runTranscribe">
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

          <section v-else class="placeholder-block">
            <span class="section-tag">No media selected</span>
            <h3>先从左侧选一个视频</h3>
            <p>选中之后，这里会出现下载音频、提取文字和全文总结三个操作按钮。</p>
          </section>

          <section v-if="showResultPanel" class="result-block">
            <div class="section-head">
              <div>
                <h3>{{ resultType === 'transcript' ? '文字提取结果' : '全文总结结果' }}</h3>
                <p class="section-desc">{{ selectedMedia?.filename || '当前结果展示面板' }}</p>
              </div>
              <span class="section-tag">{{ resultType === 'transcript' ? 'Transcript' : 'Summary' }}</span>
            </div>
            <pre class="result-content">{{ resultContent }}</pre>
          </section>
        </section>
      </section>
    </main>

    <transition name="fade">
      <div v-if="banner" class="banner" :class="bannerType === 'error' ? 'error' : 'ok'">{{ banner }}</div>
    </transition>

    <div v-if="showAuthModal" class="auth-backdrop" @click.self="closeAuthModal">
      <div class="auth-panel">
        <div class="auth-head">
          <div>
            <h3>{{ authMode === 'login' ? '用户登录' : '用户注册' }}</h3>
            <p class="auth-head-sub">进入你的私人视频工作台</p>
          </div>
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
@import url('https://fonts.googleapis.com/css2?family=Manrope:wght@500;700;800&family=Space+Grotesk:wght@500;700&display=swap');

.page {
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(circle at top left, rgba(65, 145, 255, 0.16), transparent 30%),
    radial-gradient(circle at 82% 10%, rgba(255, 137, 88, 0.16), transparent 24%),
    linear-gradient(180deg, #07111f 0%, #0d1627 50%, #eef3f7 50.1%, #edf2f6 100%);
  color: #17202a;
  font-family: 'Manrope', 'Segoe UI', sans-serif;
}

.ambient {
  position: absolute;
  inset: auto;
  pointer-events: none;
  filter: blur(40px);
  opacity: 0.6;
}

.ambient-one {
  top: 110px;
  right: -80px;
  width: 300px;
  height: 300px;
  border-radius: 50%;
  background: rgba(53, 139, 255, 0.24);
}

.ambient-two {
  top: 320px;
  left: -60px;
  width: 260px;
  height: 260px;
  border-radius: 50%;
  background: rgba(255, 141, 87, 0.18);
}

.topbar {
  position: relative;
  z-index: 2;
  padding: 20px 24px 0;
}

.topbar-shell {
  max-width: 1240px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 18px 22px;
  border: 1px solid rgba(194, 216, 243, 0.14);
  border-radius: 24px;
  background: rgba(8, 17, 32, 0.72);
  backdrop-filter: blur(18px);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.22);
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 16px;
}

.brand-mark {
  width: 54px;
  height: 54px;
  display: grid;
  place-items: center;
  border-radius: 18px;
  background: linear-gradient(135deg, #5ca3ff 0%, #79e1d2 100%);
  color: #07111f;
  font-family: 'Space Grotesk', sans-serif;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 1px;
}

.brand-copy {
  display: grid;
  gap: 4px;
}

.brand {
  font-family: 'Space Grotesk', sans-serif;
  font-size: 30px;
  font-weight: 700;
  color: #f7fafc;
  letter-spacing: 0.02em;
}

.brand-sub {
  font-size: 13px;
  color: rgba(226, 232, 240, 0.72);
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.status-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 42px;
  padding: 0 16px;
  border: 1px solid rgba(124, 151, 189, 0.24);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.04);
  color: #d7e3f4;
  font-size: 13px;
}

.status-chip.live {
  border-color: rgba(122, 226, 194, 0.4);
  box-shadow: 0 0 0 1px rgba(122, 226, 194, 0.08);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #7ae2c2;
  box-shadow: 0 0 18px rgba(122, 226, 194, 0.8);
}

.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-panel {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-badge {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(255, 173, 120, 0.22), rgba(106, 171, 255, 0.28));
  color: #fff;
  font-weight: 800;
}

.user-copy {
  display: grid;
}

.user-label {
  color: #fff;
  font-size: 14px;
  font-weight: 700;
}

.user-sub {
  color: rgba(226, 232, 240, 0.66);
  font-size: 12px;
}

.main {
  position: relative;
  z-index: 1;
  max-width: 1240px;
  margin: 0 auto;
  padding: 22px 18px 48px;
  display: grid;
  gap: 24px;
}

.hero-panel {
  display: grid;
  grid-template-columns: 1.4fr 0.9fr;
  gap: 20px;
  padding: 18px 6px 6px;
}

.hero-copy {
  padding: 18px 4px;
}

.hero-kicker {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 12px;
  border-radius: 999px;
  background: rgba(122, 226, 194, 0.12);
  color: #8cebd0;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.hero-title {
  margin-top: 18px;
  max-width: 720px;
  color: #f8fbff;
  font-size: clamp(34px, 5vw, 58px);
  line-height: 1.04;
  letter-spacing: -0.04em;
  font-weight: 800;
}

.hero-text {
  margin-top: 18px;
  max-width: 680px;
  color: rgba(229, 238, 247, 0.76);
  font-size: 16px;
  line-height: 1.75;
}

.hero-stats {
  display: grid;
  gap: 14px;
  align-content: end;
}

.stat-card {
  padding: 18px 20px;
  border-radius: 20px;
  background: rgba(9, 17, 32, 0.74);
  border: 1px solid rgba(172, 198, 229, 0.14);
  box-shadow: 0 18px 40px rgba(6, 10, 17, 0.18);
}

.stat-label {
  display: block;
  color: rgba(215, 227, 244, 0.74);
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.14em;
}

.stat-value {
  display: block;
  margin-top: 8px;
  color: #fff;
  font-family: 'Space Grotesk', sans-serif;
  font-size: 36px;
  font-weight: 700;
}

.stat-value.compact {
  font-size: 24px;
  line-height: 1.3;
}

.upload-board {
  display: grid;
  grid-template-columns: 1.25fr 0.75fr;
  gap: 20px;
}

.upload-zone {
  min-height: 320px;
  border: 1px dashed rgba(123, 169, 232, 0.5);
  border-radius: 30px;
  background:
    radial-gradient(circle at top right, rgba(95, 164, 255, 0.18), transparent 28%),
    linear-gradient(145deg, rgba(12, 24, 46, 0.96) 0%, rgba(15, 34, 56, 0.9) 100%);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  cursor: pointer;
  transition: transform 0.3s ease, border-color 0.3s ease, box-shadow 0.3s ease;
  padding: 28px;
  color: #f7fbff;
  box-shadow: 0 30px 60px rgba(6, 18, 37, 0.16);
}

.upload-icon {
  width: 82px;
  height: 82px;
  margin-bottom: 18px;
  border-radius: 24px;
  background: linear-gradient(135deg, rgba(92, 163, 255, 0.18), rgba(125, 235, 210, 0.18));
  display: grid;
  place-items: center;
}

.upload-icon svg {
  width: 42px;
  height: 42px;
  fill: none;
  stroke: #90c8ff;
  stroke-width: 2.2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.upload-zone h2 {
  margin: 0;
  max-width: 560px;
  font-size: 34px;
  line-height: 1.15;
  letter-spacing: -0.03em;
  font-weight: 800;
}

.upload-zone p {
  margin-top: 14px;
  max-width: 520px;
  color: rgba(223, 234, 246, 0.72);
  font-size: 15px;
}

.upload-zone.dragging {
  border-color: #79b7ff;
  transform: translateY(-4px);
  box-shadow: 0 28px 46px rgba(66, 144, 255, 0.22);
}

.upload-zone.busy {
  pointer-events: none;
  opacity: 0.88;
}

.list-block,
.url-upload-block,
.action-block,
.result-block,
.placeholder-block {
  background: rgba(255, 255, 255, 0.82);
  border-radius: 28px;
  border: 1px solid rgba(199, 214, 233, 0.88);
  padding: 22px;
  box-shadow: 0 24px 70px rgba(31, 51, 76, 0.08);
  backdrop-filter: blur(12px);
}

.url-upload-block {
  align-self: stretch;
  background:
    radial-gradient(circle at top left, rgba(255, 158, 94, 0.16), transparent 24%),
    rgba(255, 255, 255, 0.85);
}

.panel-note {
  color: #5d6c7d;
  font-size: 14px;
  line-height: 1.6;
  margin-bottom: 14px;
}

.url-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px;
}

.url-input {
  height: 52px;
  border-radius: 16px;
  border: 1px solid #cfdae7;
  padding: 0 16px;
  font-size: 14px;
  outline: none;
  background: rgba(250, 252, 255, 0.92);
}

.url-input:focus {
  border-color: #4186dd;
  box-shadow: 0 0 0 4px rgba(65, 134, 221, 0.12);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 14px;
}

.section-head h3 {
  margin: 0;
  font-size: 21px;
  font-weight: 800;
  color: #12263f;
}

.section-desc {
  margin-top: 6px;
  color: #607288;
  font-size: 13px;
}

.section-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  background: #edf4fb;
  color: #40628b;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.section-tag.accent {
  background: rgba(15, 157, 122, 0.12);
  color: #11725c;
}

.workspace-grid {
  display: grid;
  grid-template-columns: 1.08fr 0.92fr;
  gap: 20px;
  align-items: start;
}

.control-column {
  display: grid;
  gap: 20px;
}

.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 14px;
}

.video-card {
  position: relative;
  min-height: 156px;
  border: 1px solid #d8e3f0;
  border-radius: 22px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.92) 0%, rgba(245, 249, 255, 0.98) 100%);
  text-align: left;
  padding: 16px;
  cursor: pointer;
  transition: transform 0.24s ease, border-color 0.24s ease, box-shadow 0.24s ease;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.video-card:hover {
  border-color: #6da7f3;
  transform: translateY(-2px);
  box-shadow: 0 14px 30px rgba(79, 126, 179, 0.12);
}

.video-card.active {
  border-color: #2f76d2;
  background: linear-gradient(180deg, #f7fbff 0%, #edf5ff 100%);
  box-shadow: 0 18px 34px rgba(47, 118, 210, 0.18);
}

.video-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.video-pill {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: #eef3fa;
  color: #58708b;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.06em;
}

.video-id {
  color: #8aa0bb;
  font-family: 'Space Grotesk', sans-serif;
  font-size: 12px;
}

.card-delete-btn {
  position: absolute;
  right: 14px;
  top: 14px;
  height: 28px;
  border: 1px solid #f5c8c3;
  background: rgba(255, 255, 255, 0.92);
  color: #b42318;
  border-radius: 999px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.card-delete-btn:hover:enabled {
  background: #fff4f2;
}

.card-delete-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.video-name {
  font-size: 17px;
  font-weight: 800;
  color: #12263f;
  margin-bottom: 10px;
  padding-right: 72px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  white-space: normal;
}

.video-meta {
  font-size: 13px;
  color: #5f738f;
  margin-top: 4px;
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.action-btn {
  min-width: 126px;
  height: 46px;
  border: none;
  border-radius: 14px;
  background: linear-gradient(135deg, #3477da 0%, #5ba7ff 100%);
  color: #fff;
  cursor: pointer;
  font-size: 14px;
  font-weight: 700;
  box-shadow: 0 12px 24px rgba(52, 119, 218, 0.24);
}

.action-btn.primary {
  background: linear-gradient(135deg, #139d7b 0%, #39cfb0 100%);
  box-shadow: 0 12px 24px rgba(19, 157, 123, 0.24);
}

.action-btn.secondary {
  background: linear-gradient(135deg, #202f45 0%, #314966 100%);
  box-shadow: 0 12px 24px rgba(32, 47, 69, 0.22);
}

.action-btn.cobalt {
  background: linear-gradient(135deg, #5d56ff 0%, #2d7dff 100%);
  box-shadow: 0 12px 24px rgba(72, 102, 255, 0.22);
}

.action-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.view-row {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.tiny-btn {
  height: 38px;
  border: 1px solid #cfdbeb;
  border-radius: 12px;
  background: #f8fbff;
  color: #27486f;
  padding: 0 14px;
  cursor: pointer;
  font-weight: 700;
}

.task-tip {
  margin-top: 16px;
  padding: 14px 16px;
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(19, 157, 123, 0.12), rgba(57, 207, 176, 0.08));
  border: 1px solid rgba(19, 157, 123, 0.18);
  font-size: 13px;
  color: #145e4d;
}

.result-content {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.65;
  max-height: 420px;
  overflow-y: auto;
  background: linear-gradient(180deg, #091321 0%, #101c2f 100%);
  color: #dce6f2;
  border-radius: 18px;
  padding: 18px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.empty {
  display: grid;
  gap: 8px;
  color: #687d99;
  padding: 24px 6px;
}

.empty strong {
  font-size: 18px;
  color: #153456;
}

.ghost-btn {
  height: 40px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.35);
  background: transparent;
  color: #ffffff;
  padding: 0 14px;
  cursor: pointer;
  font-weight: 700;
}

.ghost-btn.dark {
  color: #ffffff;
  border-color: #d1deed;
  background: #f7fbff;
}

.placeholder-block {
  min-height: 210px;
  display: grid;
  align-content: center;
  gap: 10px;
}

.placeholder-block h3 {
  margin: 0;
  font-size: 24px;
  font-weight: 800;
  color: #152c47;
}

.placeholder-block p {
  color: #61748b;
  line-height: 1.7;
}

.auth-backdrop {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: grid;
  place-items: center;
  background: rgba(3, 9, 20, 0.7);
  backdrop-filter: blur(10px);
  padding: 16px;
}

.auth-panel {
  width: min(92vw, 420px);
  background: linear-gradient(180deg, #fdfefe 0%, #eef4fa 100%);
  border-radius: 24px;
  overflow: hidden;
  box-shadow: 0 30px 60px rgba(3, 9, 20, 0.28);
}

.auth-head {
  min-height: 82px;
  background: linear-gradient(135deg, #081120 0%, #172843 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
}

.auth-head h3 {
  margin: 0;
  font-size: 22px;
  font-weight: 800;
}

.auth-head-sub {
  margin-top: 4px;
  color: rgba(220, 231, 244, 0.72);
  font-size: 13px;
}

.close-btn {
  border: none;
  width: 36px;
  height: 36px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
  font-size: 24px;
  cursor: pointer;
}

.auth-body {
  display: grid;
  gap: 12px;
  padding: 20px;
}

.auth-body input {
  height: 48px;
  border-radius: 14px;
  border: 1px solid #d7e1ec;
  padding: 0 14px;
  outline: none;
  background: rgba(255, 255, 255, 0.88);
}

.auth-submit {
  height: 48px;
  border-radius: 14px;
  border: none;
  background: linear-gradient(135deg, #1d78df 0%, #60a9ff 100%);
  color: #fff;
  cursor: pointer;
  font-weight: 800;
}

.auth-submit:disabled {
  opacity: 0.65;
}

.auth-switch {
  border: none;
  background: transparent;
  color: #3268aa;
  cursor: pointer;
  font-weight: 700;
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
  padding: 12px 16px;
  border-radius: 14px;
  color: #fff;
  z-index: 50;
  font-size: 14px;
  font-weight: 700;
  box-shadow: 0 14px 34px rgba(3, 9, 20, 0.24);
}

.banner.ok {
  background: linear-gradient(135deg, #16814a 0%, #1ea466 100%);
}

.banner.error {
  background: linear-gradient(135deg, #b42318 0%, #d94738 100%);
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
  .topbar {
    padding: 14px 14px 0;
  }

  .topbar-shell,
  .hero-panel,
  .upload-board,
  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .topbar-shell {
    flex-direction: column;
    align-items: flex-start;
  }

  .topbar-actions {
    width: 100%;
    flex-direction: column;
    align-items: stretch;
  }

  .user-panel {
    width: 100%;
  }

  .upload-zone {
    min-height: 240px;
  }

  .upload-zone h2 {
    font-size: 24px;
  }

  .url-row {
    grid-template-columns: 1fr;
  }

  .brand-sub,
  .user-sub,
  .hero-text {
    font-size: 14px;
  }
}
</style>
