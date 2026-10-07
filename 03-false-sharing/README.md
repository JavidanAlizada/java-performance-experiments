# False sharing in Java

Two threads, each incrementing its **own** `volatile long`, 100 million times.
Nothing is shared in the code, but fields that sit on the same cache line make the cores fight over it.

## Run

```bash
javac --add-exports java.base/jdk.internal.vm.annotation=ALL-UNNAMED FalseSharing.java
java -XX:-RestrictContended --add-exports java.base/jdk.internal.vm.annotation=ALL-UNNAMED FalseSharing
```

`-XX:-RestrictContended` is required, otherwise the JVM silently ignores `@Contended` on your own classes.

## Layouts

| Class | What it does |
|---|---|
| `Adjacent` | two hot fields next to each other |
| `Padded64` | 56 bytes of padding between the fields |
| `Padded128` | 120 bytes of padding between the fields |
| `PaddedAll` | padding before, between and after the fields |
| `ContendedFields` | `@Contended` on both fields |

The program first prints the real field offsets (via `Unsafe.objectFieldOffset`), then the timings.

## Results (Apple M4 Max, JDK 21, median of 7 rounds)

```
Padded64         a@80 b@16 distance=-64 bytes   <- the JVM moved b next to the object header

1 thread (baseline)    157 ms   1.0x
Adjacent             ~1,000 ms  ~6.4x   (unstable: 460-1,540 ms)
Padded64               ~260 ms  1.7x
Padded128              ~265 ms  1.7x
PaddedAll               187 ms  1.2x
@Contended              184 ms  1.2x
```

Padding *between* the fields is not enough: the JVM reorders fields, and one of them ends up sharing a line
with the object header. Padding on every side (or `@Contended`) fixes it.

This is a plain timing harness, not JMH. Treat the numbers as relative, and run it on your own hardware.
