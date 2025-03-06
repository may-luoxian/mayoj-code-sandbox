package com.may.mayojcodesandbox.utils;

import com.may.mayojcodesandbox.model.ExecuteMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.StopWatch;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 进程工具类
 */
public class ProcessUtils {

    /**
     * 执行进程并获取执行进程的信息
     *
     * @param processBuilder
     * @return
     * @throws InterruptedException
     * @throws IOException
     */
    public static ExecuteMessage runProcess(ProcessBuilder processBuilder, String opName) throws InterruptedException, IOException {
        ExecuteMessage executeMessage = new ExecuteMessage();

        Runtime r = Runtime.getRuntime();
        r.gc(); //计算前先垃圾回收一次
        long startMem = r.totalMemory();

        // 初始化stopWatch，并开始计时
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        Process process = processBuilder.start();

        // 开启守护线程，10秒销毁进程
        SaveUtil saveUtil = new SaveUtil();
        saveUtil.saveTimeOut(process);

        int exitValue = process.waitFor(); // 直到进程完成，返回退出码，根据退出码判断进程是否正常终止

        // 结束计时
        stopWatch.stop();

        // 结束内存监控
        long endMem = r.freeMemory();
        long memory = (startMem - endMem) / 1024;

        // 设置程序执行用时
        long lastTaskTimeMillis = stopWatch.getLastTaskTimeMillis();
        executeMessage.setTime(lastTaskTimeMillis);

        // 设置程序内存消耗
        executeMessage.setMemory(memory);

        // 设置状态码
        executeMessage.setExitValue(exitValue);

        if (exitValue == 0) { // 正常退出
            System.out.println(opName + "成功");
            // 获取正常输入流（程序运行结果）
            try (InputStreamReader inputStreamReader = new InputStreamReader(process.getInputStream(), "utf-8");
                 BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
                List<String> outputStrList = new ArrayList<>();

                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    outputStrList.add(compileOutputLine);
                }
                executeMessage.setMessage(StringUtils.join(outputStrList, "\n"));
            }
        } else { // 异常退出
            System.out.println(opName + "错误，错误码：" + exitValue);
            // 获取正常输入流（程序运行结果）
            try (InputStreamReader inputStreamReader = new InputStreamReader(process.getInputStream(), "utf-8");
                 BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
                List<String> outputStrList = new ArrayList<>();
                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    outputStrList.add(compileOutputLine);
                }
                executeMessage.setMessage(StringUtils.join(outputStrList, "\n"));
            }

            // 获取错误输入流（程序错误原因）
            try (InputStreamReader errorInputStreamReader = new InputStreamReader(process.getErrorStream(), "utf-8");
                 BufferedReader errorBufferedReader = new BufferedReader(errorInputStreamReader)) {
                List<String> errorOutputStrList = new ArrayList<>();

                String errorCompileOutputLine;
                while ((errorCompileOutputLine = errorBufferedReader.readLine()) != null) {
                    errorOutputStrList.add(errorCompileOutputLine);
                }
                executeMessage.setErrorMessage(StringUtils.join(errorOutputStrList, "\n"));
            }
        }

        return executeMessage;
    }
}
