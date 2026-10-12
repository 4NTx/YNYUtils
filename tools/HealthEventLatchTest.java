import com.yny.targethealth.HealthEventLatch;

public final class HealthEventLatchTest {
    public static void main(String[] args) throws Exception {
        Object target = new Object(), other = new Object();
        HealthEventLatch.record(target, 11.5F, 2.0F);
        check(11.5F, HealthEventLatch.health(20.0F, target));
        check(2.0F, HealthEventLatch.absorption(0.0F, target));
        check(20.0F, HealthEventLatch.health(20.0F, other));
        Thread.sleep(450L);
        check(20.0F, HealthEventLatch.health(20.0F, target));
        System.out.println("PASS: health event wins over stale read, then expires");
    }
    private static void check(float expected, float actual) {
        if (Math.abs(expected - actual) > 0.001F) throw new AssertionError(actual);
    }
}
