import java.util.Arrays;
import java.util.Scanner;


public class TwoSum {

    // ===== Bài 3: tìm 2 phần tử có tổng bằng k =====
    public static int[] findTwoSum(int[] arr, int k) {
        if(arr == null || arr.length < 2) return new int[0];
        int left = 0, right = arr.length - 1 ;
        while (left < right){
            int sum = arr[left] + arr[right];
            if(sum == k ) return new int[]{arr[left], arr[right]};
            if(sum > k) right--;
            else left++;
        }

        return new int[0];
    }

    // ===== Nâng cao: tìm 3 phần tử có tổng bằng k =====
    public static int[] findThreeSum(int[] arr, int k) {
        if(arr == null || arr.length < 3) return new int[0];
        Arrays.sort(arr);
        for(int i = 0 ; i < arr.length - 2; i ++){
            int target = k - arr[i];
            int left = i + 1, right = arr.length - 1;
            while(left < right){
                int sum = arr[left] + arr[right];
                if(sum == target) return new int[]{arr[i],arr[left], arr[right]};
                if(sum > target) right--;
                else left++;
            }

        }
        return new int[0];
    }


    public static void main(String[] args) {
        // { arr, k, có đáp án hay không }
        Object[][] twoCases = {
                {new int[]{1, 2, 3, 4, 6}, 6, true},        // ví dụ trong đề -> [2, 4]
                {new int[]{1, 2, 3, 4, 6}, 7, true},        // nhiều đáp án: [1, 6] hoặc [3, 4]
                {new int[]{1, 2, 3, 4, 6}, 100, false},     // không có
                {new int[]{1, 2, 3, 4, 6}, 2, false},       // không được dùng 1 phần tử 2 lần (1+1)
                {new int[]{}, 5, false},                    // mảng rỗng
                {new int[]{5}, 5, false},                   // chỉ 1 phần tử
                {new int[]{3, 3}, 6, true},                 // 2 phần tử trùng giá trị -> [3, 3]
                {new int[]{-5, -2, 0, 3, 7}, 1, true},      // số âm -> [-2, 3]
                {new int[]{-3, -1, 0, 2}, -4, true},        // tổng âm -> [-3, -1]
                {new int[]{1, 2}, 3, true}                  // đúng 2 phần tử
        };

        Object[][] threeCases = {
                {new int[]{1, 2, 3, 4, 6}, 9, true},        // [1, 2, 6] hoặc [2, 3, 4]
                {new int[]{1, 2, 3, 4, 6}, 6, true},        // [1, 2, 3]
                {new int[]{1, 2, 3, 4, 6}, 13, true},       // [3, 4, 6]
                {new int[]{1, 2, 3, 4, 6}, 100, false},     // không có
                {new int[]{1, 2, 3, 4, 6}, 3, false},       // không được dùng lặp (1+1+1)
                {new int[]{}, 0, false},                    // mảng rỗng
                {new int[]{1, 2}, 3, false},                // ít hơn 3 phần tử
                {new int[]{2, 2, 2}, 6, true},              // trùng giá trị -> [2, 2, 2]
                {new int[]{-4, -1, 0, 1, 2}, 0, true},      // số âm -> [-1, 0, 1]
                {new int[]{-5, -3, 1, 8}, 0, true}          // [-5, -3, 8]
        };

        System.out.println("=== Bai 3: findTwoSum ===");
        int pass1 = 0;
        for (Object[] c : twoCases) {
            int[] arr = (int[]) c[0];
            int k = (int) c[1];
            if (check("findTwoSum", arr, k, findTwoSum(arr, k), 2, (boolean) c[2])) pass1++;
        }
        System.out.println("=> " + pass1 + "/" + twoCases.length + " PASS");

        System.out.println("\n=== Nang cao: findThreeSum ===");
        int pass2 = 0;
        for (Object[] c : threeCases) {
            int[] arr = (int[]) c[0];
            int k = (int) c[1];
            if (check("findThreeSum", arr, k, findThreeSum(arr, k), 3, (boolean) c[2])) pass2++;
        }
        System.out.println("=> " + pass2 + "/" + threeCases.length + " PASS");


        Scanner sc = new Scanner(System.in);
        System.out.println("\n--- Tu nhap du lieu ---");
        System.out.print("Nhap so phan tu: ");
        int n = sc.nextInt();
        int[] arr = new int[n];
        System.out.print("Nhap " + n + " phan tu (tang dan): ");
        for (int i = 0; i < n; i++) arr[i] = sc.nextInt();
        System.out.print("Nhap k: ");
        int k = sc.nextInt();

        System.out.println("2 phan tu: " + Arrays.toString(findTwoSum(arr, k)));
        System.out.println("3 phan tu: " + Arrays.toString(findThreeSum(arr, k)));
        sc.close();
    }

    // Kiểm tra kết quả: đúng số lượng phần tử, tổng bằng k, và các phần tử thật sự có trong arr
    // (mỗi vị trí chỉ dùng 1 lần). Nếu không có đáp án thì phải trả về mảng rỗng.
    private static boolean check(String name, int[] arr, int k, int[] actual, int size, boolean hasAnswer) {
        boolean ok;
        String expectText;
        if (!hasAnswer) {
            ok = actual != null && actual.length == 0;
            expectText = "[] (khong co dap an)";
        } else {
            ok = actual != null && actual.length == size
                    && Arrays.stream(actual).sum() == k
                    && takenFrom(arr, actual);
            expectText = size + " phan tu co trong mang, tong = " + k;
        }
        System.out.println((ok ? "[PASS] " : "[FAIL] ")
                + name + Arrays.toString(arr) + ", k=" + k + " => " + Arrays.toString(actual)
                + (ok ? "" : "  (mong doi: " + expectText + ")"));
        return ok;
    }

    // Mỗi phần tử trong picked phải lấy được từ một vị trí khác nhau trong arr
    private static boolean takenFrom(int[] arr, int[] picked) {
        boolean[] used = new boolean[arr.length];
        for (int v : picked) {
            boolean found = false;
            for (int i = 0; i < arr.length; i++) {
                if (!used[i] && arr[i] == v) {
                    used[i] = true;
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }
}
