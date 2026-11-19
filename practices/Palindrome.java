import java.util.Scanner;

/*
 * Bài 4: Kiểm tra một chuỗi có phải là chuỗi đối xứng (palindrome) hay không.
 *   Chuỗi đối xứng: đọc xuôi hay đọc ngược đều giống nhau.
 *   Ví dụ: "radar" => true,  "hello" => false
 *
 * Lưu ý: phân biệt hoa thường ("Radar" => false), khoảng trắng cũng tính là ký tự.
 */
public class Palindrome {

    public static boolean isPalindrome(String s) {
        int left = 0, right = s.length() - 1;
        while (left < right){
            if(s.charAt(left) != s.charAt(right)) return false;
            left ++;
            right --;
        }

        return true;
    }
    public static int countPalindromes(String s) {
        int n = s.length();
        int count = 0;

        for (int center = 0; center < n; center++) {

            int left = center, right = center;
            while (left >= 0 && right < n && s.charAt(left) == s.charAt(right)) {
                count++;
                left--;
                right++;
            }

            left = center;
            right = center + 1;
            while (left >= 0 && right < n && s.charAt(left) == s.charAt(right)) {
                count++;
                left--;
                right++;
            }
        }
        return count;
    }


    public static void main(String[] args) {
        // { chuỗi, kết quả mong đợi }
        Object[][] cases = {
                {"radar", true},        // ví dụ trong đề
                {"hello", false},       // không đối xứng
                {"abba", true},         // độ dài chẵn
                {"abcba", true},        // độ dài lẻ
                {"", true},             // chuỗi rỗng
                {"a", true},            // 1 ký tự
                {"ab", false},          // 2 ký tự khác nhau
                {"aa", true},           // 2 ký tự giống nhau
                {"abca", false},        // chỉ khác ở giữa
                {"Radar", false},       // phân biệt hoa thường
                {"12321", true},        // chữ số
                {"ab ba", true},        // có khoảng trắng ở giữa
                {"ab  a", false}        // khoảng trắng làm lệch
        };

        System.out.println("=== Bai 4: isPalindrome ===");
        int pass = 0;
        for (Object[] c : cases) if (check((String) c[0], isPalindrome((String) c[0]), (boolean) c[1])) pass++;
        System.out.println("=> " + pass + "/" + cases.length + " PASS");

        // { chuỗi, số chuỗi con đối xứng mong đợi }
        Object[][] countCases = {
                {"", 0},                // chuỗi rỗng
                {"a", 1},               // 1 ký tự: "a"
                {"ab", 2},              // "a", "b"
                {"aa", 3},              // "a", "a", "aa"
                {"abc", 3},             // không có chuỗi con dài nào đối xứng
                {"aba", 4},             // "a", "b", "a", "aba"
                {"aaa", 6},             // "a" x3, "aa" x2, "aaa"
                {"abba", 6},            // "a","b","b","a","bb","abba" (tâm chẵn)
                {"aaaa", 10},           // toàn ký tự giống nhau: n*(n+1)/2
                {"abcba", 7},           // "bcb", "abcba" (tâm lẻ)
                {"racecar", 10},        // từ đối xứng dài
                {"banana", 10},         // "ana" x2, "nan", "anana"
                {"Aa", 2},              // phân biệt hoa thường
                {"ab ba", 7}            // khoảng trắng cũng là ký tự
        };

        System.out.println("\n=== countPalindromes ===");
        int countPass = 0;
        for (Object[] c : countCases)
            if (checkCount((String) c[0], countPalindromes((String) c[0]), (int) c[1])) countPass++;
        System.out.println("=> " + countPass + "/" + countCases.length + " PASS");


        Scanner sc = new Scanner(System.in);
        System.out.println("\n--- Tu nhap du lieu ---");
        System.out.print("Nhap chuoi: ");
        String s = sc.nextLine();
        System.out.println("\"" + s + "\" => " + isPalindrome(s));
        sc.close();
    }

    private static boolean check(String s, boolean actual, boolean expected) {
        boolean ok = actual == expected;
        System.out.println((ok ? "[PASS] " : "[FAIL] ")
                + "isPalindrome(\"" + s + "\") => " + actual
                + (ok ? "" : "  (mong doi: " + expected + ")"));
        return ok;
    }

    private static boolean checkCount(String s, int actual, int expected) {
        boolean ok = actual == expected;
        System.out.println((ok ? "[PASS] " : "[FAIL] ")
                + "countPalindromes(\"" + s + "\") => " + actual
                + (ok ? "" : "  (mong doi: " + expected + ")"));
        return ok;
    }
}
