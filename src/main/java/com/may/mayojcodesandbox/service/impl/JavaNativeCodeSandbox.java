package com.may.mayojcodesandbox.service.impl;

import com.may.mayojcodesandbox.model.ExecuteCodeRequest;
import com.may.mayojcodesandbox.model.ExecuteCodeResponse;
import org.springframework.stereotype.Service;

/**
 * Java 原生代码沙箱实现（直接复用模板）
 */
@Service
public class JavaNativeCodeSandbox extends JavaCodeSandboxTemplate {
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }
}
