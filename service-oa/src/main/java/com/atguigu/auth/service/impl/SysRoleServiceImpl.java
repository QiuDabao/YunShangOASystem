package com.atguigu.auth.service.impl;

import com.atguigu.auth.mapper.SysRoleMapper;
import com.atguigu.auth.service.SysRoleService;
import com.atguigu.auth.service.SysUserRoleService;
import com.atguigu.model.system.SysRole;
import com.atguigu.model.system.SysUserRole;
import com.atguigu.vo.system.AssginRoleVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper,SysRole> implements SysRoleService {
    @Resource
    private SysUserRoleService sysUserRoleService;
    @Override
    public Map<String, Object> findRoleByAdminId(Long userId) {
        List<SysRole> allRoleList = baseMapper.selectList(null);

        //根据userid查并联表中的数据
        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserRole::getUserId,userId);
        List<SysUserRole> existUserRoleList = sysUserRoleService.list(wrapper);

        //将其中的id封装成list
        List<Long> existRoleIdList = existUserRoleList.stream().map(c -> c.getRoleId()).collect(Collectors.toList());
        //通俗方法
//        ArrayList<Long> list = new ArrayList<>();
//        for (SysUserRole sysUserRole:existUserRoleList){
//            Long roleId = sysUserRole.getRoleId();
//            list.add(roleId);
//        }

        //将找到的id映射到角色 封装成list
        ArrayList<SysRole> assignRoleList = new ArrayList<>();
        for(SysRole sysRole:allRoleList){
            if(existRoleIdList.contains(sysRole.getId())){
                assignRoleList.add(sysRole);
            }
        }
        //封装成map返回
        Map<String, Object> roleMap = new HashMap<>();
        roleMap.put("assignRoleList", assignRoleList);
        roleMap.put("allRolesList", allRoleList);
        return roleMap;
    }

    //为用户分配角色
    @Override
    public void doAssign(AssginRoleVo assginRoleVo) {
        //先删除所分配的角色
        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserRole::getUserId,assginRoleVo.getUserId());
        sysUserRoleService.remove(wrapper);
        //再重新分配
        for (Long roleId : assginRoleVo.getRoleIdList()) {
            if (StringUtils.isEmpty(roleId)) {
                continue;
            }
            SysUserRole sysUserRole = new SysUserRole();
            sysUserRole.setUserId(assginRoleVo.getUserId());
            sysUserRole.setRoleId(roleId);
            sysUserRoleService.save(sysUserRole);
        }
    }
}
