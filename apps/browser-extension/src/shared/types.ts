export type PlatformId = 'x' | 'instagram' | 'threads'
export type JobState = 'queued' | 'analyzing' | 'downloading' | 'processing' | 'completed' | 'failed' | 'cancelled'

export interface MediaFormat {
  id: string
  label: string
  container: string | null
  width: number | null
  height: number | null
  has_video: boolean
  has_audio: boolean
  estimated_bytes: number | null
  requires_merge: boolean
}

export interface MediaAsset {
  id: string
  type: 'video' | 'audio' | 'image'
  thumbnail_url: string | null
  formats: MediaFormat[]
}

export interface MediaInfo {
  source_url: string
  canonical_url: string
  platform: PlatformId
  title: string | null
  author: { id: string | null; name: string | null; url: string | null }
  thumbnail_url: string | null
  duration_seconds: number | null
  assets: MediaAsset[]
  authentication: { required: boolean; mode: string }
}

export interface DownloadJob {
  id: string
  source_url: string
  canonical_url: string
  platform: PlatformId
  state: JobState
  selected_format_id: string | null
  title: string | null
  output_path: string | null
  progress: {
    downloaded_bytes: number
    total_bytes: number | null
    estimated_total_bytes: number | null
    speed_bytes_per_second: number | null
    eta_seconds: number | null
    progress: number | null
  }
  error_code: string | null
  error_message: string | null
}

export interface ServiceVersion {
  service_version: string
  api_version: string
  ffmpeg_available: boolean
  ffmpeg_error: string | null
}
