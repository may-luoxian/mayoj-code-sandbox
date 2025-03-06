package com.may.mayojcodesandbox.utils;

public class SaveUtil {
    private static final long TIME_OUT = 10000L;


    // 开启守护线程，监控运行线程，若超时则杀死运行线程
    public void saveTimeOut(Process runProcess) {
        new Thread(() -> {
            try {
                Thread.sleep(TIME_OUT);
                runProcess.destroy();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();
    }
}
