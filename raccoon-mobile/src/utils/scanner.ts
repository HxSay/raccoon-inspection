import { showDialog } from 'vant'

/**
 * 扫码入口：H5 环境无统一原生扫码 API，此处提供手动输入；真机可替换为微信/企微 JSSDK scanQRCode。
 */
export async function scanDeviceCode(): Promise<string | null> {
  return promptDeviceCode()
}

export async function promptDeviceCode(defaultVal = ''): Promise<string | null> {
  return new Promise((resolve) => {
    showDialog({
      title: '设备编号 / 扫码内容',
      message: `<input id="raccoon-dc-inp" style="width:100%;padding:10px;box-sizing:border-box;border:1px solid #ccc;border-radius:6px;font-size:14px;" value="${defaultVal}" placeholder="手动输入 device_code" />`,
      showCancelButton: true,
      allowHtml: true,
      confirmButtonText: '确定',
      beforeClose: (action) => {
        if (action === 'confirm') {
          const el = document.getElementById('raccoon-dc-inp') as HTMLInputElement | null
          resolve(el?.value?.trim() || null)
        } else resolve(null)
        return true
      }
    }).catch(() => resolve(null))
  })
}
