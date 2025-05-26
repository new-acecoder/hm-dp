package com.hmdp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;


/**
 * 商铺类型服务接口
 * @author Ace
 */
public interface IShopTypeService extends IService<ShopType> {

    /**
     * 查询商铺类型列表
     * @return
     */
    Result queryTypeList() throws JsonProcessingException;
}
