# Content Verification Report

Kotlin Compiler Server Version: 2.5.0-Beta2

## Summary

| Exercise Type | Checked | Passed | Failed | Warnings |
| --- | --- | --- | --- | --- |
| write_function | 12 | 12 | 0 | 0 |
| multi_part_problem | 17 | 13 | 4 | 0 |
| predict_output | 22 | 22 | 0 | 0 |
| multiple_choice | 3 | 3 | 0 | 0 |
| cloze | 28 | 28 | 0 | 22 |
| **Total** | **82** | **78** | **4** | **22** |

## Failures (7)

### 1. [ERROR] `kotlin-ch09-ex-telemetry-batcher` (Part: `kotlin-ch09-batcher-part-1`)
- **File**: `kotlin/chapters/ch09-mobile-terminal.yaml`
- **Exercise ID**: `kotlin-ch09-ex-telemetry-batcher`
- **Part ID**: `kotlin-ch09-batcher-part-1`
- **Check**: starter_code compilation
- **Evidence**:
  ```
  Solution.kt:16: Return type 'Nothing' needs to be specified explicitly.
  ```

### 2. [ERROR] `ch10-card-fee-report-problem` (Part: `ch10-card-fee-report-part-1`)
- **File**: `kotlin/chapters/ch10-stripe-classics.yaml`
- **Exercise ID**: `ch10-card-fee-report-problem`
- **Part ID**: `ch10-card-fee-report-part-1`
- **Check**: cumulative reference solution tests
- **Evidence**:
  ```
  CardFeeReportPart1Test.examples: expected:<[TransactionFee(id=tx1, feeCents=59), TransactionFee(id=tx2, feeCents=104), TransactionFee(id=tx3, feeCents=33)]> but was:<[TransactionFee(id=tx1, feeCents=33), TransactionFee(id=tx2, feeCents=37), TransactionFee(id=tx3, feeCents=30)]>
  ```

### 3. [ERROR] `ch10-card-fee-report-problem` (Part: `ch10-card-fee-report-part-2`)
- **File**: `kotlin/chapters/ch10-stripe-classics.yaml`
- **Exercise ID**: `ch10-card-fee-report-problem`
- **Part ID**: `ch10-card-fee-report-part-2`
- **Check**: cumulative reference solution tests
- **Evidence**:
  ```
  CardFeeReportPart2Test.example: expected:<[MerchantFeeReport(merchant=acme, volumeCents=3550, feesCents=163, netCents=3387), MerchantFeeReport(merchant=bolt, volumeCents=99, feesCents=33, netCents=66)]> but was:<[MerchantFeeReport(merchant=acme, volumeCents=3550, feesCents=70, netCents=3480), MerchantFeeReport(merchant=bolt, volumeCents=99, feesCents=30, netCents=69)]>
  CardFeeReportPart1Test.examples: expected:<[TransactionFee(id=tx1, feeCents=59), TransactionFee(id=tx2, feeCents=104), TransactionFee(id=tx3, feeCents=33)]> but was:<[TransactionFee(id=tx1, feeCents=33), TransactionFee(id=tx2, feeCents=37), TransactionFee(id=tx3, feeCents=30)]>
  ```

### 4. [ERROR] `ch10-card-fee-report-problem` (Part: `ch10-card-fee-report-part-3`)
- **File**: `kotlin/chapters/ch10-stripe-classics.yaml`
- **Exercise ID**: `ch10-card-fee-report-problem`
- **Part ID**: `ch10-card-fee-report-part-3`
- **Check**: cumulative reference solution tests
- **Evidence**:
  ```
  CardFeeReportPart3Test.examples: expected:<[MerchantFeeReport(merchant=acme, volumeCents=3550, feesCents=178, netCents=3372), MerchantFeeReport(merchant=bolt, volumeCents=99, feesCents=33, netCents=66)]> but was:<[MerchantFeeReport(merchant=acme, volumeCents=3550, feesCents=72, netCents=3478), MerchantFeeReport(merchant=bolt, volumeCents=99, feesCents=30, netCents=69)]>
  CardFeeReportPart2Test.example: expected:<[MerchantFeeReport(merchant=acme, volumeCents=3550, feesCents=163, netCents=3387), MerchantFeeReport(merchant=bolt, volumeCents=99, feesCents=33, netCents=66)]> but was:<[MerchantFeeReport(merchant=acme, volumeCents=3550, feesCents=70, netCents=3480), MerchantFeeReport(merchant=bolt, volumeCents=99, feesCents=30, netCents=69)]>
  CardFeeReportPart1Test.examples: expected:<[TransactionFee(id=tx1, feeCents=59), TransactionFee(id=tx2, feeCents=104), TransactionFee(id=tx3, feeCents=33)]> but was:<[TransactionFee(id=tx1, feeCents=33), TransactionFee(id=tx2, feeCents=37), TransactionFee(id=tx3, feeCents=30)]>
  ```

### 5. [ERROR] `ch10-card-fee-report-problem` (Part: `ch10-card-fee-report-part-4`)
- **File**: `kotlin/chapters/ch10-stripe-classics.yaml`
- **Exercise ID**: `ch10-card-fee-report-problem`
- **Part ID**: `ch10-card-fee-report-part-4`
- **Check**: cumulative reference solution tests
- **Evidence**:
  ```
  CardFeeReportPart4Test.example: expected:<[MerchantFeeReport(merchant=acme, volumeCents=2550, feesCents=149, netCents=2401), MerchantFeeReport(merchant=bolt, volumeCents=99, feesCents=33, netCents=66)]> but was:<[MerchantFeeReport(merchant=acme, volumeCents=2550, feesCents=69, netCents=2481), MerchantFeeReport(merchant=bolt, volumeCents=99, feesCents=30, netCents=69)]>
  CardFeeReportPart3Test.examples: expected:<[MerchantFeeReport(merchant=acme, volumeCents=3550, feesCents=178, netCents=3372), MerchantFeeReport(merchant=bolt, volumeCents=99, feesCents=33, netCents=66)]> but was:<[MerchantFeeReport(merchant=acme, volumeCents=3550, feesCents=72, netCents=3478), MerchantFeeReport(merchant=bolt, volumeCents=99, feesCents=30, netCents=69)]>
  CardFeeReportPart2Test.example: expected:<[MerchantFeeReport(merchant=acme, volumeCents=3550, feesCents=163, netCents=3387), MerchantFeeReport(merchant=bolt, volumeCents=99, feesCents=33, netCents=66)]> but was:<[MerchantFeeReport(merchant=acme, volumeCents=3550, feesCents=70, netCents=3480), MerchantFeeReport(merchant=bolt, volumeCents=99, feesCents=30, netCents=69)]>
  CardFeeReportPart1Test.examples: expected:<[TransactionFee(id=tx1, feeCents=59), TransactionFee(id=tx2, feeCents=104), TransactionFee(id=tx3, feeCents=33)]> but was:<[TransactionFee(id=tx1, feeCents=33), TransactionFee(id=tx2, feeCents=37), TransactionFee(id=tx3, feeCents=30)]>
  ```

### 6. [ERROR] `ch11-more-practice-shipping-routes-problem` (Part: `ch11-more-practice-shipping-routes-part-4`)
- **File**: `kotlin/chapters/ch11-more-practice.yaml`
- **Exercise ID**: `ch11-more-practice-shipping-routes-problem`
- **Part ID**: `ch11-more-practice-shipping-routes-part-4`
- **Check**: cumulative reference solution compilation
- **Evidence**:
  ```
  Solution.kt:28: No value passed for parameter 'refusedCarrier'.
  ```

### 7. [ERROR] `ch11-more-practice-shop-closing-time-problem` (Part: `ch11-more-practice-shop-closing-time-part-4`)
- **File**: `kotlin/chapters/ch11-more-practice.yaml`
- **Exercise ID**: `ch11-more-practice-shop-closing-time-problem`
- **Part ID**: `ch11-more-practice-shop-closing-time-part-4`
- **Check**: cumulative reference solution tests
- **Evidence**:
  ```
  ShopClosingTimePart4Test.handlesALargeLog: java.lang.OutOfMemoryError: Java heap space
  ```

## Warnings (22)

### 1. [WARNING] `ch01-syntax-values-cloze-null`
- **File**: `kotlin/chapters/ch01-syntax.yaml`
- **Exercise ID**: `ch01-syntax-values-cloze-null`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  ```

### 2. [WARNING] `ch01-syntax-control-cloze-require`
- **File**: `kotlin/chapters/ch01-syntax.yaml`
- **Exercise ID**: `ch01-syntax-control-cloze-require`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Function declaration must have a name.
  ```

### 3. [WARNING] `ch02-strings-splitting-cloze-safe-number`
- **File**: `kotlin/chapters/ch02-strings.yaml`
- **Exercise ID**: `ch02-strings-splitting-cloze-safe-number`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:1: Unresolved reference 'amountText'.
  ```

### 4. [WARNING] `ch02-strings-splitting-cloze-padding`
- **File**: `kotlin/chapters/ch02-strings.yaml`
- **Exercise ID**: `ch02-strings-splitting-cloze-padding`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  ```

### 5. [WARNING] `ch02-strings-structured-cloze-regex`
- **File**: `kotlin/chapters/ch02-strings.yaml`
- **Exercise ID**: `ch02-strings-structured-cloze-regex`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  ```

### 6. [WARNING] `ch03-collections-grouping-cloze-count`
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

### 7. [WARNING] `ch03-collections-searching-cloze-duplicate`
- **File**: `kotlin/chapters/ch03-collections.yaml`
- **Exercise ID**: `ch03-collections-searching-cloze-duplicate`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  ```

### 8. [WARNING] `ch03-collections-searching-cloze-bucket`
- **File**: `kotlin/chapters/ch03-collections.yaml`
- **Exercise ID**: `ch03-collections-searching-cloze-bucket`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Function declaration must have a name.
  Snippet.kt:1: Cannot infer type for type parameter 'T'. Specify it explicitly.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  ```

### 9. [WARNING] `ch04-testing-junit-cloze-equals`
- **File**: `kotlin/chapters/ch04-testing.yaml`
- **Exercise ID**: `ch04-testing-junit-cloze-equals`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  ```

### 10. [WARNING] `ch04-testing-harness-cloze-failures`
- **File**: `kotlin/chapters/ch04-testing.yaml`
- **Exercise ID**: `ch04-testing-harness-cloze-failures`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Function declaration must have a name.
  Snippet.kt:1: Unresolved reference 'failures'.
  Snippet.kt:1: Unresolved reference 'failures'.
  ```

### 11. [WARNING] `kotlin-ch05-results-cloze-guard`
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

### 12. [WARNING] `kotlin-ch05-results-cloze-filter`
- **File**: `kotlin/chapters/ch05-modeling.yaml`
- **Exercise ID**: `kotlin-ch05-results-cloze-filter`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:1: Unresolved reference 'results'.
  Snippet.kt:1: Unresolved reference 'RowResult'.
  Snippet.kt:1: Unresolved reference 'it'.
  ```

### 13. [WARNING] `kotlin-ch05-enums-cloze-low`
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

### 14. [WARNING] `kotlin-ch06-queues-cloze-average`
- **File**: `kotlin/chapters/ch06-time-queues-graphs.yaml`
- **Exercise ID**: `kotlin-ch06-queues-cloze-average`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  ```

### 15. [WARNING] `kotlin-ch06-queues-cloze-window`
- **File**: `kotlin/chapters/ch06-time-queues-graphs.yaml`
- **Exercise ID**: `kotlin-ch06-queues-cloze-window`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  ```

### 16. [WARNING] `kotlin-ch06-graphs-cloze-previous`
- **File**: `kotlin/chapters/ch06-time-queues-graphs.yaml`
- **Exercise ID**: `kotlin-ch06-graphs-cloze-previous`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Function declaration must have a name.
  Snippet.kt:2: Unresolved reference 'previous'.
  Snippet.kt:2: Unresolved reference 'next'.
  Snippet.kt:2: Unresolved reference 'node'.
  Snippet.kt:3: Unresolved reference 'queue'.
  Snippet.kt:3: Unresolved reference 'next'.
  ```

### 17. [WARNING] `kotlin-ch06-graphs-cloze-map`
- **File**: `kotlin/chapters/ch06-time-queues-graphs.yaml`
- **Exercise ID**: `kotlin-ch06-graphs-cloze-map`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Function declaration must have a name.
  Snippet.kt:1: Cannot infer type for type parameter 'T'. Specify it explicitly.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  ```

### 18. [WARNING] `kotlin-ch07-readable-cloze-sorted`
- **File**: `kotlin/chapters/ch07-idioms.yaml`
- **Exercise ID**: `kotlin-ch07-readable-cloze-sorted`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Function declaration must have a name.
  Snippet.kt:1: Unresolved reference 'merchant'.
  Snippet.kt:1: Syntax error: Expecting ')'.
  Snippet.kt:1: Syntax error: Unexpected tokens (use ';' to separate expressions on the same line).
  ```

### 19. [WARNING] `kotlin-ch07-money-cloze-round`
- **File**: `kotlin/chapters/ch07-idioms.yaml`
- **Exercise ID**: `kotlin-ch07-money-cloze-round`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  ```

### 20. [WARNING] `kotlin-ch07-money-cloze-value`
- **File**: `kotlin/chapters/ch07-idioms.yaml`
- **Exercise ID**: `kotlin-ch07-money-cloze-value`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  Snippet.kt:1: Syntax error: Expecting a top level declaration.
  ```

### 21. [WARNING] `kotlin-ch08-cache-cloze-age`
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

### 22. [WARNING] `kotlin-ch08-cache-cloze-null`
- **File**: `kotlin/chapters/ch08-mobile-kotlin.yaml`
- **Exercise ID**: `kotlin-ch08-cache-cloze-null`
- **Check**: cloze snippet highlighting
- **Evidence**:
  ```
  Snippet.kt:1: Unresolved reference 'entry'.
  Snippet.kt:1: 'return' is prohibited here.
  ```

