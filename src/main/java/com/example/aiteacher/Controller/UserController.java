package com.example.aiteacher.Controller;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.aiteacher.Entity.User;
import com.example.aiteacher.Service.loginService;
import com.example.aiteacher.Util.LoginUserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class UserController {

    @Autowired
    private loginService login;


    @RequestMapping("/user")
    public Boolean updateUser(String  teacherName,
                              String subject,
                              String grade,
                              String title,
                              String email,
                              String phone,
                              String avatarUrl,
                              String bio) {
        Long userId = LoginUserHolder.getLoginUser().getUserId();
        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(User::getId, userId);
        wrapper.set(User::getTeacherName, teacherName)
                .set(User::getSubject, subject)
                .set(User::getGrade, grade)
                .set(User::getTitle, title)
                .set(User::getEmail, email)
                .set(User::getPhone, phone)
                .set(User::getAvatarUrl, avatarUrl)
                .set(User::getBio, bio);
        return login.update(wrapper);
    }
}
