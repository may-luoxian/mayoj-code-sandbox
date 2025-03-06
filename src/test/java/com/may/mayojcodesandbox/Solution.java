package com.may.mayojcodesandbox;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;


public class Solution {
    Map<Character, Integer> symbolValues = new HashMap<Character, Integer>() {{
        put('I', 1);
        put('V', 5);
        put('X', 10);
        put('L', 50);
        put('C', 100);
        put('D', 500);
        put('M', 1000);
    }};
//    public String isPalindrome() {
//        String userDir = System.getProperty("user.dir");
//        String filePath = userDir + File.separator + "src/main/resources/application.yml";
//        List<String> allLines = null;
//        try {
//            allLines = Files.readAllLines(Paths.get(filePath));
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//        return String.join("\n", allLines);

        // 内存溢出
//        List<byte[]> bytes = new ArrayList<>();
//        while (true) {
//            bytes.add(new byte[10000]);
//        }
//        String str = null;
//        if (str.length() == 0) {
//            return true;
//        }

//        if (x < 0 || (x % 10 == 0 && x != 0)) {
//            return false;
//        }
//
//        int revertedNumber = 0;
//        while (x > revertedNumber) {
//            revertedNumber = revertedNumber * 10 + x % 10;
//            x /= 10;
//        }
//        return x == revertedNumber || x == revertedNumber / 10;
//    }

//    public static void main(String[] args) {
//        Solution solution = new Solution();
//        String palindrome = solution.isPalindrome();
//        System.out.println(palindrome);
//    }


    public int romanToInt(String s) {
        int ans = 0;
        int n = s.length();
        for (int i = 0; i < n; ++i) {
            int value = symbolValues.get(s.charAt(i));
            if (i < n - 1 && value < symbolValues.get(s.charAt(i + 1))) {
                ans -= value;
            } else {
                ans += value;
            }
        }
        return ans;
    }

    public static void main(String[] args) {
        Solution solution = new Solution();
        String arg = args[0];
        int romanToInt = solution.romanToInt(arg);
        System.out.println(romanToInt);
    }
}
