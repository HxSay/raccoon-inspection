/**
 * GPS 定位封装：用于巡检签到（写入 inspection_task.longitude / latitude）。
 */
export interface GeoPosition {
  latitude: number
  longitude: number
  accuracy?: number
}

export function getCurrentPosition(timeoutMs = 15000): Promise<GeoPosition> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error('浏览器不支持定位'))
      return
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        resolve({
          latitude: pos.coords.latitude,
          longitude: pos.coords.longitude,
          accuracy: pos.coords.accuracy
        })
      },
      (err) => reject(err),
      { enableHighAccuracy: true, timeout: timeoutMs, maximumAge: 0 }
    )
  })
}
