import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { spawnSync } from 'node:child_process'
import ts from 'typescript'
import type { CoursePack, WriteFunctionExercise } from '../src/types.ts'

const packDir = resolve(import.meta.dirname, '../packs')
function solution(filename: string): string {
  const pack = JSON.parse(readFileSync(join(packDir, filename), 'utf8')) as CoursePack
  const exercise = pack.courses[0].chapters.flatMap((chapter) => chapter.lessons.flatMap((lesson) => lesson.exercises)).find((item) => item.type === 'write_function') as WriteFunctionExercise | undefined
  if (!exercise?.referenceSolution) throw new Error(`No guided reference solution in ${filename}`)
  return exercise.referenceSolution
}

function run(label: string, command: string, args: string[]) {
  const result = spawnSync(command, args, { encoding: 'utf8' })
  if (result.error || result.status !== 0) throw new Error(`${label}: ${result.error?.message ?? result.stderr ?? result.stdout}`)
  console.log(`${label}: passed`)
}

const directory = mkdtempSync(join(tmpdir(), 'learn-code-guided-'))
try {
  const swift = `${solution('swift-foundations.json')}\nassert(lowerBound([1, 2, 2, 4], 2) == 1)\nassert(lowerBound([1, 2, 2, 4], 3) == 3)\nassert(lowerBound([], 1) == 0)\n`
  const swiftFile = join(directory, 'Guided.swift')
  writeFileSync(swiftFile, swift)
  run('Swift lower bound', 'swift', [swiftFile])

  const dart = `${solution('flutter-dart-interviews.json')}\nvoid main() {\n  final result = countIds(['a', 'b', 'a']);\n  assert(result['a'] == 2 && result['b'] == 1);\n  assert(countIds([]).isEmpty);\n}\n`
  const dartFile = join(directory, 'guided.dart')
  writeFileSync(dartFile, dart)
  run('Dart frequency map', 'dart', ['--enable-asserts', dartFile])

  const java = `import java.util.*;\nclass GuidedCheck {\n${solution('java-android-interviews.json')}\npublic static void main(String[] args) {\n  var solver = new GuidedCheck();\n  var result = solver.countIds(Arrays.asList("a", "b", "a"));\n  if (result.get("a") != 2 || result.get("b") != 1 || !solver.countIds(List.of()).isEmpty()) throw new AssertionError(result);\n}\n}\n`
  const javaFile = join(directory, 'GuidedCheck.java')
  writeFileSync(javaFile, java)
  run('Java frequency map', 'java', [javaFile])

  const js = ts.transpileModule(solution('react-native-typescript-interviews.json'), { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText
  const requestId = { current: 0 }
  const pending: Array<{ query: string; resolve: (rows: string[]) => void }> = []
  const repository = { search: (query: string) => new Promise<string[]>((resolve) => pending.push({ query, resolve })) }
  let visible: string[] = []
  const search = new Function('requestId', 'repository', 'setRows', `${js}\nreturn search;`)(requestId, repository, (rows: string[]) => { visible = rows }) as (query: string) => Promise<void>
  const first = search('A')
  const second = search('B')
  pending.find((item) => item.query === 'B')!.resolve(['B'])
  await second
  pending.find((item) => item.query === 'A')!.resolve(['A'])
  await first
  if (visible.join(',') !== 'B') throw new Error('TypeScript search applied a stale response')
  console.log('TypeScript latest-request search: passed')

  console.log('Kotlin executable check: skipped (kotlinc is not installed); guided reconstruction is validated by the pack validator')
} finally {
  rmSync(directory, { recursive: true, force: true })
}
