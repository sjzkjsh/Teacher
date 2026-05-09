package com.example.aiteacher.LoginController;

import com.example.aiteacher.Entity.*;
import com.example.aiteacher.Service.loginService;
import com.example.aiteacher.Util.LoginUserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



@RequestMapping
@RestController
public class LoginController {

    @Autowired
    private loginService login;

    //获取验证码
    @RequestMapping("/captchaVo")
    public Result<CaptchaVo> captchaVo(){
        return Result.success(login.captchaVo());
    }
    //登录
    @RequestMapping("/login")
    public Result<String> login(loginVo log){
        return Result.success(login.login(log));
    }
    //注册
    @RequestMapping("/register")
    public Result<String>  register(User  user){
        login.register(user);
        return Result.success("注册成功");
    }
    //获取用户信息
    @RequestMapping("/userInfo")
    public Result<UserVo>  userInfo(){

        Long userId = LoginUserHolder.getLoginUser().getUserId();
        UserVo userVo = login.get(userId);
        return Result.success(userVo);
    }

}
