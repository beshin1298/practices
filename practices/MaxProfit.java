import java.util.Arrays;
import java.util.Scanner;

public class MaxProfit {

    public static int maxProfit(int[] prices) {
        if(prices == null || prices.length < 2) return 0;
        int minPrice = prices[0];
        int maxProfit  = 0;
        for(int i = 1; i < prices.length; i ++){
            int profitToday = prices[i] - minPrice;
            if(profitToday > maxProfit) {
                maxProfit = profitToday;
            }
            if(prices[i] < minPrice) minPrice = prices[i];
        }
        return maxProfit;
    }

    // ===== Nâng cao: được mua bán nhiều lần (giữ tối đa 1 đơn vị vàng) =====
    public static int maxProfitMulti(int[] prices) {
       if(prices == null || prices.length < 2) return 0;
       int profit = 0;
       for (int i = 1; i < prices.length; i ++){
           if(prices[i] > prices[i-1]){
               profit += prices[i] - prices[i-1];
           }
       }
       return profit;
    }


    public static void main(String[] args) {
        // { prices, kết quả mua bán 1 lần, kết quả mua bán nhiều lần }
        Object[][] cases = {
                {new int[]{7, 1, 5, 3, 6, 4}, 5, 7},          // ví dụ trong đề
                {new int[]{7, 6, 4, 3, 1}, 0, 0},             // giá giảm liên tục -> không mua
                {new int[]{1, 2, 3, 4, 5}, 4, 4},             // giá tăng liên tục
                {new int[]{}, 0, 0},                          // mảng rỗng
                {new int[]{5}, 0, 0},                         // chỉ 1 ngày
                {new int[]{2, 4, 1}, 2, 2},                   // giá thấp nhất nằm cuối
                {new int[]{3, 3, 3}, 0, 0},                   // giá không đổi
                {new int[]{2, 1, 2, 1, 0, 1, 2}, 2, 3},       // nhiều đáy
                {new int[]{3, 2, 6, 5, 0, 3}, 4, 7},          // đáy mới sau đỉnh
                {new int[]{1, 7, 2, 8}, 7, 12}                // 1 lần: 8-1; nhiều lần: (7-1)+(8-2)
        };

        System.out.println("=== Cach 1: maxProfit (mua ban 1 lan) ===");
        int pass1 = 0;
        for (Object[] c : cases) if (check("maxProfit", (int[]) c[0], maxProfit((int[]) c[0]), (int) c[1])) pass1++;
        System.out.println("=> " + pass1 + "/" + cases.length + " PASS");

        System.out.println("\n=== Nang cao: maxProfitMulti (mua ban nhieu lan) ===");
        int pass2 = 0;
        for (Object[] c : cases) if (check("maxProfitMulti", (int[]) c[0], maxProfitMulti((int[]) c[0]), (int) c[2])) pass2++;
        System.out.println("=> " + pass2 + "/" + cases.length + " PASS");


        Scanner sc = new Scanner(System.in);
        System.out.println("\n--- Tu nhap du lieu ---");
        System.out.print("Nhap so ngay: ");
        int n = sc.nextInt();
        int[] prices = new int[n];
        System.out.print("Nhap " + n + " gia: ");
        for (int i = 0; i < n; i++) prices[i] = sc.nextInt();

        System.out.println("Mua ban 1 lan:     " + maxProfit(prices));
        System.out.println("Mua ban nhieu lan: " + maxProfitMulti(prices));
        sc.close();
    }

    private static boolean check(String name, int[] prices, int actual, int expected) {
        boolean ok = actual == expected;
        System.out.println((ok ? "[PASS] " : "[FAIL] ")
                + name + Arrays.toString(prices) + " => " + actual
                + (ok ? "" : "  (mong doi: " + expected + ")"));
        return ok;
    }
}
