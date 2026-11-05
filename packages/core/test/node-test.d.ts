declare module 'node:test' {
  export interface TestContext {
    beforeEach?: (fn: () => void | Promise<void>) => void;
  }
  export type TestFn = (t?: TestContext) => void | Promise<void>;

  export function test(name: string, fn: TestFn): void;
  export function describe(name: string, fn: () => void): void;
  export function beforeEach(fn: () => void | Promise<void>): void;
}

declare module 'node:assert/strict' {
  export function equal<T>(actual: T, expected: T, message?: string | Error): void;
  export function notEqual<T>(actual: T, expected: T, message?: string | Error): void;
  export function deepEqual<T>(actual: T, expected: T, message?: string | Error): void;
  export function match(value: string, regExp: RegExp, message?: string | Error): void;
  export function throws(fn: () => unknown, expected?: unknown, message?: string | Error): void;
}
