package com.example.aiteacher.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.aiteacher.Entity.CaptchaVo;
import com.example.aiteacher.Entity.User;
import com.example.aiteacher.Entity.UserVo;
import com.example.aiteacher.Entity.loginVo;
import org.springframework.stereotype.Service;

@Service
public interface loginService extends IService<User> {

    UserVo get(Long userId);

    String login(loginVo log);

    void register(User  user);

    CaptchaVo captchaVo();

}
