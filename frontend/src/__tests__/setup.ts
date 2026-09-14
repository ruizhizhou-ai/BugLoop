/**
 * 本文件为单元测试提供浏览器存储实现，避免 Node 的 localStorage 在测试环境下不可用。
 */

function createMemoryStorage(): Storage {
  const store = new Map<string, string>()
  return {
    get length() {
      return store.size
    },
    clear: () => store.clear(),
    getItem: (key: string) => store.get(key) ?? null,
    key: (index: number) => [...store.keys()][index] ?? null,
    removeItem: (key: string) => {
      store.delete(key)
    },
    setItem: (key: string, value: string) => {
      store.set(key, String(value))
    },
  }
}

if (!globalThis.localStorage) {
  Object.defineProperty(globalThis, 'localStorage', { value: createMemoryStorage(), writable: true })
}

if (!globalThis.sessionStorage) {
  Object.defineProperty(globalThis, 'sessionStorage', { value: createMemoryStorage(), writable: true })
}
