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

// Node 新版本可能提供不可直接使用的实验性 Storage；测试中始终替换为确定性的内存实现。
Object.defineProperty(globalThis, 'localStorage', { value: createMemoryStorage(), writable: true })
Object.defineProperty(globalThis, 'sessionStorage', { value: createMemoryStorage(), writable: true })

// jsdom 没有 ResizeObserver 和 matchMedia，Element Plus 表格在测量容器时需要它们。
class ResizeObserverStub {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}

Object.defineProperty(globalThis, 'ResizeObserver', { value: ResizeObserverStub, writable: true })

if (!globalThis.matchMedia) {
  Object.defineProperty(globalThis, 'matchMedia', {
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }),
    writable: true,
  })
}
