public final class VerifyYNYEntry {
    public static void main(String[] args) throws Exception {
        Class.forName("com.yny.utils.YNYUtils", false, VerifyYNYEntry.class.getClassLoader());
        System.out.println("YNYUtils verified from " +
                VerifyYNYEntry.class.getClassLoader()
                    .getResource("com/yny/utils/YNYUtils.class"));
    }
}