package com.hmdp.controller;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.hmdp.dto.Result;
import com.hmdp.service.IShopTypeService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



/**
 * 商铺类型控制器
 * @author Ace
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {

    @Resource
    private IShopTypeService typeService;

    /**
     * 查询商铺类型列表
     * @return 商铺类型列表
     * @throws JsonProcessingException 异常
     */
    @GetMapping("list")
    public Result queryTypeList() throws JsonProcessingException {
        return typeService.queryTypeList();
    }
}
