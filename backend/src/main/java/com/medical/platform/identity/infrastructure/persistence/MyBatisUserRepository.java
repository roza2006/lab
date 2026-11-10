package com.medical.platform.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.medical.platform.identity.infrastructure.mapper.SysUserMapper;
import com.medical.platform.identity.domain.User;
import com.medical.platform.identity.domain.UserRepository;
import com.medical.platform.identity.domain.UserStatus;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisUserRepository implements UserRepository {

    private final SysUserMapper mapper;

    public MyBatisUserRepository(SysUserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        SysUserPO po = mapper.selectOne(Wrappers.<SysUserPO>lambdaQuery()
                .eq(SysUserPO::getUsername, username.trim()));
        if (po == null) {
            return Optional.empty();
        }
        UserStatus status = po.getStatus() != null && po.getStatus() == 1
                ? UserStatus.ACTIVE : UserStatus.DISABLED;
        return Optional.of(new User(po.getId(), po.getUsername(), po.getPasswordHash(),
                po.getRoleCode(), status));
    }
}
