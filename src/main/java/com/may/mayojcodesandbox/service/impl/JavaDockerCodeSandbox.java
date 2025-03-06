package com.may.mayojcodesandbox.service.impl;

import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.may.mayojcodesandbox.model.ExecuteCodeRequest;
import com.may.mayojcodesandbox.model.ExecuteCodeResponse;
import com.may.mayojcodesandbox.model.ExecuteMessage;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Docker 安全沙箱实现
 */
@Service
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate {

    private static final Boolean FIRST_INIT = false;

    private static final Long TIME_OUT = 5000L;

    @Override
    public List<ExecuteMessage> runFile(String codeParentPath, List<String> inputList) {
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
        return runExecuteMessageList;
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }
}
