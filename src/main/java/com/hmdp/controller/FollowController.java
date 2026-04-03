package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Autowired
    private IFollowService followService;

    /**
     * 关注，取关，通过isFollowed判断
     * @param id
     * @param isFollowed
     * @return
     */
    @PutMapping("/{id}/{isFollowed}")
    public Result follow(@PathVariable("id") Long id, @PathVariable("isFollowed") Boolean isFollow){
        return followService.follow(id,isFollow);
    }

    /**
     * 返回已经关注或未关注
     * @param id
     * @return
     */
    @GetMapping("/or/not/{id}")
    public Result isFollowed(Long id){
        return followService.isFollowed(id);
    }

    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable Long id){
        return followService.followCommons(id);
    }
}
