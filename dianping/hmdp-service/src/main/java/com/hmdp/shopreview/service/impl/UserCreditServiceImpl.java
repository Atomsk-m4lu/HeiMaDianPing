package com.hmdp.shopreview.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hmdp.entity.UserInfo;
import com.hmdp.mapper.UserInfoMapper;
import com.hmdp.shopreview.service.UserCreditService;
import org.springframework.stereotype.Service;

@Service
public class UserCreditServiceImpl implements UserCreditService {

    private final UserInfoMapper userInfoMapper;

    public UserCreditServiceImpl(UserInfoMapper userInfoMapper) {
        this.userInfoMapper = userInfoMapper;
    }

    @Override
    public void addCredits(Long userId, int credits) {
        userInfoMapper.update(null, new UpdateWrapper<UserInfo>()
                .eq("user_id", userId)
                .setSql("credits = IFNULL(credits, 0) + " + credits));
    }
}
