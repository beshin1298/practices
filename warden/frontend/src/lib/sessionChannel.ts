/**
 * Tells the other tabs of this browser that the user logged out, so they drop their in-memory
 * session and cached server data at once instead of on their next request.
 */
const channel = typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel('warden-session') : null;

export function announceLogout(): void {
  channel?.postMessage('logout');
}

export function onLogoutElsewhere(handler: () => void): () => void {
  if (!channel) return () => {};
  const listener = (event: MessageEvent) => {
    if (event.data === 'logout') handler();
  };
  channel.addEventListener('message', listener);
  return () => channel.removeEventListener('message', listener);
}
