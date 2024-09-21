package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;


@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.验证手机号格式
        boolean invalid = RegexUtils.isPhoneInvalid(phone);
        //2.如果不符合，返回错误信息
        if (invalid) {
            return Result.fail("手机号格式错误");
        }
        //3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //4.保存验证码到redis
        //设置键值对的有效期
        redisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
//        redisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code);
        //5.发送验证码
        log.info("发送短信验证码成功:" + code);
        //返回ok
        return Result.ok();
    }

//    @Override
//    public Result login(LoginFormDTO loginForm, HttpSession session) {
//        //校验手机号和验证码
//        boolean invalid = RegexUtils.isPhoneInvalid(loginForm.getPhone());
//        if (invalid) {
//            return Result.fail("手机号格式错误");
//        }
//        //若验证码正确（和code接口返回的一致）
////        Object cacheCode = session.getAttribute("code");//正确的验证码
//        String code = loginForm.getCode();//用户输入的验证码
//        //根据手机号查询用户
//        if (cacheCode == null) {//session中没有验证码，证明用户从来没有请求过验证码
//            return Result.fail("请先申请验证码");
//        } else if (!cacheCode.toString().equals(code)) {//验证码不匹配
//            return Result.fail("验证码错误");
//        }
//        //校验通过，检查用户是否在数据库中存在（新老用户）
//        User user = query().eq("phone", loginForm.getPhone()).one();
//        //用户不存在，创建新用户并保存到数据库
//        if (user == null) {
//            user = creatUserWithPhone(loginForm.getPhone());
//        }
//        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
//        //不论存不存在，用户信息保存到session中
//        session.setAttribute("user", userDTO);
//        return Result.ok();
//    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        //校验手机号和验证码
        boolean invalid = RegexUtils.isPhoneInvalid(phone);
        if (invalid) {
            return Result.fail("手机号格式错误");
        }
        //若验证码正确（和code接口返回的一致）
//        Object cacheCode = session.getAttribute("code");//正确的验证码
        String code = loginForm.getCode();//用户输入的验证码
        String cacheCode = redisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        //根据手机号查询用户
        if (cacheCode == null) {//session中没有验证码，证明用户从来没有请求过验证码
            return Result.fail("请先申请验证码");
        } else if (!cacheCode.equals(code)) {//验证码不匹配
            return Result.fail("验证码错误");
        }
        //校验通过，检查用户是否在数据库中存在（新老用户）
        User user = query().eq("phone", phone).one();
        //用户不存在，创建新用户并保存到数据库
        if (user == null) {
            user = creatUserWithPhone(phone);
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //不论存不存在，用户信息保存到redis中
        //随机生成token，作为登陆令牌
        String token = UUID.randomUUID().toString();
        //将User对象转为Hash存储
        //将userDTO转换为hash
        //注意将userDTO中的非String类型的值转为String类型后再存储到map中
        HashMap<String, String> userDTOMap = new HashMap<>();
        Map<String, Object> map = BeanUtil.beanToMap(userDTO);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            userDTOMap.put(entry.getKey(), entry.getValue().toString());
        }
//        Map<String, Object> map = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create().setFieldValueEditor());
        redisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userDTOMap);
        //设置token有效期
        redisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        //返回token
        log.info("返回token");
        return Result.ok(token);
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
