package com.example.aiteacher.Util;


import com.example.aiteacher.Entity.loginUser;
import lombok.Data;

@Data
public class LoginUserHolder {
    public static ThreadLocal<loginUser> loginUserThreadLocal = new ThreadLocal<>();
    public static void setLoginUser(loginUser user){
        loginUserThreadLocal.set(user);
    }
    public static loginUser getLoginUser(){
        return loginUserThreadLocal.get();
    }
    public static void clearLoginUser(){
        loginUserThreadLocal.remove();
    }
}
