package com.yny.targethealth;

public final class TargetGraceTest {
    public static void main(String[] args) {
        Object first = new Object(), second = new Object();
        check(first, TargetGrace.chooseAt(first, null, 1000L));
        check(first, TargetGrace.chooseAt(null, first, 1250L));
        check(second, TargetGrace.chooseAt(second, first, 1300L));
        check(null, TargetGrace.chooseAt(null, first, 1310L));
        check(second, TargetGrace.chooseAt(second, null, 1400L));
        check(null, TargetGrace.chooseAt(null, second, 1800L));
        System.out.println("PASS: target linger, immediate switch and expiry");
    }
    private static void check(Object expected, Object actual) {
        if (expected != actual) throw new AssertionError("unexpected target");
    }
}
