package com.agent.auth;

import com.agent.auth.entity.Role;
import com.agent.auth.entity.User;
import com.agent.auth.mapper.RoleMapper;
import com.agent.auth.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// 启动时初始化默认管理员账号
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // 创建默认角色
        Role adminRole = roleMapper.selectOne(
            new LambdaQueryWrapper<Role>().eq(Role::getName, "ADMIN"));
        if (adminRole == null) {
            adminRole = new Role();
            adminRole.setName("ADMIN");
            adminRole.setDescription("System administrator");
            roleMapper.insert(adminRole);
        }

        // 创建默认管理员用户
        User existing = userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, "admin"));
        if (existing == null) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setEmail("admin@agent.local");
            admin.setEnabled(true);
            userMapper.insert(admin);
            userMapper.insertUserRole(admin.getId(), adminRole.getId());
            log.info("Default admin user created: admin / admin123");
        }
    }
}
