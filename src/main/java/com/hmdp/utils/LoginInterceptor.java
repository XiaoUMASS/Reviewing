package com.hmdp.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {

//    private StringRedisTemplate redisTemplate;//key field value都是String存储

//    public LoginInterceptor() {
//        this.redisTemplate = redisTemplate;
//    }

    //    @Override
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        //获取session
//        HttpSession session = request.getSession();
//        //获取session中的用户
//        UserDTO user = (UserDTO) session.getAttribute("user");
//        //判断用户是否存在
//        if(user == null){
//            //不存在，拦截（session携带的信息是无效的）
//            response.setStatus(401);
//            return false;
//        }
//        //存在，保存到 ThreadLocal
//        UserHolder.saveUser(user);
//        //放行
//        return true;
//    }

//    @Override
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        //获取请求头中的token
//        String token = request.getHeader("Authorization");
//        //根据token获取reids中的用户
//        if (StringUtil.isNullOrEmpty(token)) {
//            response.setStatus(401);
//            return false;
//        }
//        //基于token获取redis中的用户
//        Map<Object, Object> userDTOMap = redisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
//        //判断用户是否存在
//        if (userDTOMap.isEmpty()) {
//            //不存在，拦截（请求体携带的信息是无效的）
//            response.setStatus(401);
//            return false;
//        }
//        UserDTO userDTO = new UserDTO();
//        //将查询到的hash数据转为UserDTO对象
//        BeanUtil.fillBeanWithMap(userDTOMap,userDTO,false);
//        //存在，保存到 ThreadLocal
//        UserHolder.saveUser(userDTO);
//        //刷新token有效期
//        redisTemplate.expire(LOGIN_USER_KEY + token,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
//        //放行
//        return true;
//    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        //判断是否需要拦截（ThreadLocal中是否有用户，不论是新用户还是老用户）
        if(UserHolder.getUser() == null){
            //没有，证明RefreshTokenInterceptor不认为该用户是有效的登陆用户，
            //但是依旧放行使得用户可以使用非登陆用户可以使用的功能
            //而登陆用户才可以使用的功能需要经过本拦截器
            response.setStatus(401);
            return false;
        }
        return true;
    }
}
