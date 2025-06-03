package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;


/**
 * 关注服务接口
 * @author Ace
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注或取消关注用户
     * @param followUserId
     * @param isFollow
     * @return
     */
    Result follow(Long followUserId, Boolean isFollow);


    /**
     * 判断是否关注用户
     * @param followUserId
     * @return
     */
    Result isFollow(Long followUserId);

    /**
     * 查看共同关注
     * @param id
     * @return
     */
    Result followCommons(Long id);
}
