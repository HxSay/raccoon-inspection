/**
 * 现场拍照：使用 input capture 调起相机，返回 File（可再转 Base64 写入 JSON）。
 */
export function pickCameraImage(): Promise<File | null> {
  return new Promise((resolve) => {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = 'image/*'
    input.capture = 'environment'
    input.onchange = () => resolve(input.files?.[0] ?? null)
    input.click()
  })
}

export function fileToDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const fr = new FileReader()
    fr.onload = () => resolve(fr.result as string)
    fr.onerror = () => reject(fr.error)
    fr.readAsDataURL(file)
  })
}

/** 将多张图片转为 JSON 数组字符串，对应表字段 image_urls / fault_image_urls */
export async function filesToJsonUrlArray(files: File[]): Promise<string> {
  const arr: string[] = []
  for (const f of files) {
    arr.push(await fileToDataUrl(f))
  }
  return JSON.stringify(arr)
}
