import { useRef, useState } from 'react'
import { uploadDocument } from '../api'
import { Alert, Icon, PageHeader, Panel } from '../components/Common'
import { navigate } from '../router'
import { formatBytes } from '../utils'

const MAX_FILE_SIZE = 50 * 1024 * 1024

function validFile(file) {
  return /\.(md|markdown|zip)$/i.test(file?.name || '')
}

export default function UploadPage() {
  const inputRef = useRef(null)
  const [file, setFile] = useState(null)
  const [autoStart, setAutoStart] = useState(true)
  const [dragging, setDragging] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState('')

  function chooseFile(nextFile) {
    setError('')
    if (!nextFile) return
    if (!validFile(nextFile)) {
      setError('仅支持 .md、.markdown 或 .zip 文件')
      return
    }
    if (nextFile.size > MAX_FILE_SIZE) {
      setError('文件不能超过 50 MB')
      return
    }
    setFile(nextFile)
  }

  function onDrop(event) {
    event.preventDefault()
    setDragging(false)
    chooseFile(event.dataTransfer.files?.[0])
  }

  async function submit() {
    if (!file) {
      setError('请先选择要上传的文档')
      return
    }
    setUploading(true)
    setError('')
    try {
      const result = await uploadDocument(file, autoStart)
      navigate(`/uploads/${result.uploadId}`)
    } catch (uploadError) {
      setError(uploadError.message)
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className="page page-narrow">
      <PageHeader title="上传知识文档" description="上传 Markdown 文档或包含多份 Markdown 的 ZIP 压缩包。" />
      <Alert>{error}</Alert>

      <Panel className="upload-panel">
        <input
          ref={inputRef}
          className="visually-hidden"
          type="file"
          accept=".md,.markdown,.zip,text/markdown,application/zip"
          onChange={(event) => chooseFile(event.target.files?.[0])}
        />
        <div
          className={`drop-zone ${dragging ? 'drop-zone-active' : ''} ${file ? 'drop-zone-selected' : ''}`}
          onDragEnter={(event) => { event.preventDefault(); setDragging(true) }}
          onDragOver={(event) => event.preventDefault()}
          onDragLeave={() => setDragging(false)}
          onDrop={onDrop}
        >
          <span className="drop-icon"><Icon name={file ? 'check' : 'upload'} size={30} /></span>
          {file ? (
            <>
              <strong>{file.name}</strong>
              <p>{formatBytes(file.size)} · 已准备上传</p>
              <button className="button button-ghost" type="button" onClick={() => inputRef.current?.click()}>重新选择</button>
            </>
          ) : (
            <>
              <strong>拖放文档到这里</strong>
              <p>或者从设备中选择文件</p>
              <button className="button button-ghost" type="button" onClick={() => inputRef.current?.click()}>选择文件</button>
              <small>支持 MD、MARKDOWN、ZIP，最大 50 MB</small>
            </>
          )}
        </div>

        <div className="upload-options">
          <div>
            <strong>上传后自动生成题目</strong>
            <p>解析完成后立即创建 Codex 导入任务。</p>
          </div>
          <label className="switch">
            <input type="checkbox" checked={autoStart} onChange={(event) => setAutoStart(event.target.checked)} />
            <span />
          </label>
        </div>

        <button className="button button-primary upload-submit" type="button" disabled={!file || uploading} onClick={submit}>
          {uploading ? <span className="spinner spinner-light" /> : <Icon name="upload" />}
          {uploading ? '正在上传并创建解析任务…' : '上传文档'}
        </button>
      </Panel>

      <section className="upload-help-grid">
        <article><span>01</span><div><strong>Markdown</strong><p>单文件上传，保留原始内容用于预览与产题。</p></div></article>
        <article><span>02</span><div><strong>ZIP 批量上传</strong><p>后端安全解压并识别压缩包中的 Markdown 文件。</p></div></article>
        <article><span>03</span><div><strong>后台任务</strong><p>离开页面不会中断解析和 Codex 生成任务。</p></div></article>
      </section>
    </div>
  )
}
