import java.lang.reflect.Field;
import java.util.Arrays;
import jdk.internal.vm.annotation.Contended;
import sun.misc.Unsafe;

public class FalseSharing {
    interface Counters { void incA(); void incB(); long sum(); }

    static final class Adjacent implements Counters {
        volatile long a; volatile long b;
        public void incA() { a++; } public void incB() { b++; } public long sum() { return a + b; }
    }
    static final class Padded64 implements Counters {
        volatile long a; long p1, p2, p3, p4, p5, p6, p7; volatile long b;
        public void incA() { a++; } public void incB() { b++; } public long sum() { return a + b; }
    }
    static final class Padded128 implements Counters {
        volatile long a; long p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15; volatile long b;
        public void incA() { a++; } public void incB() { b++; } public long sum() { return a + b; }
    }
    static final class ContendedFields implements Counters {
        @Contended volatile long a; @Contended volatile long b;
        public void incA() { a++; } public void incB() { b++; } public long sum() { return a + b; }
    }

    static final class PaddedAll implements Counters {
        long q1, q2, q3, q4, q5, q6, q7, q8, q9, q10, q11, q12, q13, q14, q15, q16; volatile long a; long p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16; volatile long b; long r1, r2, r3, r4, r5, r6, r7, r8, r9, r10, r11, r12, r13, r14, r15, r16;
        public void incA() { a++; } public void incB() { b++; } public long sum() { return a + b; }
    }
    static final long N = 100_000_000L;

    static long runTwo(Counters c) throws Exception {
        Thread t1 = new Thread(() -> { for (long i = 0; i < N; i++) c.incA(); });
        Thread t2 = new Thread(() -> { for (long i = 0; i < N; i++) c.incB(); });
        long s = System.nanoTime();
        t1.start(); t2.start(); t1.join(); t2.join();
        long e = System.nanoTime() - s;
        if (c.sum() != 2 * N) throw new AssertionError();
        return e;
    }
    static long runOne(Counters c) {
        long s = System.nanoTime();
        for (long i = 0; i < N; i++) c.incA();
        return System.nanoTime() - s;
    }

    static void offsets(Class<?> k) throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true);
        Unsafe u = (Unsafe) f.get(null);
        long a = u.objectFieldOffset(k.getDeclaredField("a")), b = u.objectFieldOffset(k.getDeclaredField("b"));
        System.out.printf("%-16s a@%d b@%d distance=%d bytes%n", k.getSimpleName(), a, b, b - a);
    }

    public static void main(String[] args) throws Exception {
        for (Class<?> k : new Class<?>[]{Adjacent.class, Padded64.class, Padded128.class, ContendedFields.class, PaddedAll.class}) offsets(k);
        String[] names = {"1 thread (baseline)", "Adjacent", "Padded64", "Padded128", "@Contended", "PaddedAll"};
        int rounds = 7;
        long[][] r = new long[names.length][rounds];
        for (int w = 0; w < 2; w++) { runOne(new Adjacent()); runTwo(new Adjacent()); runTwo(new Padded128()); }
        for (int i = 0; i < rounds; i++) {
            r[0][i] = runOne(new Adjacent());
            r[1][i] = runTwo(new Adjacent());
            r[2][i] = runTwo(new Padded64());
            r[3][i] = runTwo(new Padded128());
            r[4][i] = runTwo(new ContendedFields());
            r[5][i] = runTwo(new PaddedAll());
        }
        for (int j = 0; j < names.length; j++) {
            long[] x = r[j].clone(); Arrays.sort(x);
            System.out.printf("%-22s median %6d ms  (%.2f ns/op)  min %d max %d%n", names[j],
                x[rounds / 2] / 1_000_000, x[rounds / 2] / (double) N, x[0] / 1_000_000, x[rounds - 1] / 1_000_000);
        }
    }
}
