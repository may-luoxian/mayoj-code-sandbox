package com.may.mayojcodesandbox;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

public class Main {
    public static void main(String[] args) {
        try {
            // 获取当前目录路径
            Main main = new Main();
            String currentDirectory = main.getCurrentDirectory();
            File currentDir = new File(currentDirectory);
            URL url = currentDir.toURI().toURL();
            URL[] urls = new URL[]{url};

            // 创建一个新的类加载器
            ClassLoader classLoader = new URLClassLoader(urls);

            // 动态加载Solution类
            Class<?> solutionClass = classLoader.loadClass("Solution");

            // 创建Solution类的实例
            Object solutionInstance = solutionClass.getDeclaredConstructor().newInstance();

            // 处理输入参数
            String arg = args[0];
            Integer i = Integer.parseInt(args[1]);

            // 获取getMessage方法
            java.lang.reflect.Method method = solutionClass.getMethod("convert", String.class);

            // 调用Solution类的方法
            String result = (String) method.invoke(solutionInstance, arg, i);

            System.out.println(result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getCurrentDirectory() {
        return this.getClass().getClassLoader().getResource("").getPath();
    }
}
