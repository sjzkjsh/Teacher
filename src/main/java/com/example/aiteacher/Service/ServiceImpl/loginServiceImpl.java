package com.example.aiteacher.Service.ServiceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aiteacher.Entity.*;
import com.example.aiteacher.Mapper.loginMapper;
import com.example.aiteacher.Service.loginService;
import com.example.aiteacher.Util.JwtUtil;
import com.wf.captcha.SpecCaptcha;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
@Service
public class loginServiceImpl extends ServiceImpl<loginMapper, User> implements loginService {
    @Autowired
    private loginMapper loginMapper;
    @Autowired
    private StringRedisTemplate tamplate;

    @Override
    public UserVo get(Long userId) {
        User user = loginMapper.selectById(userId);
        UserVo userVo = new UserVo();
        userVo.setName(user.getTeacherName());
        userVo.setAvatarUrl(user.getAvatarUrl());
        return userVo;
    }

    @Override
    public String login(loginVo loginVo) {
        if(!StringUtils.hasText(loginVo.getCaptchaCode())){
            throw new RuntimeException("验证码不能为空");
        }
        String s= tamplate.opsForValue().get(loginVo.getCaptchaKey());//获取redis中的验证码
        if(s== null){
            throw new RuntimeException("验证码已过期");
        }
        if(!s.equals(loginVo.getCaptchaCode())){
            throw new RuntimeException("验证码错误");
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername,loginVo.getUsername());
        User user = loginMapper.selectOne(wrapper);
        if(user== null){
            throw new RuntimeException("用户不存在");
        }
        if(!user.getPassword().equals(loginVo.getPassword())){
            throw new RuntimeException("密码错误");
        }

        return JwtUtil.createToken(user.getId(),user.getUsername());

    }

    @Override
    public void register(User  user) {
        if(!StringUtils.hasText(user.getUsername())){
            throw new RuntimeException("用户名不能为空");
        }
        if(!StringUtils.hasText(user.getPassword())){
            throw new RuntimeException("密码不能为空");
        }
        User user1 = new User();
        user1.setUsername(user.getUsername());
        user1.setPassword(user.getPassword());
        user1.setPhone(user.getPhone());
        user1.setEmail(user.getEmail());
        user1.setCreateTime(LocalDateTime.now());
        user1.setStatus(1);
        loginMapper.insert(user1);
        JwtUtil.createToken(user1.getId(),user1.getUsername());
    }



    @Override
    public CaptchaVo captchaVo() {
        //创建验证码图片
        SpecCaptcha specCaptcha=new SpecCaptcha(130,48,4);
        specCaptcha.setCharType(SpecCaptcha.TYPE_ONLY_NUMBER);
        String code=specCaptcha.text().toLowerCase();//忽略大小写
        String key = RedisConstant.ADMIN_LOGIN_PREFIX + UUID.randomUUID();//根据规则拼接key
        tamplate.opsForValue().set(key, code,
                RedisConstant.ADMIN_LOGIN_CAPTCHA_TTL_SEC,
                TimeUnit.SECONDS);//存入redis中，带有过期时间。
        //构造vo并返回。
        String image = specCaptcha.toBase64();//对图片进行base64编码
        return new CaptchaVo(image, key);//返回image+key
    }
}
