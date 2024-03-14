package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if (!RegexUtils.isCodeInvalid(phone)) {
            return Result.fail("手机号格式不正确");
        }

        //发送短信验证码
        String code = RandomUtil.randomNumbers(6);
        //save code to session
        session.setAttribute("code", code);
        //send code to phone
        log.info("发送验证码：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //login or register
        //校验手机号
        if (!RegexUtils.isCodeInvalid(loginForm.getPhone())){
            return Result.fail("手机号格式不正确");
        }
        String code = loginForm.getCode();
        String sessionCode = (String) session.getAttribute("code");
        if (sessionCode.isEmpty() || !code.equals(sessionCode)) {
            return Result.fail("验证码错误");
        }
        //get user info from db
        User user = query().eq("phone", loginForm.getPhone()).one();
        if (user == null) {
            //create user
            user = createUser(loginForm.getPhone());
        }
        //save user info to session
        session.setAttribute("user", user);
        return Result.ok();
    }

    private User createUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10));
        save(user);
        return user;
    }

}
