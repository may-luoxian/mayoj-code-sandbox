package com.may.mayojcodesandbox.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.may.mayojcodesandbox.model.ExecuteCodeRequest;
import com.may.mayojcodesandbox.model.ExecuteCodeResponse;
import com.may.mayojcodesandbox.model.ExecuteMessage;
import com.may.mayojcodesandbox.model.JudgeInfo;
import com.may.mayojcodesandbox.service.CodeSandbox;
import com.may.mayojcodesandbox.utils.ProcessUtils;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class JavaDockerCodeSandboxOld implements CodeSandbox {

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final String SECURITY_MANAGER_PATH = "D:\\project\\blog\\mayoj-code-sandbox\\src\\main\\resources\\security";

    private static final String SECURITY_MANAGER_NAME = "MySecurityManager";

    private static final Boolean FIRST_INIT = false;

    private static final Long TIME_OUT = 5000L;

    public static void main(String[] args) throws IOException, InterruptedException {
        JavaDockerCodeSandboxOld javaDockerCodeSandbox = new JavaDockerCodeSandboxOld();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
//        String code = ResourceUtil.readStr("testCode/unsafeCode/RunFileError.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setInputList(Arrays.asList("3 9"));
        executeCodeRequest.setLanguage("java");
        ExecuteCodeResponse executeCodeResponse = javaDockerCodeSandbox.executeCode(executeCodeRequest);
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
        String language = executeCodeRequest.getLanguage();



        // 判断tmpCode文件夹是否存在，没有则新建，tmpCode文件夹用于存储执行代码文件
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }

        // 1、把用户代码保存为文件，用户代码隔离存放（UUID作为文件夹名，存储代码文件）
        String codeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String codePath = codeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, codePath, StandardCharsets.UTF_8);

        // 2、编译代码，得到class文件
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
//        Process compileProcess; // 开辟进程执行代码
        ExecuteMessage executeMessage;
        try {
//            compileProcess = Runtime.getRuntime().exec(compileCmd);
            ProcessBuilder processBuilder = new ProcessBuilder("javac", "-encoding", "utf-8", userCodeFile.getAbsolutePath());

            executeMessage = ProcessUtils.runProcess(processBuilder, "编译");
            System.out.println(executeMessage);
        } catch (IOException | InterruptedException e) {
            return getErrorResponse(e);
        }

        // 3、创建容器，把文件复制到容器内
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        String image = "openjdk:8-alpine";
        if (FIRST_INIT) {
            // 拉取Docker镜像
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载镜像：" + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd.exec(pullImageResultCallback).awaitCompletion();
            } catch (InterruptedException e) {
                System.out.println("拉取镜像异常");
                throw new RuntimeException(e);
            }
            System.out.println("镜像下载完成");
        }

        // 创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        // 构建容器映射
        HostConfig hostConfig = new HostConfig();
        hostConfig.setBinds(new Bind(codeParentPath, new Volume("/app")));
        hostConfig.withMemory(100 * 1000 * 1000L); // 限制容器内存 100M
        hostConfig.withCpuCount(1L);
        CreateContainerResponse containerResponse = containerCmd
                .withHostConfig(hostConfig) // 将宿主机中的代码文件映射到容器/app目录下
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true) // 开启容器可以与本地终端交互
                .withTty(true) // 创建一个交互终端
                .exec();
        System.out.println("Docker容器" + containerResponse);

        String containerId = containerResponse.getId();

        // 启动容器
        dockerClient.startContainerCmd(containerId).exec();


        ArrayList<ExecuteMessage> runExecuteMessageList = new ArrayList<>();
        for (String input : inputList) {
            // 初始化stopWatch，并开始计时
            StopWatch stopWatch = new StopWatch();

            String[] strings = input.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, strings);
            // 创建执行命令
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient
                    .execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();
            System.out.println("创建执行命令" + execCreateCmdResponse);


            ExecuteMessage message = new ExecuteMessage();
            final String[] payload = {null};
            final String[] errPayload = {null};
            final boolean[] isTimeout = {true};
            String execCreateCmdResponseId = execCreateCmdResponse.getId();
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                @Override
                public void onComplete() {
                    isTimeout[0] = false;
                    super.onComplete();
                }

                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if (streamType.STDERR.equals(streamType)) {
                        errPayload[0] = new String(frame.getPayload());
                        System.out.println("输出错误结果：" + errPayload);
                    } else {
                        payload[0] = new String(frame.getPayload());
                        System.out.println("输出正确结果：" + payload);
                    }
                    super.onNext(frame);
                }
            };

            // 获取程序运行占用的内存
            final long[] maxMemory = {0L};
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            ResultCallback<Statistics> statisticsResultCallback = new ResultCallback<Statistics>() {
                @Override
                public void onNext(Statistics statistics) {
                    System.out.println("内存占用：" + statistics.getMemoryStats().getUsage());
                    maxMemory[0] = Math.max(statistics.getMemoryStats().getUsage(), maxMemory[0]);
                }

                @Override
                public void close() throws IOException {

                }

                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }
            };
            statsCmd.exec(statisticsResultCallback);

            try {
                stopWatch.start();
                dockerClient
                        .execStartCmd(execCreateCmdResponseId)
                        .exec(execStartResultCallback)
                        .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
                stopWatch.stop();
                statsCmd.close();
            } catch (InterruptedException e) {
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }

            // 设置程序执行用时
            message.setTime(stopWatch.getLastTaskTimeMillis());
            message.setErrorMessage(errPayload[0]);
            message.setMessage(payload[0]);
            message.setMemory(maxMemory[0]);
            runExecuteMessageList.add(message);
        }

        // 4、收集整理输出结果
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        ArrayList<String> outputList = new ArrayList<>();
        long maxTime = 0L;
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
//         judgeInfo.setMessage();
//         judgeInfo.setMemory();
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
}
