package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 关注控制器
 * @author Ace
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    /**
     * 关注或取消关注用户
     * @param followUserId 被关注用户ID
     * @param isFollow 是否关注
     * @return 结果
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId,@PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(followUserId, isFollow);
    }

    /**
     * 判断是否关注用户
     * @param followUserId
     * @return
     */
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserId) {
        return followService.isFollow(followUserId);
    }

    /**
     * 查看共同关注
     * @param id 商户ID
     * @return 结果
     */
    @GetMapping("common/{id}")
    public Result followCommons(@PathVariable("id") Long id) {
        return followService.followCommons(id);
    }

}
