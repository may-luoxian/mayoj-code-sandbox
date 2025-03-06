package com.may.mayojcodesandbox.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.WordTree;
import com.may.mayojcodesandbox.model.ExecuteCodeRequest;
import com.may.mayojcodesandbox.model.ExecuteCodeResponse;
import com.may.mayojcodesandbox.model.ExecuteMessage;
import com.may.mayojcodesandbox.model.JudgeInfo;
import com.may.mayojcodesandbox.service.CodeSandbox;
import com.may.mayojcodesandbox.utils.ProcessUtils;
import com.may.mayojcodesandbox.utils.SaveUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class JavaNativeCodeSandboxOld implements CodeSandbox {

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Solution.java";

    private static final String SECURITY_MANAGER_PATH = "D:\\project\\blog\\mayoj-code-sandbox\\src\\main\\resources\\security";

    private static final String SECURITY_MANAGER_NAME = "MySecurityManager";

    private static final List<String> BLACK_LIST = Arrays.asList("Files", "exec");

    private static final WordTree WORD_TREE = new WordTree();

    static {
        WORD_TREE.addWords(BLACK_LIST);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        JavaNativeCodeSandboxOld javaNativeCodeSandbox = new JavaNativeCodeSandboxOld();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Solution.java", StandardCharsets.UTF_8);
        String mainCode = ResourceUtil.readStr("testCode/unsafeCode/SleepError.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setInputList(Arrays.asList("3 9"));
        executeCodeRequest.setLanguage("java");
        executeCodeRequest.setMainCode(mainCode);
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);
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
        String language = executeCodeRequest.getLanguage();

        // 校验代码，黑白名单
//        FoundWord foundWord = WORD_TREE.matchWord(code);
//        if (foundWord != null) {
//            System.out.println(foundWord.getFoundWord());
//            return null;
//        }

        // 判断tmpCode文件夹是否存在，没有则新建，tmpCode文件夹用于存储执行代码文件
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }

        // 1、拼接代码，把用户代码保存为文件，用户代码隔离存放（UUID作为文件夹名，存储代码文件）
        String codeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String finalCode = this.mergeCode(code, mainCode);
        String codePath = codeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;

        File userCodeFile = FileUtil.writeString(finalCode, codePath, StandardCharsets.UTF_8);

        // 2、编译代码，得到class文件
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        Process compileProcess; // 开辟进程执行代码
        ExecuteMessage executeMessage;
        try {
            compileProcess = Runtime.getRuntime().exec(compileCmd);
            ProcessBuilder processBuilder = new ProcessBuilder("javac", "-encoding", "utf-8", userCodeFile.getAbsolutePath());

            executeMessage = ProcessUtils.runProcess(processBuilder, "编译");
            System.out.println(executeMessage);
        } catch (IOException | InterruptedException e) {
            return getErrorResponse(e);
        }

        // 3、执行代码，得到输出结果
        ArrayList<ExecuteMessage> runExecuteMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
//             String runCmd = String.format("java -Dfile.encoding=UTF-8 -cp %s Main %s", codeParentPath, inputArgs);
            // 启用Java安全管理器
            String runCmd = String.format("java -Dfile.encoding=UTF-8 -cp %s;%s -Djava.security.manager=%s Solution %s", codeParentPath, SECURITY_MANAGER_PATH, SECURITY_MANAGER_NAME, inputArgs);

            Process runProcess;
            try {
//                runProcess = Runtime.getRuntime().exec(runCmd);

                ProcessBuilder processBuilder = new ProcessBuilder("java", "-Xmx256m", "-Dfile.encoding=UTF-8", "-cp", codeParentPath, "Solution", inputArgs);
                ExecuteMessage runExecuteMessage = ProcessUtils.runProcess(processBuilder, "运行");
                runExecuteMessageList.add(runExecuteMessage);
                System.out.println(runExecuteMessage);
            } catch (IOException | InterruptedException e) {
                return getErrorResponse(e);
            }
        }

        // 4、收集整理输出结果
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        ArrayList<String> outputList = new ArrayList<>();
        long maxTime = 0;
        for (ExecuteMessage runExecuteMessage : runExecuteMessageList) {
            String errorMessage = runExecuteMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)) {
                executeCodeResponse.setMessage(errorMessage);
                // 3 用户代码执行错误
                executeCodeResponse.setStatus(3);
                break;
            }
            outputList.add(runExecuteMessage.getMessage());
            Long time = runExecuteMessage.getTime();
            if (time != null) {
                maxTime = Math.max(maxTime, time);
            }

        }
        executeCodeResponse.setOutputList(outputList);
        if (outputList.size() == runExecuteMessageList.size()) {
            executeCodeResponse.setStatus(1);
        }
        // TODO: 待处理功能
        JudgeInfo judgeInfo = new JudgeInfo();
        // judgeInfo.setMessage();
        // judgeInfo.setMemory();
        judgeInfo.setTime(maxTime);
        executeCodeResponse.setJudgeInfo(judgeInfo);

        // 5、文件清理
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(codeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
        }
        return executeCodeResponse;
    }

    /**
     * 获取错误响应
     * 6、错误处理，提升程序健壮性
     *
     * @param e
     * @return
     */
    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        // 2 表示代码沙箱错误
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
