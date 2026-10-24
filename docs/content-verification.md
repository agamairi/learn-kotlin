# Content Verification Report

Kotlin Compiler Server Version: 2.5.0-Beta2

## Summary

| Exercise Type | Checked | Passed | Failed | Warnings |
| --- | --- | --- | --- | --- |
| write_function | 12 | 12 | 0 | 0 |
| multi_part_problem | 17 | 17 | 0 | 0 |
| predict_output | 22 | 22 | 0 | 0 |
| multiple_choice | 3 | 3 | 0 | 0 |
| cloze | 28 | 28 | 0 | 21 |
| **Total** | **82** | **82** | **0** | **21** |

## Failures (0)

No failures found.

## Warnings (21)

### 1. [WARNING] `ch01-syntax-values-cloze-null`
- **File**: `kotlin/chapters/ch01-syntax.yaml`
- **Exercise ID**: `ch01-syntax-values-cloze-null`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:2: Unresolved reference 'nickname'.
  ```

### 2. [WARNING] `ch01-syntax-control-cloze-require`
- **File**: `kotlin/chapters/ch01-syntax.yaml`
- **Exercise ID**: `ch01-syntax-control-cloze-require`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:2: Unresolved reference 'amountCents'.
  ```

### 3. [WARNING] `ch02-strings-splitting-cloze-safe-number`
- **File**: `kotlin/chapters/ch02-strings.yaml`
- **Exercise ID**: `ch02-strings-splitting-cloze-safe-number`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:1: Unresolved reference 'amountText'.
  ```

### 4. [WARNING] `ch02-strings-structured-cloze-regex`
- **File**: `kotlin/chapters/ch02-strings.yaml`
- **Exercise ID**: `ch02-strings-structured-cloze-regex`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:2: Unresolved reference 'pair'.
  ```

### 5. [WARNING] `ch03-collections-grouping-cloze-count`
- **File**: `kotlin/chapters/ch03-collections.yaml`
- **Exercise ID**: `ch03-collections-grouping-cloze-count`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:1: Unresolved reference 'txs'.
  Snippet.kt:1: Unresolved reference 'it'.
  Snippet.kt:1: Cannot infer type for type parameter 'T'. Specify it explicitly.
  Snippet.kt:1: Cannot infer type for type parameter 'K'. Specify it explicitly.
  ```

### 6. [WARNING] `ch03-collections-searching-cloze-duplicate`
- **File**: `kotlin/chapters/ch03-collections.yaml`
- **Exercise ID**: `ch03-collections-searching-cloze-duplicate`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:2: Unresolved reference 'seen'.
  Snippet.kt:2: Unresolved reference 'id'.
  Snippet.kt:2: Unresolved reference 'id'.
  ```

### 7. [WARNING] `ch03-collections-searching-cloze-bucket`
- **File**: `kotlin/chapters/ch03-collections.yaml`
- **Exercise ID**: `ch03-collections-searching-cloze-bucket`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:2: Unresolved reference 'buckets'.
  Snippet.kt:2: Cannot infer type for type parameter 'K'. Specify it explicitly.
  Snippet.kt:2: Cannot infer type for type parameter 'V'. Specify it explicitly.
  Snippet.kt:2: Unresolved reference 'tx'.
  Snippet.kt:2: Cannot infer type for type parameter 'T'. Specify it explicitly.
  Snippet.kt:2: Unresolved reference 'add'.
  Snippet.kt:2: Unresolved reference 'tx'.
  ```

### 8. [WARNING] `ch04-testing-junit-cloze-equals`
- **File**: `kotlin/chapters/ch04-testing.yaml`
- **Exercise ID**: `ch04-testing-junit-cloze-equals`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:2: Unresolved reference 'assertEquals'.
  Snippet.kt:2: Unresolved reference 'feeCents'.
  ```

### 9. [WARNING] `ch04-testing-harness-cloze-failures`
- **File**: `kotlin/chapters/ch04-testing.yaml`
- **Exercise ID**: `ch04-testing-harness-cloze-failures`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:2: Syntax error: Expecting an element.
  Snippet.kt:2: Unresolved reference 'failures'.
  Snippet.kt:2: Unresolved reference 'failures'.
  ```

### 10. [WARNING] `kotlin-ch05-results-cloze-guard`
- **File**: `kotlin/chapters/ch05-modeling.yaml`
- **Exercise ID**: `kotlin-ch05-results-cloze-guard`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:1: Unresolved reference 'batteryText'.
  Snippet.kt:2: 'return' is prohibited here.
  Snippet.kt:2: Unresolved reference 'RowResult'.
  Snippet.kt:2: Unresolved reference 'lineNo'.
  ```

### 11. [WARNING] `kotlin-ch05-results-cloze-filter`
- **File**: `kotlin/chapters/ch05-modeling.yaml`
- **Exercise ID**: `kotlin-ch05-results-cloze-filter`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:1: Unresolved reference 'results'.
  Snippet.kt:1: Unresolved reference 'RowResult'.
  Snippet.kt:1: Unresolved reference 'it'.
  ```

### 12. [WARNING] `kotlin-ch05-enums-cloze-low`
- **File**: `kotlin/chapters/ch05-modeling.yaml`
- **Exercise ID**: `kotlin-ch05-enums-cloze-low`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:1: Unresolved reference 'BatteryLevel'.
  Snippet.kt:2: Unresolved reference 'LOW'.
  Snippet.kt:3: Unresolved reference 'OK'.
  Snippet.kt:4: Unresolved reference 'FULL'.
  ```

### 13. [WARNING] `kotlin-ch06-queues-cloze-average`
- **File**: `kotlin/chapters/ch06-time-queues-graphs.yaml`
- **Exercise ID**: `kotlin-ch06-queues-cloze-average`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:2: Unresolved reference 'values'.
  Snippet.kt:2: Unresolved reference 'size'.
  Snippet.kt:2: Unresolved reference 'sum'.
  Snippet.kt:2: Unresolved reference 'values'.
  Snippet.kt:2: Cannot infer type for type parameter 'T'. Specify it explicitly.
  ```

### 14. [WARNING] `kotlin-ch06-queues-cloze-window`
- **File**: `kotlin/chapters/ch06-time-queues-graphs.yaml`
- **Exercise ID**: `kotlin-ch06-queues-cloze-window`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:2: Unresolved reference 'timestamp'.
  Snippet.kt:2: Unresolved reference 'now'.
  Snippet.kt:2: Unresolved reference 'timestamp'.
  Snippet.kt:2: Unresolved reference 'now'.
  ```

### 15. [WARNING] `kotlin-ch06-graphs-cloze-previous`
- **File**: `kotlin/chapters/ch06-time-queues-graphs.yaml`
- **Exercise ID**: `kotlin-ch06-graphs-cloze-previous`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:2: Unresolved reference 'next'.
  Snippet.kt:2: Unresolved reference 'not' for operator '!'.
  Snippet.kt:2: Unresolved reference 'previous'.
  Snippet.kt:3: Unresolved reference 'previous'.
  Snippet.kt:3: Unresolved reference 'next'.
  Snippet.kt:3: Unresolved reference 'node'.
  Snippet.kt:4: Unresolved reference 'queue'.
  Snippet.kt:4: Unresolved reference 'next'.
  ```

### 16. [WARNING] `kotlin-ch06-graphs-cloze-map`
- **File**: `kotlin/chapters/ch06-time-queues-graphs.yaml`
- **Exercise ID**: `kotlin-ch06-graphs-cloze-map`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:2: Unresolved reference 'graph'.
  Snippet.kt:2: Cannot infer type for type parameter 'V'. Specify it explicitly.
  Snippet.kt:2: Cannot infer type for type parameter 'T'. Specify it explicitly.
  Snippet.kt:2: Unresolved reference 'add'.
  Snippet.kt:2: Unresolved reference 'Edge'.
  ```

### 17. [WARNING] `kotlin-ch07-readable-cloze-sorted`
- **File**: `kotlin/chapters/ch07-idioms.yaml`
- **Exercise ID**: `kotlin-ch07-readable-cloze-sorted`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:2: Unresolved reference 'totals'.
  Snippet.kt:2: Cannot infer type for type parameter 'K'. Specify it explicitly.
  Snippet.kt:2: Cannot infer type for type parameter 'V'. Specify it explicitly.
  Snippet.kt:2: Cannot infer type for type parameter 'T'. Specify it explicitly.
  Snippet.kt:2: Cannot infer type for type parameter 'T'. Specify it explicitly.
  Snippet.kt:2: Operator call 'component1()' is ambiguous for destructuring of type 'uninferred ??? (Unknown type for type parameter T)'. Applicable candidates:
  fun <T> Array<out T>.component1(): T
  fun ByteArray.component1(): Byte
  fun ShortArray.component1(): Short
  fun IntArray.component1(): Int
  fun LongArray.component1(): Long
  fun FloatArray.component1(): Float
  fun DoubleArray.component1(): Double
  fun BooleanArray.component1(): Boolean
  fun CharArray.component1(): Char
  fun <T> List<T>.component1(): T
  fun <K, V> Map.Entry<K, V>.component1(): K
  fun UIntArray.component1(): UInt
  fun ULongArray.component1(): ULong
  fun UByteArray.component1(): UByte
  fun UShortArray.component1(): UShort
  Snippet.kt:2: Operator call 'component2()' is ambiguous for destructuring of type 'uninferred ??? (Unknown type for type parameter T)'. Applicable candidates:
  fun <T> Array<out T>.component2(): T
  fun ByteArray.component2(): Byte
  fun ShortArray.component2(): Short
  fun IntArray.component2(): Int
  fun LongArray.component2(): Long
  fun FloatArray.component2(): Float
  fun DoubleArray.component2(): Double
  fun BooleanArray.component2(): Boolean
  fun CharArray.component2(): Char
  fun <T> List<T>.component2(): T
  fun <K, V> Map.Entry<K, V>.component2(): V
  fun UIntArray.component2(): UInt
  fun ULongArray.component2(): ULong
  fun UByteArray.component2(): UByte
  fun UShortArray.component2(): UShort
  ```

### 18. [WARNING] `kotlin-ch07-money-cloze-round`
- **File**: `kotlin/chapters/ch07-idioms.yaml`
- **Exercise ID**: `kotlin-ch07-money-cloze-round`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:2: Syntax error: Expecting an element.
  Snippet.kt:2: Unresolved reference 'setScale'.
  Snippet.kt:2: Unresolved reference 'RoundingMode'.
  ```

### 19. [WARNING] `kotlin-ch07-money-cloze-value`
- **File**: `kotlin/chapters/ch07-idioms.yaml`
- **Exercise ID**: `kotlin-ch07-money-cloze-value`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:2: Unresolved reference 'BigDecimal'.
  Snippet.kt:2: Unresolved reference 'cents'.
  ```

### 20. [WARNING] `kotlin-ch08-cache-cloze-age`
- **File**: `kotlin/chapters/ch08-mobile-kotlin.yaml`
- **Exercise ID**: `kotlin-ch08-cache-cloze-age`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:1: Unresolved reference 'clock'.
  Snippet.kt:1: Unresolved reference 'storedAt'.
  Snippet.kt:1: 'operator' modifier is required on 'fun String.compareTo(other: String, ignoreCase: Boolean = ...): Int'.
  Snippet.kt:1: Unresolved reference 'ttlMillis'.
  ```

### 21. [WARNING] `kotlin-ch08-cache-cloze-null`
- **File**: `kotlin/chapters/ch08-mobile-kotlin.yaml`
- **Exercise ID**: `kotlin-ch08-cache-cloze-null`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:1: Unresolved reference 'entry'.
  Snippet.kt:1: 'return' is prohibited here.
  ```

