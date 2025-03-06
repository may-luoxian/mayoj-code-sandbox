package com.may.mayojcodesandbox.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.may.mayojcodesandbox.model.ExecuteCodeRequest;
import com.may.mayojcodesandbox.model.ExecuteCodeResponse;
import com.may.mayojcodesandbox.model.ExecuteMessage;
import com.may.mayojcodesandbox.model.JudgeInfo;
import com.may.mayojcodesandbox.service.CodeSandbox;
import com.may.mayojcodesandbox.utils.ProcessUtils;
import com.may.mayojcodesandbox.utils.SaveUtil;
import lombok.extern.slf4j.Slf4j;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 安全沙箱 模板类
 */
@Slf4j
public abstract class JavaCodeSandboxTemplate implements CodeSandbox {

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Solution.java";

    private static final String SECURITY_MANAGER_PATH = "D:\\project\\blog\\mayoj-code-sandbox\\src\\main\\resources\\security";

    private static final String SECURITY_MANAGER_NAME = "MySecurityManager";

    private static final String USER_DIR = System.getProperty("user.dir");

    private static final String GLOBAL_CODE_PATH_NAME = USER_DIR + File.separator + GLOBAL_CODE_DIR_NAME;

    /**
     * 1、拼接代码，把用户代码保存为文件，用户代码隔离存放（UUID作为文件夹名，存储代码文件）
     * 判断tmpCode文件夹是否存在，没有则新建，tmpCode文件夹用于存储执行代码文件
     *
     * @param code 用户代码
     * @return
     */
    public File saveCodeToFile(String code, String mainCode, String codeParentPath) {
        if (!FileUtil.exist(GLOBAL_CODE_PATH_NAME)) {
            FileUtil.mkdir(GLOBAL_CODE_PATH_NAME);
        }
        String finalCode = this.mergeCode(code, mainCode);
        String codePath = codeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(finalCode, codePath, StandardCharsets.UTF_8);
        return userCodeFile;
    }

    /**
     * 2、编译代码，得到class文件
     *
     * @param userCodeFile 用户代码文件
     * @return
     */
    public ExecuteMessage compileFile(File userCodeFile) {
//        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        ExecuteMessage executeMessage;
//        Process compileProcess;
        try {
//            compileProcess = Runtime.getRuntime().exec(compileCmd);

            ProcessBuilder processBuilder = new ProcessBuilder("javac", "-encoding", "utf-8", userCodeFile.getAbsolutePath());

            executeMessage = ProcessUtils.runProcess(processBuilder, "编译");
//            if (executeMessage.getExitValue() != 0) {
//                throw new RuntimeException("编译错误");
//            }
            return executeMessage;
        } catch (IOException | InterruptedException e) {
//            return getErrorResponse(e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 3、执行代码，得到输出结果
     *
     * @param codeParentPath 用户代码文件父目录
     * @param inputList      输入参数列表
     * @return
     */
    public List<ExecuteMessage> runFile(String codeParentPath, List<String> inputList) {
        ArrayList<ExecuteMessage> runExecuteMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
//            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Solution %s", codeParentPath, inputArgs);
            // 启用Java安全管理器
//            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s;%s -Djava.security.manager=%s Solution %s", codeParentPath, SECURITY_MANAGER_PATH, SECURITY_MANAGER_NAME, inputArgs);
//            Process runProcess;
            try {
//                runProcess = Runtime.getRuntime().exec(runCmd);
//                ProcessBuilder processBuilder = new ProcessBuilder("java", "-Xmx256m", "-Dfile.encoding=UTF-8", "-cp", codeParentPath, "Solution", inputArgs);
                ProcessBuilder processBuilder = new ProcessBuilder(
                        "java",
                        "-Xmx256m",
                        "-Dfile.encoding=UTF-8",
                        "-cp",
                        codeParentPath + ";" + SECURITY_MANAGER_PATH,
                        "-Djava.security.manager=" + SECURITY_MANAGER_NAME,
                        "Solution"
                );
                processBuilder.command().addAll(Arrays.asList(inputArgs));
                ExecuteMessage runExecuteMessage = ProcessUtils.runProcess(processBuilder, "运行");

                runExecuteMessageList.add(runExecuteMessage);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("执行错误", e);
            }
        }
        return runExecuteMessageList;
    }

    /**
     * 4、获取输出结果
     *
     * @param runExecuteMessageList 代码执行结果列表
     * @return
     */
    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> runExecuteMessageList) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        ArrayList<String> outputList = new ArrayList<>();
        long maxTime = 0L;
        long maxMemory = 0L;
        for (ExecuteMessage runExecuteMessage : runExecuteMessageList) {
            String errorMessage = runExecuteMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)) {
//                executeCodeResponse.setMessage(errorMessage);
                // 3 用户代码执行错误
                executeCodeResponse.setStatus(3);
                outputList.add(runExecuteMessage.getErrorMessage());
            } else {
                outputList.add(runExecuteMessage.getMessage());
            }
            Long time = runExecuteMessage.getTime();
            if (time != null) {
                maxTime = Math.max(maxTime, time);
            }
            Long memory = runExecuteMessage.getMemory();
            if (memory != null) {
                maxMemory = Math.max(maxMemory, memory);
            }
        }

        // 如果代码不存在错误，对比输入输出数量是否一致，一致则执行成功
        if (executeCodeResponse.getStatus() == null && outputList.size() == runExecuteMessageList.size()) {
            // 1 输入和输出数量一致，代码执行成功
            executeCodeResponse.setStatus(1);
        }

        executeCodeResponse.setOutputList(outputList);
        // TODO: 待处理功能
        JudgeInfo judgeInfo = new JudgeInfo();
        // judgeInfo.setMessage();
        judgeInfo.setMemory(maxMemory);
        judgeInfo.setTime(maxTime);
        executeCodeResponse.setJudgeInfo(judgeInfo);
        return executeCodeResponse;
    }

    /**
     * 5、删除文件
     *
     * @param userCodeFile
     * @return
     */
    public boolean deleteFile(File userCodeFile) {
        if (userCodeFile.getParentFile() != null) {
            String codeParentPath = userCodeFile.getParentFile().getAbsolutePath();
            boolean del = FileUtil.del(codeParentPath);
            return del;
        }
        return true;
    }

    /**
     * 执行代码沙箱
     *
     * @param executeCodeRequest
     * @return
     */
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String mainCode = executeCodeRequest.getMainCode();
//        String language = executeCodeRequest.getLanguage();

        String codeParentPath = GLOBAL_CODE_PATH_NAME + File.separator + UUID.randomUUID();

        // 1、把用户代码保存为文件
        File userCodeFile = saveCodeToFile(code, mainCode, codeParentPath);

        // 2、编译代码，得到class文件
        ExecuteMessage executeMessage = compileFile(userCodeFile);
        if (executeMessage.getExitValue() != 0) {
            return getErrorResponse(executeMessage);
        }

        // 3、执行代码，得到输出结果
        List<ExecuteMessage> runExecuteMessageList = runFile(codeParentPath, inputList);

        // 4、收集整理输出结果
        ExecuteCodeResponse executeCodeResponse = getOutputResponse(runExecuteMessageList);

        // 5、文件清理
        boolean b = deleteFile(userCodeFile);
        if (!b) {
            log.error("deleteFile error, userCodeFilePath:", userCodeFile.getAbsolutePath());
        }

        return executeCodeResponse;
    }

    /**
     * 获取错误响应
     * 6、错误处理
     */
    private ExecuteCodeResponse getErrorResponse(ExecuteMessage executeMessage) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(executeMessage.getErrorMessage());
        // 2 表示编译错误
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }

    /**
     * 拼接主方法代码和用户代码
     */
    private String mergeCode(String code, String mainCode) {
        int pos = code.lastIndexOf("}");
        if (pos == -1) {
            return null;
        }
        return code.substring(0, pos) + mainCode + "}";
    }
}
