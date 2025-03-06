package com.may.mayojcodesandbox.security;

import java.security.Permission;

public class DenySecurityManager extends SecurityManager {
    //检查所有的权限
    @Override
    public void checkPermission(Permission perm) {
        super.checkPermission(perm);
        throw new SecurityException(String.valueOf(perm));
    }
}
