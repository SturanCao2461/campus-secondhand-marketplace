import { useEffect, useRef } from 'react'

export function useBrowserNotification() {
  const permissionRef = useRef<NotificationPermission>('default')

  useEffect(() => {
    if (!('Notification' in window)) return
    permissionRef.current = Notification.permission
    if (Notification.permission === 'default') {
      Notification.requestPermission().then(p => {
        permissionRef.current = p
      })
    }
  }, [])

  const notify = (title: string, body: string, onClick?: () => void) => {
    if (permissionRef.current !== 'granted') return
    if (document.visibilityState === 'visible') return

    const n = new Notification(title, { body, icon: '/favicon.ico' })
    if (onClick) {
      n.onclick = () => {
        window.focus()
        onClick()
        n.close()
      }
    }
  }

  return { notify }
}
