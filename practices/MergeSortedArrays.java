import java.util.Arrays;
import java.util.Scanner;


public class MergeSortedArrays {

    // ===== Cách 1: merge ra mảng mới (dùng mảng tạm) =====
    // Độ phức tạp: O(n + m) thời gian, O(n + m) bộ nhớ
    public static int[] merge(int[] a, int[] b) {
        int[] result = new int[a.length + b.length];
        int i = 0, j = 0, k = 0;
        while (k < result.length) {
            if (j >= b.length || (i < a.length && a[i] <= b[j])) {
                result[k++] = a[i++];
            } else {
                result[k++] = b[j++];
            }
        }
        return result;
    }

    // ===== Cách 2: merge không dùng mảng tạm (in-place) =====

    public static int[] mergeInPlace(int[] a, int m, int[] b) {
        int i = m - 1, j = b.length - 1, k = m + b.length - 1;

        while (j >= 0) {
            if (i >= 0 && a[i] > b[j]) a[k--] = a[i--];
            else a[k--] = b[j--];
        }

        return a;
    }


    public static void main(String[] args) {
        int[][][] cases = {
                // { A, B, expected }
                {{1, 3, 5, 7}, {2, 4, 6}, {1, 2, 3, 4, 5, 6, 7}},        // xen kẽ
                {{}, {1, 2}, {1, 2}},                                    // A rỗng
                {{1, 2}, {}, {1, 2}},                                    // B rỗng
                {{}, {}, {}},                                            // cả hai rỗng
                {{1, 1, 2}, {1, 3}, {1, 1, 1, 2, 3}},                    // phần tử trùng
                {{5, 6, 7}, {1, 2, 3}, {1, 2, 3, 5, 6, 7}},              // B nhỏ hơn toàn bộ A
                {{1, 2, 3}, {5, 6, 7}, {1, 2, 3, 5, 6, 7}},              // A nhỏ hơn toàn bộ B
                {{-5, -1, 0}, {-3, 2}, {-5, -3, -1, 0, 2}},              // số âm
                {{2}, {1}, {1, 2}},                                      // mỗi mảng 1 phần tử
                {{4, 4, 4}, {4, 4}, {4, 4, 4, 4, 4}},                    // toàn bộ bằng nhau
                {{1, 10, 20}, {2, 3, 4, 5, 6}, {1, 2, 3, 4, 5, 6, 10, 20}} // độ dài khác nhau
        };

        System.out.println("=== Cach 1: merge(a, b) ===");
        int pass1 = 0;
        for (int[][] c : cases) if (testMerge(c[0], c[1], c[2])) pass1++;
        System.out.println("=> " + pass1 + "/" + cases.length + " PASS");

        System.out.println("\n=== Cach 2: mergeInPlace(a, m, b) ===");
        int pass2 = 0;
        for (int[][] c : cases) if (testMergeInPlace(c[0], c[1], c[2])) pass2++;
        System.out.println("=> " + pass2 + "/" + cases.length + " PASS");


        Scanner sc = new Scanner(System.in);
        System.out.println("\n--- Tu nhap du lieu ---");
        System.out.print("Nhap so phan tu cua A: ");
        int n = sc.nextInt();
        int[] a = new int[n];
        System.out.print("Nhap " + n + " phan tu cua A (tang dan): ");
        for (int i = 0; i < n; i++) a[i] = sc.nextInt();

        System.out.print("Nhap so phan tu cua B: ");
        int m = sc.nextInt();
        int[] b = new int[m];
        System.out.print("Nhap " + m + " phan tu cua B (tang dan): ");
        for (int i = 0; i < m; i++) b[i] = sc.nextInt();

        System.out.println("Cach 1: " + Arrays.toString(merge(a, b)));
        System.out.println("Cach 2: " + Arrays.toString(mergeInPlace(Arrays.copyOf(a, n + m), n, b)));
        sc.close();
    }

    private static boolean testMerge(int[] a, int[] b, int[] expected) {
        int[] actual = merge(a, b);
        return report(a, b, actual, expected);
    }

    private static boolean testMergeInPlace(int[] a, int[] b, int[] expected) {
        // Tạo mảng A có thêm b.length ô trống ở cuối (giá trị 0)
        int[] buffer = Arrays.copyOf(a, a.length + b.length);
        int[] actual = mergeInPlace(buffer, a.length, b);
        return report(a, b, actual, expected);
    }

    private static boolean report(int[] a, int[] b, int[] actual, int[] expected) {
        boolean ok = Arrays.equals(actual, expected);
        System.out.println((ok ? "[PASS] " : "[FAIL] ")
                + Arrays.toString(a) + " + " + Arrays.toString(b)
                + " => " + Arrays.toString(actual)
                + (ok ? "" : "  (mong doi: " + Arrays.toString(expected) + ")"));
        return ok;
    }
}
