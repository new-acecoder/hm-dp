package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;

/**用户控制器
 * 提供用户相关的操作接口，如发送验证码、登录、登出等功能。
 * 该类使用了 Lombok 的 @Slf4j 注解来简化日志记录。
 * 使用 @RestController 注解表示这是一个 RESTful 风格的控制器，
 * 并使用 @RequestMapping 注解定义了基础路径为 "/user"。
 * @author Ace
 *
 */

@Slf4j
@RestController
@RequestMapping("/user")

public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     * @param phone
     * @param session
     * @return
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // 发送短信验证码并保存验证码
        return userService.sendCode(phone, session);
    }
    /**
     * 登录功能
     * @param loginForm
     * @param session
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        //实现登录功能
        return userService.login(loginForm, session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        // TODO 实现登出功能
        return Result.fail("功能未完成");
    }

    /**
     * 获取当前登录用户信息
     * @return 返回当前用户信息
     */
    @GetMapping("/me")
    public Result me(){
        //获取当前登录的用户并返回
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    /**
     * 查询用户详情
     * @param userId 用户ID
     * @return 返回用户详情
     */
    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }
    /**
     * 根据id查询用户详情
     * @param userId 用户ID
     * @return 返回用户详情
     */
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        // 查询详情
        User user = userService.getById(userId);

        return userService.queryUserById(userId);
    }

}
