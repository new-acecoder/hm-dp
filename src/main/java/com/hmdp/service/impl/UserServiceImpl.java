package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;


/**
 * @author Ace
 * @date 2025/5/26
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //手机号格式不正确
            return Result.fail("手机号格式错误");
        }
        //格式正确
        //2.生成验证码
        String code = RandomUtil.randomNumbers(6);
        //3.保存验证码到redis并设置有效时长2分钟  key: phone, value: code
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //4.发送验证码短信
        log.debug("发送短信验证码成功,验证码：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            //手机号格式不正确
            return Result.fail("手机号格式错误");
        }
        //2.从redis获取并校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            //3.验证码不一致
            return Result.fail("验证码错误");
        }
        //4.一致，根据手机号查询用户信息
        User user = query().eq("phone", phone).one();
        //5.判断用户是否存在
        if (user == null) {
            //6.用户不存在，创建新用户并保存
            user = createUserWithPhone(phone);
        }
        //7保存用户信息到redis
        //7.1随机生成token
        String token = UUID.randomUUID().toString(true);
        //7.2将对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //使用BeanUtil将UserDTO转换为Map，并忽略null值,并且把所有字段的值转换为String类型
        Map<String, Object> userMap = BeanUtil.beanToMap(
                userDTO,
                new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor(
                                (fieldName, fieldValue) -> fieldValue.toString()
                        )
        );
        //7.3 保存数据到redis
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        //7.4 设置有效时长
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        //8. 返回token
        return Result.ok(token);
    }

    /**
     * 根据用户ID查询用户信息
     * @param userId 用户ID
     * @return 用户信息
     */
    @Override
    public Result queryUserById(Long userId) {
        User user = getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        // 设置随机昵称
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //2.保存用户信息到数据库
        save(user);
        return user;
    }
}
