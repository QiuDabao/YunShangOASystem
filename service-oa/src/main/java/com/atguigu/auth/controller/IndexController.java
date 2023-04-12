package com.atguigu.auth.controller;

import com.atguigu.auth.service.SysMenuService;
import com.atguigu.auth.service.SysUserService;
import com.atguigu.common.execption.GuiguException;
import com.atguigu.common.jwt.JwtHelper;
import com.atguigu.common.result.Result;
import com.atguigu.model.system.SysUser;
import com.atguigu.vo.system.LoginVo;
import com.atguigu.vo.system.RouterVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.annotations.Api;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 后台登录登出
 * </p>
 */
@Api(tags = "后台登录管理")
@RestController
@RequestMapping("/admin/system/index")
public class IndexController {
    @Resource
    private SysUserService sysUserService;
    @Resource
    private SysMenuService sysMenuService;

    /**
     * 登录
     * @return
     */
    @PostMapping("login")
    public Result login(@RequestBody LoginVo loginVo) {
//        Map<String, Object> map = new HashMap<>();
//        map.put("token","admin");
//        return Result.ok(map);
        //获取输入的用户名和密码
        String username = loginVo.getUsername();
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername,username);
        SysUser sysUser = sysUserService.getOne(wrapper);
        //根据用户名查询数据
        if (sysUser == null) {
            throw new GuiguException(201,"用户不存在");
        }
        if (!sysUser.getPassword().equals(loginVo.getPassword())) {
            throw new GuiguException(201,"密码不正确");
        }
        if (sysUser.getStatus().intValue() == 0) {
            throw new GuiguException(201,"用户已被禁用");
        }
        //使用jwt生成token字符串并返回
        String token = JwtHelper.createToken(sysUser.getId(), sysUser.getUsername());
        HashMap<String, Object> map = new HashMap<>();
        map.put("token",token);
        return Result.ok(map);
    }
    /**
     * 获取用户信息
     * @return
     */
    @GetMapping("info")
    public Result info(HttpServletRequest request) {
        //获取请求头中的token字符串
        String token = request.getHeader("token");
        //从token字符串中获取用户id
        Long userId = JwtHelper.getUserId(token);
        //查询出用户信息
        SysUser sysUser = sysUserService.getById(userId);
        //查询他可以操作的菜单和按钮列表(动态构建出路由结构)
        List<RouterVo> routerList = sysMenuService.findUserMenuListByUserId(userId);
        List<String> permsList = sysMenuService.findUserPermsByUserId(userId);

        Map<String, Object> map = new HashMap<>();
        map.put("roles","[admin]");
        map.put("name",sysUser.getName());
        map.put("avatar","https://oss.aliyuncs.com/aliyun_id_photo_bucket/default_handsome.jpg");
        //返回用户可以操作的菜单和按钮
        map.put("routers",routerList);
        map.put("buttons",permsList);
        return Result.ok(map);
    }
    /**
     * 退出
     * @return
     */
    @PostMapping("logout")
    public Result logout(){
        return Result.ok();
    }

}