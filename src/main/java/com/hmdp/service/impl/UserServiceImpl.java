package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;


@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.验证手机号格式
        boolean invalid = RegexUtils.isPhoneInvalid(phone);
        //2.如果不符合，返回错误信息
        if (invalid) {
            return Result.fail("手机号格式错误");
        }
        //3。符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //4。保存验证码到session
        session.setAttribute("code", code);
        //5。发送验证码
        log.info("发送短信验证码成功:"+code);
        //返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号和验证码
        boolean invalid = RegexUtils.isPhoneInvalid(loginForm.getPhone());
        if (invalid) {
            return Result.fail("手机号格式错误");
        }
        //若验证码正确（和code接口返回的一致）
        Object cacheCode = session.getAttribute("code");//正确的验证码
        String code = loginForm.getCode();//用户输入的验证码
        //根据手机号查询用户
        if (cacheCode == null) {//session中没有验证码，证明用户从来没有请求过验证码
            return Result.fail("请先申请验证码");
        } else if (!cacheCode.toString().equals(code)) {//验证码不匹配
            return Result.fail("验证码错误");
        }
        //校验通过，检查用户是否在数据库中存在（新老用户）
        User user = query().eq("phone", loginForm.getPhone()).one();
        //用户不存在，创建新用户并保存到数据库
        if (user == null) {
            user = creatUserWithPhone(loginForm.getPhone());
        }
        //不论存不存在，用户信息保存到session中
        session.setAttribute("user", user);
        return Result.ok();
    }

    private User creatUserWithPhone(String phone) {
        //创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //插入数据
        save(user);
        return user;
    }
}
