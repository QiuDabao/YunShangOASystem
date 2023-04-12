package com.atguigu.auth;

import com.atguigu.auth.mapper.SysRoleMapper;
import com.atguigu.auth.service.SysRoleService;
import com.atguigu.model.system.SysRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class TestMp {
    @Autowired
    private SysRoleMapper mapper;
    @Test
    public void delete(){
        mapper.deleteById(2);
    }
    @Test
    public void queryByQueryWrapper(){
        //创建QueryWrapper对象
        QueryWrapper<SysRole> wrapper = new QueryWrapper<>();
        wrapper.eq("role_name","管理员");
        //调用mp方法
        List<SysRole> list = mapper.selectList(wrapper);
        System.out.println(list);
    }
    @Test
    public void queryByLambdaQueryWrapper(){
        //创建LambdaQueryWrapper对象
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRole::getRoleName,"管理员");
        //调用mp方法
        List<SysRole> list = mapper.selectList(wrapper);
        System.out.println(list);
    }

    @Autowired
    private SysRoleService service;
    @Test
    public void testServiceMP(){
        System.out.println(service.list());
    }
}
