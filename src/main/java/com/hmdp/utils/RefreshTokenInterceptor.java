package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.获取请求头中的token
        String jwtToken = request.getHeader("authorization");
        // 2.判断token是否为空
        if (StrUtil.isBlank(jwtToken)) {
            return true;
        }
        String key = RedisConstants.LOGIN_USER_KEY + jwtToken;
        // 3.判断redis中是否存在token
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(key))) {
            return true;
        }
        Claims claims = JwtUtils.parseJWT(jwtToken);
//        log.info("id: {}", Long.parseLong(claims.get("id", String.class)));
//        log.info("nickName: {}", claims.get("nickName", String.class));
        //获取user对象
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        UserDTO userDTO = BeanUtil.mapToBean(entries, UserDTO.class, false);
        UserHolder.saveUser(userDTO);
        //刷新token有效期
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
