package com.may.mayojcodesandbox.security;

import java.security.Permission;

public class MySecurityManager extends SecurityManager {

    private static final String[] BANNED_PATHS = {"/application"};

    @Override
    public void checkPermission(Permission perm) {
        // 默认情况下，不做任何操作，允许所有权限
    }

    @Override
    public void checkRead(String file) {
        for (String bannedPath : BANNED_PATHS) {
            if (file.contains(bannedPath)) {
                throw new SecurityException("Reading from banned path is not allowed: " + file);
            }
        }
        super.checkRead(file);
    }

    @Override
    public void checkWrite(String file) {
        for (String bannedPath : BANNED_PATHS) {
            if (file.contains(bannedPath)) {
                throw new SecurityException("Reading from banned path is not allowed: " + file);
            }
        }
        super.checkWrite(file);
    }
}
