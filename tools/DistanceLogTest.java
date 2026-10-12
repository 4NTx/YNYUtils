import com.yny.targethealth.DistanceLog;

public final class DistanceLogTest {
    public static void main(String[] args) {
        check("2m", DistanceLog.bucket("2.3m"));
        check("3m", DistanceLog.bucket("2.7m"));
        check("4m", DistanceLog.bucket("4.0m"));
        check("?", DistanceLog.bucket("?"));
        System.out.println("PASS: distance log grouping");
    }

    private static void check(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError(actual);
    }
}
